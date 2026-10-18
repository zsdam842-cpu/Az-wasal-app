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

    fun getMyProfile(
        userId: String,
        accessToken: String
    ): Result<ProfileUser> {
        if (userId.isBlank() || accessToken.isBlank()) {
            return Result.failure(IOException("جلسة وصال غير مكتملة."))
        }

        return try {
            // المسار الصحيح لجلب الحساب الحالي
            CoreApiClient.execute(
                CoreApiClient.get(
                    ServerConfig.USERS_ENDPOINT + "/me",
                    accessToken
                )
            ).use { response ->
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    return Result.failure(
                        IOException(
                            "تعذر جلب بيانات الحساب. رمز الاستجابة: ${response.code}"
                        )
                    )
                }

                val root = JSONObject(body)
                val user = root.optJSONObject("user")
                    ?: return Result.failure(
                        IOException("لم يرجع الخادم بيانات الحساب.")
                    )

                val id = user.optString("id")
                val name = user.optString("displayName")
                    .ifBlank { user.optString("name") }
                val code = user.optString("wasalCode")
                    .ifBlank { user.optString("code") }
                val bio = user.optString("bio")

                if (id.isBlank() || !code.matches(Regex("^[A-Za-z0-9]{15}$"))) {
                    return Result.failure(
                        IOException("بيانات الحساب أو رمز وصال غير مكتملة.")
                    )
                }

                Result.success(
                    ProfileUser(
                        id = id,
                        displayName = name.ifBlank { "مستخدم وصال" },
                        wasalCode = code,
                        bio = bio
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(
                IOException(e.message ?: "تعذر الاتصال بخادم وصال.", e)
            )
        }
    }

    fun updateDisplayName(
        displayName: String,
        accessToken: String
    ): Result<ProfileUser> {
        val name = displayName.trim()

        if (accessToken.isBlank()) {
            return Result.failure(IOException("جلسة وصال غير صالحة."))
        }
        if (name.isBlank() || name.length > 40) {
            return Result.failure(IOException("اسم العرض غير صالح."))
        }

        return updateProfile(
            path = "/display-name",
            payload = JSONObject().put("displayName", name).toString(),
            accessToken = accessToken
        )
    }

    fun updateBio(
        bio: String,
        accessToken: String
    ): Result<ProfileUser> {
        val cleanBio = bio.trim()

        if (accessToken.isBlank()) {
            return Result.failure(IOException("جلسة وصال غير صالحة."))
        }
        if (cleanBio.length > 200) {
            return Result.failure(IOException("النبذة يجب ألا تتجاوز 200 حرف."))
        }

        return updateProfile(
            path = "/bio",
            payload = JSONObject().put("bio", cleanBio).toString(),
            accessToken = accessToken
        )
    }

    private fun updateProfile(
        path: String,
        payload: String,
        accessToken: String
    ): Result<ProfileUser> {
        return try {
            CoreApiClient.execute(
                CoreApiClient.put(
                    ServerConfig.USERS_ENDPOINT + "/me" + path,
                    payload,
                    accessToken
                )
            ).use { response ->
                val body = response.body?.string().orEmpty()

                if (!response.isSuccessful) {
                    return Result.failure(
                        IOException(
                            "تعذر حفظ بيانات الحساب. رمز الاستجابة: ${response.code}"
                        )
                    )
                }

                val user = JSONObject(body).optJSONObject("user")
                    ?: return Result.failure(
                        IOException("لم يرجع الخادم بيانات الحساب المحفوظة.")
                    )

                val id = user.optString("id")
                val name = user.optString("displayName")
                    .ifBlank { user.optString("name") }
                val code = user.optString("wasalCode")
                    .ifBlank { user.optString("code") }

                if (id.isBlank() ||
                    !code.matches(Regex("^[A-Za-z0-9]{15}$"))
                ) {
                    return Result.failure(
                        IOException("بيانات الحساب المحفوظة غير مكتملة.")
                    )
                }

                Result.success(
                    ProfileUser(
                        id = id,
                        displayName = name.ifBlank { "مستخدم وصال" },
                        wasalCode = code,
                        bio = user.optString("bio")
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(
                IOException(e.message ?: "تعذر الاتصال بخادم وصال.", e)
            )
        }
    }
}
