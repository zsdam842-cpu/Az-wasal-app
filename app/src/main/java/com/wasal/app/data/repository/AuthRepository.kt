
package com.wasal.app.data.repository

import com.wasal.app.data.local.AuthDataStore
import com.wasal.app.data.network.CoreApiClient
import com.wasal.app.data.network.ServerConfig
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class AuthRepository(
    private val authDataStore: AuthDataStore
) {

    companion object {
        // يجب أن يحتوي الرمز على 15 خانة بالضبط،
        // وعلى حرف إنجليزي واحد على الأقل ورقم واحد على الأقل.
        private val WASAL_CODE_REGEX =
            Regex("^(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9]{15}$")
    }

    suspend fun createAccount(
        displayName: String = "مستخدم وصال"
    ): Result<String> {
        val cleanName = displayName.trim()
            .ifBlank { "مستخدم وصال" }

        return try {
            val payload = JSONObject()
                .put("displayName", cleanName)
                .toString()

            val request = CoreApiClient.post(
                ServerConfig.AUTH_ENDPOINT + "/register",
                payload
            )

            val accessToken: String
            val refreshToken: String
            val userId: String
            val wasalCode: String

            CoreApiClient.execute(request).use { response ->
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    val error = runCatching {
                        JSONObject(body).optString("error")
                    }.getOrDefault("")

                    val message = when (error) {
                        "DISPLAY_NAME_REQUIRED" ->
                            "يرجى إدخال اسم العرض."

                        "DISPLAY_NAME_TOO_LONG" ->
                            "اسم العرض أطول من الحد المسموح."

                        "WASAL_CODE_GENERATION_FAILED" ->
                            "تعذر إنشاء رمز وصال. حاول مرة أخرى."

                        "ACCOUNT_CONFLICT" ->
                            "حدث تعارض أثناء إنشاء الحساب. حاول مرة أخرى."

                        "ACCOUNT_CREATION_FAILED" ->
                            "فشل إنشاء الحساب في الخادم."

                        else ->
                            "تعذر إنشاء الحساب. رمز الاستجابة: ${response.code}"
                    }

                    return Result.failure(IOException(message))
                }

                val root = try {
                    JSONObject(body)
                } catch (_: Exception) {
                    return Result.failure(
                        IOException("استجابة الخادم ليست بصيغة JSON صحيحة.")
                    )
                }

                val user = root.optJSONObject("user")
                    ?: return Result.failure(
                        IOException("لم يرجع الخادم بيانات المستخدم.")
                    )

                // يدعم شكل استجابة الخادم الحالي والقديم.
                val tokens = root.optJSONObject("tokens") ?: root

                accessToken = tokens.optString("accessToken")
                    .ifBlank { root.optString("accessToken") }

                refreshToken = tokens.optString("refreshToken")
                    .ifBlank { root.optString("refreshToken") }

                userId = user.optString("id")

                // الخادم الحالي يعيد رمز وصال داخل user.code.
                wasalCode = sequenceOf(
                    user.optString("code"),
                    user.optString("wasalCode"),
                    user.optString("wasal_code"),
                    user.optString("pin_code"),
                    user.optString("pinCode"),
                    root.optString("code"),
                    root.optString("wasalCode"),
                    root.optString("pin_code"),
                    root.optString("pinCode")
                ).firstOrNull { it.isNotBlank() }.orEmpty()

                if (accessToken.isBlank()) {
                    return Result.failure(
                        IOException("لم يرجع الخادم رمز الوصول accessToken.")
                    )
                }

                if (refreshToken.isBlank()) {
                    return Result.failure(
                        IOException("لم يرجع الخادم رمز التجديد refreshToken.")
                    )
                }

                if (userId.isBlank()) {
                    return Result.failure(
                        IOException("لم يرجع الخادم معرّف المستخدم.")
                    )
                }

                if (!WASAL_CODE_REGEX.matches(wasalCode)) {
                    return Result.failure(
                        IOException(
                            "رمز وصال غير صالح. يجب أن يتكون من 15 خانة " +
                                "من الحروف الإنجليزية والأرقام معًا."
                        )
                    )
                }
            }

            authDataStore.saveSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                userId = userId
            )

            Result.success(wasalCode)
        } catch (e: Exception) {
            Result.failure(
                IOException(
                    e.message ?: "تعذر الاتصال بخادم وصال.",
                    e
                )
            )
        }
    }

    // --------------------------------------------------
    // الأمان: الرمز السري، نقل الحساب، النسخ الاحتياطي
    // --------------------------------------------------

    data class SecurityStatus(
        val hasSecret: Boolean = false,
        val remainingChanges: Int = 2,
        val backupEmail: String? = null,
        val deviceBlocked: Boolean = false
    )

    data class TransferCode(
        val id: String,
        val deviceName: String,
        val code: String
    )

    private suspend fun token(): String =
        authDataStore.accessToken.first().orEmpty()

    private fun errorMessage(body: String, fallback: String): String {
        return runCatching {
            JSONObject(body).optString("error")
        }.getOrDefault("").ifBlank { fallback }
    }

    suspend fun securityStatus(deviceId: String): Result<SecurityStatus> {
        return runCatching {
            val path = ServerConfig.AUTH_ENDPOINT +
                "/security?deviceId=" + android.net.Uri.encode(deviceId)

            CoreApiClient.execute(CoreApiClient.get(path, token())).use { response ->
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    throw IOException(
                        errorMessage(body, "تعذر قراءة حالة الأمان.")
                    )
                }

                val root = JSONObject(body)

                SecurityStatus(
                    hasSecret = root.optBoolean("hasSecret", false),
                    remainingChanges = root.optInt("remainingChanges", 2),
                    backupEmail = root.optString("backupEmail")
                        .takeIf { it.isNotBlank() && it != "null" },
                    deviceBlocked = root.optBoolean("deviceBlocked", false)
                )
            }
        }
    }

    suspend fun setSecret(secret: String): Result<Unit> {
        return runCatching {
            val payload = JSONObject().put("secret", secret).toString()

            CoreApiClient.execute(
                CoreApiClient.post(
                    ServerConfig.AUTH_ENDPOINT + "/secret",
                    payload,
                    token()
                )
            ).use { response ->
                if (!response.isSuccessful) {
                    throw IOException(
                        errorMessage(
                            response.body?.string().orEmpty(),
                            "تعذر تعيين الرمز السري."
                        )
                    )
                }
            }
        }
    }

    /** يعيد عدد المحاولات المتبقية على هذا الهاتف. */
    suspend fun changeSecret(
        currentSecret: String,
        newSecret: String,
        deviceId: String
    ): Result<Int> {
        return runCatching {
            val payload = JSONObject()
                .put("currentSecret", currentSecret)
                .put("newSecret", newSecret)
                .put("deviceId", deviceId)
                .toString()

            CoreApiClient.execute(
                CoreApiClient.post(
                    ServerConfig.AUTH_ENDPOINT + "/secret/change",
                    payload,
                    token()
                )
            ).use { response ->
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    throw IOException(
                        errorMessage(body, "تعذر تغيير الرمز السري.")
                    )
                }

                JSONObject(body).optInt("remainingChanges", 0)
            }
        }
    }

    /** يطلب دخولاً من هاتف آخر؛ يعيد معرّف الطلب. */
    suspend fun requestSecretLogin(
        code: String,
        secret: String,
        deviceId: String,
        deviceName: String
    ): Result<String> {
        return runCatching {
            val payload = JSONObject()
                .put("code", code)
                .put("secret", secret)
                .put("deviceId", deviceId)
                .put("deviceName", deviceName)
                .toString()

            CoreApiClient.execute(
                CoreApiClient.post(
                    ServerConfig.AUTH_ENDPOINT + "/secret/login",
                    payload
                )
            ).use { response ->
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    throw IOException(
                        errorMessage(body, "تعذر بدء الدخول بالرمز السري.")
                    )
                }

                JSONObject(body).optString("requestId").ifBlank {
                    throw IOException("لم يرجع الخادم معرّف الطلب.")
                }
            }
        }
    }

    suspend fun verifySecretLogin(
        requestId: String,
        verificationCode: String,
        deviceId: String
    ): Result<Boolean> {
        return runCatching {
            val payload = JSONObject()
                .put("requestId", requestId)
                .put("code", verificationCode)
                .put("deviceId", deviceId)
                .toString()

            CoreApiClient.execute(
                CoreApiClient.post(
                    ServerConfig.AUTH_ENDPOINT + "/secret/verify",
                    payload
                )
            ).use { response ->
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    throw IOException(
                        errorMessage(body, "رمز التأكيد غير صحيح.")
                    )
                }

                val root = JSONObject(body)
                val tokens = root.optJSONObject("tokens") ?: root
                val user = root.optJSONObject("user")

                val accessToken = tokens.optString("accessToken")
                    .ifBlank { root.optString("accessToken") }
                val refreshToken = tokens.optString("refreshToken")
                    .ifBlank { root.optString("refreshToken") }
                val userId = user?.optString("id").orEmpty()

                if (accessToken.isBlank() || refreshToken.isBlank() ||
                    userId.isBlank()
                ) {
                    throw IOException("لم يرجع الخادم جلسة صالحة.")
                }

                authDataStore.saveSession(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    userId = userId
                )

                root.optBoolean("hasBackup", false)
            }
        }
    }

    suspend fun setBackupEmail(email: String): Result<String> {
        return runCatching {
            val payload = JSONObject().put("email", email).toString()

            CoreApiClient.execute(
                CoreApiClient.post(
                    ServerConfig.AUTH_ENDPOINT + "/backup-email",
                    payload,
                    token()
                )
            ).use { response ->
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    throw IOException(
                        errorMessage(body, "تعذر حفظ البريد الاحتياطي.")
                    )
                }

                JSONObject(body).optString("backupEmail")
            }
        }
    }

    suspend fun uploadBackup(payloadText: String): Result<Unit> {
        return runCatching {
            val payload = JSONObject().put("payload", payloadText).toString()

            CoreApiClient.execute(
                CoreApiClient.post(
                    ServerConfig.AUTH_ENDPOINT + "/backup",
                    payload,
                    token()
                )
            ).use { response ->
                if (!response.isSuccessful) {
                    throw IOException("تعذر رفع النسخة الاحتياطية.")
                }
            }
        }
    }

    suspend fun downloadBackup(): Result<String> {
        return runCatching {
            CoreApiClient.execute(
                CoreApiClient.get(
                    ServerConfig.AUTH_ENDPOINT + "/backup",
                    token()
                )
            ).use { response ->
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    throw IOException("لا توجد نسخة احتياطية على الخادم.")
                }

                JSONObject(body).optString("payload")
            }
        }
    }

    suspend fun pendingTransferCodes(): Result<List<TransferCode>> {
        return runCatching {
            CoreApiClient.execute(
                CoreApiClient.get(
                    ServerConfig.AUTH_ENDPOINT + "/transfer/pending",
                    token()
                )
            ).use { response ->
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    throw IOException("تعذر قراءة طلبات النقل.")
                }

                val requests = JSONObject(body).optJSONArray("requests")
                    ?: JSONArray()

                (0 until requests.length()).mapNotNull { index ->
                    val item = requests.optJSONObject(index) ?: return@mapNotNull null

                    TransferCode(
                        id = item.optString("id"),
                        deviceName = item.optString("deviceName"),
                        code = item.optString("code")
                    )
                }
            }
        }
    }
}
