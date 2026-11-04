package com.wasal.app.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.wasal.app.data.local.AuthDataStore
import com.wasal.app.data.local.SettingsDataStore
import com.wasal.app.data.network.MediaApiClient
import com.wasal.app.data.network.ServerConfig
import com.wasal.app.util.MediaCompressor
import com.wasal.app.util.PreparedMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import org.json.JSONObject
import java.io.File
import java.io.IOException

data class UploadedMedia(
    val filename: String,
    val url: String,
    val mimeType: String,
    val size: Long,
    val originalSize: Long = size,
    val sentSize: Long = size
) {
    val bytesSaved: Long
        get() = (originalSize - sentSize).coerceAtLeast(0)

    val savedPercent: Int
        get() = if (originalSize <= 0) {
            0
        } else {
            ((bytesSaved * 100) / originalSize).toInt()
        }
}

class MediaRepository(
    context: Context,
    private val authDataStore: AuthDataStore
) {
    private val appContext = context.applicationContext

    private val resolver: ContentResolver =
        appContext.contentResolver

    private val settings = SettingsDataStore(appContext)

    private val mediaApi = MediaApiClient()

    private val tokenAuthenticator =
        TokenAuthenticator(authDataStore)

    private val maxAttempts = 3

    /**
     * يرفع ملفًا إلى خادم الوسائط.
     *
     * الخطوات: ضغط (صور/فيديو) ثم رفع مع إبلاغ التقدّم وإعادة المحاولة
     * تلقائيًا عند انقطاع الشبكة. في حال فشل الضغط يُرسل الأصل كما هو.
     */
    suspend fun upload(
        uri: Uri,
        mimeTypeOverride: String? = null,
        compress: Boolean = true,
        onStage: ((String) -> Unit)? = null,
        onProgress: ((Int) -> Unit)? = null
    ): Result<UploadedMedia> =
        withContext(Dispatchers.IO) {
            var temporaryFile: File? = null

            try {
                val mimeType = mimeTypeOverride
                    ?: resolver.getType(uri)
                    ?: "application/octet-stream"

                val fileName = queryFileName(uri)
                val originalSize = querySize(uri)

                val dataSaver = runCatching {
                    settings.dataSaver.first()
                }.getOrDefault(false)

                var prepared = PreparedMedia(
                    uri = uri,
                    file = null,
                    mimeType = mimeType,
                    fileName = fileName,
                    length = originalSize,
                    originalSize = originalSize,
                    compressedSize = originalSize
                )

                if (
                    compress &&
                    (
                        mimeType.startsWith("image/") ||
                            mimeType.startsWith("video/")
                        )
                ) {
                    prepared = MediaCompressor.prepare(
                        context = appContext,
                        uri = uri,
                        mimeType = mimeType,
                        fileName = fileName,
                        dataSaver = dataSaver,
                        originalSize = originalSize
                    ) { message ->
                        onStage?.invoke(message)
                    }

                    temporaryFile = prepared.file
                }

                val accessToken =
                    authDataStore.accessToken.first()
                        ?.takeIf { it.isNotBlank() }
                        ?: return@withContext Result.failure(
                            IOException(
                                "رمز الدخول غير موجود. أنشئ جلسة وصال أولاً."
                            )
                        )

                var token = accessToken
                var attempt = 1
                var lastError: Exception? = null

                while (attempt <= maxAttempts) {
                    try {
                        var response = executeUpload(
                            prepared = prepared,
                            accessToken = token,
                            onProgress = onProgress
                        )

                        if (response.code == 401 && attempt == 1) {
                            response.close()

                            token = tokenAuthenticator
                                .refreshAccessToken()
                                .getOrElse { error ->
                                    return@withContext Result.failure(
                                        IOException(
                                            error.message
                                                ?: "تعذر تجديد جلسة وصال.",
                                            error
                                        )
                                    )
                                }

                            attempt++

                            response = executeUpload(
                                prepared = prepared,
                                accessToken = token,
                                onProgress = onProgress
                            )
                        }

                        return@withContext parseResponse(
                            response = response,
                            fallbackMimeType = prepared.mimeType,
                            originalSize = prepared.originalSize,
                            sentSize = prepared.length
                                .takeIf { it > 0 }
                                ?: prepared.compressedSize
                        )
                    } catch (error: IOException) {
                        lastError = error

                        if (attempt >= maxAttempts) {
                            break
                        }

                        onStage?.invoke(
                            "تعذر الاتصال، إعادة المحاولة ($attempt/$maxAttempts)..."
                        )

                        delay(700L * attempt)
                        attempt++
                    }
                }

                Result.failure(
                    IOException(
                        "تعذر إرسال الملف بعد $maxAttempts محاولات. تحقق من الاتصال وحاول مجددًا.",
                        lastError
                    )
                )
            } catch (error: Exception) {
                Result.failure(error)
            } finally {
                temporaryFile?.delete()
            }
        }

    private fun parseResponse(
        response: Response,
        fallbackMimeType: String,
        originalSize: Long,
        sentSize: Long
    ): Result<UploadedMedia> =
        response.use { result ->
            val body = result.body?.string().orEmpty()

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

                    503, 502, 504 ->
                        "خادم الوسائط غير متاح الآن. أعد المحاولة بعد لحظات."

                    else ->
                        serverError.ifBlank {
                            "فشل رفع الملف. رمز الاستجابة: ${result.code}"
                        }
                }

                return Result.failure(IOException(message))
            }

            val json = JSONObject(body)
            val filename = json.optString("filename")
            val relativeUrl = json.optString("url")

            if (filename.isBlank() || relativeUrl.isBlank()) {
                return Result.failure(
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
                        fallbackMimeType
                    ),
                    size = json.optLong("size", sentSize),
                    originalSize = originalSize,
                    sentSize = sentSize
                )
            )
        }

    private fun queryFileName(uri: Uri): String =
        resolver.query(
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

    private fun querySize(uri: Uri): Long =
        runCatching {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)

                if (cursor.moveToFirst() && index >= 0 && !cursor.isNull(index)) {
                    cursor.getLong(index)
                } else {
                    -1L
                }
            } ?: -1L
        }.getOrDefault(-1L)

    private fun executeUpload(
        prepared: PreparedMedia,
        accessToken: String,
        onProgress: ((Int) -> Unit)?
    ): Response {
        // طول معلوم بدل البث المجزّأ (-1): يمنع انقطاع الرفع على الشبكات
        // البطيئة ويسمح بإظهار نسبة حقيقية للتقدّم.
        val fileBody = if (prepared.file != null) {
            progressFileBody(
                file = prepared.file,
                mimeType = prepared.mimeType,
                onProgress = onProgress
            )
        } else {
            progressStreamBody(
                uri = prepared.uri,
                mimeType = prepared.mimeType,
                length = prepared.length,
                onProgress = onProgress
            )
        }

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                prepared.fileName,
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

        return mediaApi.uploadClient
            .newCall(request)
            .execute()
    }

    private fun progressFileBody(
        file: File,
        mimeType: String,
        onProgress: ((Int) -> Unit)?
    ): RequestBody =
        object : RequestBody() {
            override fun contentType() =
                mimeType.toMediaTypeOrNull()

            override fun contentLength(): Long = file.length()

            override fun writeTo(sink: BufferedSink) {
                val total = file.length().coerceAtLeast(1L)

                var written = 0L
                var lastPercent = -1

                file.inputStream().use { stream ->
                    val buffer = ByteArray(64 * 1024)

                    while (true) {
                        val read = stream.read(buffer)

                        if (read == -1) {
                            break
                        }

                        sink.write(buffer, 0, read)

                        written += read

                        val percent =
                            ((written * 100) / total).toInt()
                                .coerceIn(0, 100)

                        if (percent / 5 != lastPercent / 5) {
                            lastPercent = percent
                            onProgress?.invoke(percent)
                        }
                    }
                }
            }
        }

    private fun progressStreamBody(
        uri: Uri,
        mimeType: String,
        length: Long,
        onProgress: ((Int) -> Unit)?
    ): RequestBody =
        object : RequestBody() {
            override fun contentType() =
                mimeType.toMediaTypeOrNull()

            override fun contentLength(): Long = length

            override fun writeTo(sink: BufferedSink) {
                val input = resolver.openInputStream(uri)
                    ?: throw IOException(
                        "تعذر فتح الملف المحدد."
                    )

                val total = length.coerceAtLeast(1L)

                var written = 0L
                var lastPercent = -1

                input.use { stream ->
                    val buffer = ByteArray(64 * 1024)

                    while (true) {
                        val read = stream.read(buffer)

                        if (read == -1) {
                            break
                        }

                        sink.write(buffer, 0, read)

                        written += read

                        val percent =
                            ((written * 100) / total).toInt()
                                .coerceIn(0, 100)

                        if (percent / 5 != lastPercent / 5) {
                            lastPercent = percent
                            onProgress?.invoke(percent)
                        }
                    }
                }
            }
        }
}
