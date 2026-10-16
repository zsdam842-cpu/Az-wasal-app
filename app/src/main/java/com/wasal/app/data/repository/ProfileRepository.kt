
package com.wasal.app.data.repository

import com.wasal.app.data.network.CoreApiClient
import com.wasal.app.data.network.ServerConfig
import org.json.JSONObject
import java.io.IOException

data class ProfileUser(
    val id: String,
    val displayName: String,
    val wasalCode: String,
    val bio: String
)

class ProfileRepository {

    /**
     * جلب بيانات حساب وصال الحالي من الخادم.
     * يجب استدعاء الدالة من Dispatchers.IO.
     */
    fun getMyProfile(
        userId: String,
        accessToken: String
    ): Result<ProfileUser> {

        if (userId.isBlank() || accessToken.isBlank()) {
            return Result.failure(
                IOException("جلسة وصال غير مكتملة.")
            )
        }

        return try {
            val path =
                ServerConfig.USERS_ENDPOINT + "/" + userId

            CoreApiClient.execute(
                CoreApiClient.get(path, accessToken)
            ).use { response ->

                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    return Result.failure(
                        IOException(
                            "تعذر جلب بيانات الحساب. رمز الاستجابة: ${response.code}"
                        )
                    )
                }

                val user = JSONObject(body)
                    .optJSONObject("user")
                    ?: return Result.failure(
                        IOException("لم يرجع الخادم بيانات الحساب.")
                    )

                val id = user.optString("id")
                val displayName = user.optString("displayName")
                val wasalCode = user.optString("wasalCode")
                val bio = user.optString("bio")

                if (
                    id.isBlank() ||
                    !wasalCode.matches(Regex("^[A-Za-z0-9]{15}$"))
                ) {
                    return Result.failure(
                        IOException("بيانات الحساب أو رمز وصال غير مكتملة.")
                    )
                }

                Result.success(
                    ProfileUser(
                        id = id,
                        displayName = displayName.ifBlank {
                            "مستخدم وصال"
                        },
                        wasalCode = wasalCode,
                        bio = bio
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * حفظ اسم العرض للحساب الحالي على الخادم.
     * يجب استدعاء الدالة من Dispatchers.IO.
     */
    fun updateDisplayName(
        displayName: String,
        accessToken: String
    ): Result<ProfileUser> {

        val cleanName = displayName.trim()

        if (accessToken.isBlank()) {
            return Result.failure(
                IOException("انتهت جلسة وصال. سجّل الدخول مجددًا.")
            )
        }

        if (cleanName.isBlank()) {
            return Result.failure(
                IOException("اكتب اسم العرض أولًا.")
            )
        }

        if (cleanName.length > 40) {
            return Result.failure(
                IOException("اسم العرض يجب ألا يتجاوز 40 حرفًا.")
            )
        }

        return try {
            val path =
                ServerConfig.USERS_ENDPOINT + "/me/display-name"

            val requestBody = JSONObject()
                .put("displayName", cleanName)
                .toString()

            CoreApiClient.execute(
                CoreApiClient.put(
                    path,
                    requestBody,
                    accessToken
                )
            ).use { response ->

                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    val message = when (response.code) {
                        400 -> "اسم العرض غير صالح."
                        401 -> "انتهت جلسة وصال. سجّل الدخول مجددًا."
                        404 -> "لم يتم العثور على الحساب."
                        else -> "تعذر حفظ الاسم. رمز الاستجابة: ${response.code}"
                    }

                    return Result.failure(IOException(message))
                }

                val root = JSONObject(body)
                val user = root.optJSONObject("user")
                    ?: return Result.failure(
                        IOException("لم يرجع الخادم بيانات الاسم المحفوظ.")
                    )

                val id = user.optString("id")
                val savedName = user.optString("displayName")
                val wasalCode = user.optString("wasalCode")
                val bio = user.optString("bio")

                if (
                    id.isBlank() ||
                    savedName.isBlank() ||
                    !wasalCode.matches(Regex("^[A-Za-z0-9]{15}$"))
                ) {
                    return Result.failure(
                        IOException("بيانات الحساب المحفوظة غير مكتملة.")
                    )
                }

                Result.success(
                    ProfileUser(
                        id = id,
                        displayName = savedName,
                        wasalCode = wasalCode,
                        bio = bio
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(
                IOException(
                    e.message ?: "حدث خطأ أثناء حفظ اسم العرض.",
                    e
                )
            )
        }
    }

    /**
     * حفظ النبذة التعريفية للحساب الحالي على الخادم.
     * يجب استدعاء الدالة من Dispatchers.IO.
     */
    fun updateBio(
        bio: String,
        accessToken: String
    ): Result<ProfileUser> {

        val cleanBio = bio.trim()

        if (accessToken.isBlank()) {
            return Result.failure(
                IOException("انتهت جلسة وصال. سجّل الدخول مجددًا.")
            )
        }

        if (cleanBio.length > 200) {
            return Result.failure(
                IOException("النبذة يجب ألا تتجاوز 200 حرف.")
            )
        }

        return try {
            val path =
                ServerConfig.USERS_ENDPOINT + "/me/bio"

            val requestBody = JSONObject()
                .put("bio", cleanBio)
                .toString()

            CoreApiClient.execute(
                CoreApiClient.put(
                    path,
                    requestBody,
                    accessToken
                )
            ).use { response ->

                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    val message = when (response.code) {
                        400 -> "النبذة غير صالحة."
                        401 -> "انتهت جلسة وصال. سجّل الدخول مجددًا."
                        404 -> "لم يتم العثور على الحساب."
                        else -> "تعذر حفظ النبذة. رمز الاستجابة: ${response.code}"
                    }

                    return Result.failure(IOException(message))
                }

                val root = JSONObject(body)
                val user = root.optJSONObject("user")
                    ?: return Result.failure(
                        IOException("لم يرجع الخادم بيانات النبذة المحفوظة.")
                    )

                val id = user.optString("id")
                val savedBio = user.optString("bio")
                val wasalCode = user.optString("wasalCode")

                if (
                    id.isBlank() ||
                    !wasalCode.matches(Regex("^[A-Za-z0-9]{15}$"))
                ) {
                    return Result.failure(
                        IOException("بيانات الحساب المحفوظة غير مكتملة.")
                    )
                }

                Result.success(
                    ProfileUser(
                        id = id,
                        displayName = user.optString("displayName")
                            .ifBlank { "مستخدم وصال" },
                        wasalCode = wasalCode,
                        bio = savedBio
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(
                IOException(
                    e.message ?: "حدث خطأ أثناء حفظ النبذة.",
                    e
                )
            )
        }
    }
}
