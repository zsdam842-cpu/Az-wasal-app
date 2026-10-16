package com.wasal.app.data.repository

import com.wasal.app.data.local.AuthDataStore
import com.wasal.app.data.network.CoreApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

data class AuthenticatedApiResponse(
    val code: Int,
    val body: String,
    val isSuccessful: Boolean
)

class AuthenticatedApiClient(
    private val authDataStore: AuthDataStore
) {
    private val tokenAuthenticator =
        TokenAuthenticator(authDataStore)

    /**
     * ينفذ الطلب، ويجدد رمز الدخول عند استجابة 401،
     * ثم يعيد الطلب مرة واحدة باستخدام الرمز الجديد.
     *
     * يجب تمرير دالة تنشئ Request باستخدام رمز الدخول.
     */
    suspend fun execute(
        requestFactory: (String) -> Request
    ): Result<AuthenticatedApiResponse> =
        withContext(Dispatchers.IO) {
            try {
                val accessToken =
                    authDataStore.accessToken.first()

                if (accessToken.isNullOrBlank()) {
                    return@withContext Result.failure(
                        IOException(
                            "رمز الدخول غير موجود. يلزم إنشاء جلسة جديدة."
                        )
                    )
                }

                val firstResponse =
                    CoreApiClient.execute(
                        requestFactory(accessToken)
                    ).use { response ->
                        AuthenticatedApiResponse(
                            code = response.code,
                            body = response.body
                                ?.string()
                                .orEmpty(),
                            isSuccessful = response.isSuccessful
                        )
                    }

                if (firstResponse.code != 401) {
                    return@withContext Result.success(
                        firstResponse
                    )
                }

                val refreshedToken =
                    tokenAuthenticator
                        .refreshAccessToken()
                        .getOrElse { error ->
                            return@withContext Result.failure(
                                IOException(
                                    error.message
                                        ?: "تعذر تجديد رمز الدخول.",
                                    error
                                )
                            )
                        }

                val retryResponse =
                    CoreApiClient.execute(
                        requestFactory(refreshedToken)
                    ).use { response ->
                        AuthenticatedApiResponse(
                            code = response.code,
                            body = response.body
                                ?.string()
                                .orEmpty(),
                            isSuccessful = response.isSuccessful
                        )
                    }

                Result.success(retryResponse)
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
}
