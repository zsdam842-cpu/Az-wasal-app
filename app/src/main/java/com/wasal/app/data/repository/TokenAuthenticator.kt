package com.wasal.app.data.repository

import com.wasal.app.data.local.AuthDataStore
import com.wasal.app.data.network.CoreApiClient
import com.wasal.app.data.network.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

class TokenAuthenticator(
    private val authDataStore: AuthDataStore
) {

    /**
     * تجديد رمز الدخول وحفظ الرمزين الجديدين.
     * يجب استدعاء الدالة من coroutine.
     */
    suspend fun refreshAccessToken(): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val oldRefreshToken =
                    authDataStore.refreshToken.first()

                if (oldRefreshToken.isNullOrBlank()) {
                    return@withContext Result.failure(
                        IOException(
                            "رمز التحديث غير موجود. يلزم تسجيل الدخول مجددًا."
                        )
                    )
                }

                val payload = JSONObject()
                    .put("refreshToken", oldRefreshToken)
                    .toString()

                val request = CoreApiClient.post(
                    ServerConfig.AUTH_ENDPOINT + "/refresh",
                    payload
                )

                CoreApiClient.execute(request).use { response ->
                    val body = response.body?.string().orEmpty()

                    if (!response.isSuccessful) {
                        val error = runCatching {
                            JSONObject(body).optString("error")
                        }.getOrDefault("")

                        val message = when (error) {
                            "INVALID_OR_EXPIRED_REFRESH_TOKEN" ->
                                "انتهت صلاحية الجلسة. يلزم تسجيل الدخول مجددًا."

                            "REFRESH_TOKEN_REQUIRED" ->
                                "رمز التحديث غير موجود."

                            else ->
                                "تعذر تجديد الجلسة. رمز الاستجابة: ${response.code}"
                        }

                        return@withContext Result.failure(
                            IOException(message)
                        )
                    }

                    val root = JSONObject(body)

                    val newAccessToken =
                        root.optString("accessToken")

                    val newRefreshToken =
                        root.optString("refreshToken")

                    if (
                        newAccessToken.isBlank() ||
                        newRefreshToken.isBlank()
                    ) {
                        return@withContext Result.failure(
                            IOException(
                                "لم يرجع الخادم رمزي الجلسة الجديدين."
                            )
                        )
                    }

                    val userId =
                        authDataStore.userId.first()

                    if (userId.isNullOrBlank()) {
                        return@withContext Result.failure(
                            IOException(
                                "معرّف المستخدم غير موجود."
                            )
                        )
                    }

                    authDataStore.saveSession(
                        accessToken = newAccessToken,
                        refreshToken = newRefreshToken,
                        userId = userId
                    )

                    Result.success(newAccessToken)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
