package com.wasal.app.data.repository

import com.wasal.app.data.local.AuthDataStore
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

class ProfileRepository(
    private val authDataStore: AuthDataStore
) {
    private val api = AuthenticatedApiClient(authDataStore)

    suspend fun getMyProfile(
        userId: String,
        accessToken: String
    ): Result<ProfileUser> {
        if (userId.isBlank() || accessToken.isBlank()) {
            return Result.failure(IOException("جلسة وصال غير مكتملة."))
        }

        val response = api.execute { token ->
            CoreApiClient.get(
                ServerConfig.USERS_ENDPOINT + "/me",
                token
            )
        }.getOrElse {
            return Result.failure(it)
        }

        if (!response.isSuccessful) {
            return Result.failure(
                IOException("تعذر جلب بيانات الحساب. رمز الاستجابة: ${response.code}")
            )
        }

        return parseProfile(response.body)
    }

    suspend fun updateDisplayName(
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
            payload = JSONObject().put("displayName", name).toString()
        )
    }

    suspend fun updateBio(
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
            payload = JSONObject().put("bio", cleanBio).toString()
        )
    }

    private suspend fun updateProfile(
        path: String,
        payload: String
    ): Result<ProfileUser> {
        val response = api.execute { token ->
            CoreApiClient.put(
                ServerConfig.USERS_ENDPOINT + "/me" + path,
                payload,
                token
            )
        }.getOrElse {
            return Result.failure(it)
        }

        if (!response.isSuccessful) {
            return Result.failure(
                IOException("تعذر حفظ بيانات الحساب. رمز الاستجابة: ${response.code}")
            )
        }

        return parseProfile(response.body)
    }

    private fun parseProfile(body: String): Result<ProfileUser> {
        return try {
            val user = JSONObject(body).optJSONObject("user")
                ?: return Result.failure(
                    IOException("لم يرجع الخادم بيانات الحساب.")
                )

            val id = user.optString("id")
            val name = user.optString("displayName")
                .ifBlank { user.optString("name") }
            val code = user.optString("wasalCode")
                .ifBlank { user.optString("code") }
            val bio = user.optString("bio")

            if (id.isBlank() ||
                !code.matches(Regex("^[A-Za-z0-9]{15}$"))
            ) {
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
        } catch (e: Exception) {
            Result.failure(
                IOException(e.message ?: "تعذر قراءة بيانات الحساب.", e)
            )
        }
    }
}
