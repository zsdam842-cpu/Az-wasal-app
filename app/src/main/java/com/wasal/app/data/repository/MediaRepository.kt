
package com.wasal.app.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.wasal.app.data.local.AuthDataStore
import com.wasal.app.data.network.MediaApiClient
import com.wasal.app.data.network.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.IOException

data class UploadedMedia(
    val filename: String,
    val url: String,
    val mimeType: String,
    val size: Long
)

class MediaRepository(
    context: Context,
    private val authDataStore: AuthDataStore
) {
    private val resolver: ContentResolver =
        context.applicationContext.contentResolver

    private val mediaApi = MediaApiClient()

    private val tokenAuthenticator =
        TokenAuthenticator(authDataStore)

    suspend fun upload(
        uri: Uri,
        mimeTypeOverride: String? = null
    ): Result<UploadedMedia> =
        withContext(Dispatchers.IO) {
            try {
                val mimeType = mimeTypeOverride
                    ?: resolver.getType(uri)
                    ?: "application/octet-stream"

                val fileName = resolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val index = cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME
                    )

                    if (cursor.moveToFirst() && index >= 0) {
                        cursor.getString(index)
                    } else {
                        null
                    }
                }?.takeIf { it.isNotBlank() }
                    ?: uri.lastPathSegment
                        ?.substringAfterLast('/')
                        ?.takeIf { it.isNotBlank() }
                    ?: "upload"

                val accessToken =
                    authDataStore.accessToken.first()
                        ?.takeIf { it.isNotBlank() }
                        ?: return@withContext Result.failure(
                            IOException(
                                "رمز الدخول غير موجود. أنشئ جلسة وصال أولاً."
                            )
                        )

                var response = executeUpload(
                    uri = uri,
                    fileName = fileName,
                    mimeType = mimeType,
                    accessToken = accessToken
                )

                if (response.code == 401) {
                    response.close()

                    val refreshedToken =
                        tokenAuthenticator.refreshAccessToken()
                            .getOrElse { error ->
                                return@withContext Result.failure(
                                    IOException(
                                        error.message
                                            ?: "تعذر تجديد جلسة وصال.",
                                        error
                                    )
                                )
                            }

                    response = executeUpload(
                        uri = uri,
                        fileName = fileName,
                        mimeType = mimeType,
                        accessToken = refreshedToken
                    )
                }

                response.use { result ->
                    val body =
                        result.body?.string().orEmpty()

                    if (!result.isSuccessful) {
                        val serverError = runCatching {
                            JSONObject(body).optString("error")
                        }.getOrDefault("")

                        val message = when (result.code) {
                            401 ->
                                "رفض خادم الوسائط رمز الدخول. تحقق من إعداد المصادقة بين الخادمين."

                            413 ->
                                "حجم الملف أكبر من الحد المسموح به."

                            415 ->
                                "نوع الملف غير مدعوم."

                            else ->
                                serverError.ifBlank {
                                    "فشل رفع الملف. رمز الاستجابة: ${result.code}"
                                }
                        }

                        return@withContext Result.failure(
                            IOException(message)
                        )
                    }

                    val json = JSONObject(body)
                    val filename = json.optString("filename")
                    val relativeUrl = json.optString("url")

                    if (
                        filename.isBlank() ||
                        relativeUrl.isBlank()
                    ) {
                        return@withContext Result.failure(
                            IOException(
                                "استجابة الخادم لا تحتوي على اسم الملف ورابطه."
                            )
                        )
                    }

                    val absoluteUrl =
                        if (
                            relativeUrl.startsWith("http://") ||
                            relativeUrl.startsWith("https://")
                        ) {
                            relativeUrl
                        } else {
                            ServerConfig.MEDIA_CALLS_SERVER_URL
                                .trimEnd('/') +
                                "/" + relativeUrl.trimStart('/')
                        }

                    Result.success(
                        UploadedMedia(
                            filename = filename,
                            url = absoluteUrl,
                            mimeType = json.optString(
                                "mimeType",
                                mimeType
                            ),
                            size = json.optLong("size", -1L)
                        )
                    )
                }
            } catch (error: Exception) {
                Result.failure(error)
            }
        }

    private fun executeUpload(
        uri: Uri,
        fileName: String,
        mimeType: String,
        accessToken: String
    ): Response {
        val fileBody = object : RequestBody() {
            override fun contentType() =
                mimeType.toMediaTypeOrNull()

            override fun contentLength(): Long = -1L

            override fun writeTo(sink: BufferedSink) {
                val input = resolver.openInputStream(uri)
                    ?: throw IOException(
                        "تعذر فتح الملف المحدد."
                    )

                input.use { stream ->
                    sink.writeAll(stream.source())
                }
            }
        }

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                fileName,
                fileBody
            )
            .build()

        val request = Request.Builder()
            .url(
                ServerConfig.MEDIA_CALLS_SERVER_URL
                    .trimEnd('/') +
                    ServerConfig.MEDIA_ENDPOINT +
                    "/upload"
            )
            .header(
                "Authorization",
                "Bearer $accessToken"
            )
            .post(multipart)
            .build()

        return mediaApi.client
            .newCall(request)
            .execute()
    }
}
