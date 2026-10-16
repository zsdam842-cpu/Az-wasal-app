
package com.wasal.app.data.repository

import com.wasal.app.data.local.AuthDataStore
import com.wasal.app.data.network.CoreApiClient
import com.wasal.app.data.network.ServerConfig
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
}
