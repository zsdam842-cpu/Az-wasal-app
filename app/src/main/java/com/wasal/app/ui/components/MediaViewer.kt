package com.wasal.app.ui.components

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.wasal.app.data.network.CoreApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * مصنع مشغّل الفيديو: يمرّر رمز الدخول إلى الخادم حتى تعمل الروابط المحمية،
 * ويدعم أيضاً الملفات المحلية (file://) بعد التنزيل.
 */
private fun buildPlayer(
    context: Context,
    url: String,
    token: String
): ExoPlayer {
    val httpFactory = DefaultHttpDataSource.Factory().apply {
        if (token.isNotBlank()) {
            setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
        }
        setAllowCrossProtocolRedirects(true)
    }

    val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)

    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .build()
        .apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
        }
}

/** فيديو داخل فقاعة المحادثة، مع أزرار تشغيل/إيقاف وشريط تقدم. */
@Composable
fun InlineVideoPlayer(
    url: String,
    accessToken: String,
    modifier: Modifier = Modifier,
    playWhenReady: Boolean = false
) {
    if (url.isBlank()) {
        return
    }

    val context = LocalContext.current
    val autoPlay = playWhenReady
    val player = remember(url, accessToken) {
        buildPlayer(context, url, accessToken).apply {
            this.playWhenReady = autoPlay
        }
    }

    DisposableEffect(url, accessToken) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                controllerAutoShow = false
                controllerShowTimeoutMs = 2500
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        modifier = modifier
    )
}

/** شاشة كاملة للصورة مع التكبير بإصبعين. */
@Composable
fun FullScreenImageDialog(
    url: String,
    accessToken: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            val request = remember(url, accessToken) {
                ImageRequest.Builder(context)
                    .data(url)
                    .apply {
                        if (accessToken.isNotBlank()) {
                            addHeader("Authorization", "Bearer $accessToken")
                        }
                    }
                    .build()
            }

            SubcomposeAsyncImage(
                model = request,
                contentDescription = "صورة",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    ),
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                },
                error = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Text(
                            text = "تعذر عرض الصورة",
                            color = Color.White
                        )
                    }
                }
            )

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "إغلاق",
                    tint = Color.White
                )
            }
        }
    }
}

/** شاشة كاملة للفيديو مع أدوات التحكم. */
@Composable
fun FullScreenVideoDialog(
    url: String,
    accessToken: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val player = remember(url, accessToken) {
        buildPlayer(context, url, accessToken).apply { playWhenReady = true }
    }

    DisposableEffect(url, accessToken) {
        onDispose { player.release() }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                        controllerAutoShow = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "إغلاق",
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * حفظ مرفق في مجلد التنزيلات بالجهاز (بدل الرابط النصي).
 * يعمل مع الروابط المحمية عبر تمرير رمز الدخول.
 */
object MediaSaver {

    suspend fun save(
        context: Context,
        url: String,
        accessToken: String,
        desiredName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (url.isBlank()) {
                throw IOException("الرابط فارغ.")
            }

            var downloaded: ByteArray? = null

            if (url.startsWith("file://") || url.startsWith("/")) {
                val file = if (url.startsWith("file://")) {
                    File(android.net.Uri.parse(url).path ?: "")
                } else {
                    File(url)
                }

                if (!file.exists()) {
                    throw IOException("الملف غير موجود.")
                }

                downloaded = file.readBytes()
            } else {
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .apply {
                        if (accessToken.isNotBlank()) {
                            header("Authorization", "Bearer $accessToken")
                        }
                    }
                    .build()

                CoreApiClient.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("تعذر تنزيل الملف (${response.code}).")
                    }

                    val body = response.body ?: throw IOException("الملف فارغ.")
                    downloaded = body.bytes()
                }
            }

            val bytes = downloaded
                ?: throw IOException("تعذر قراءة الملف.")

            val fileName = sanitizeName(desiredName)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, guessMime(fileName))
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                ) ?: throw IOException("تعذر إنشاء الملف.")

                resolver.openOutputStream(uri)?.use { it.write(bytes) }

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)

                "تم الحفظ في مجلد التنزيلات: $fileName"
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    ),
                    "Wasal"
                )

                if (!dir.exists()) {
                    dir.mkdirs()
                }

                val target = File(dir, fileName)
                target.writeBytes(bytes)

                "تم الحفظ في: ${target.absolutePath}"
            }
        }
    }

    private fun sanitizeName(name: String): String {
        val clean = name.trim().ifBlank { "wasal_file" }
        return clean.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)
    }

    private fun guessMime(name: String): String {
        val lower = name.lowercase()

        return when {
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".mp4") -> "video/mp4"
            lower.endsWith(".3gp") -> "video/3gpp"
            lower.endsWith(".m4a") -> "audio/mp4"
            lower.endsWith(".mp3") -> "audio/mpeg"
            lower.endsWith(".ogg") -> "audio/ogg"
            else -> "application/octet-stream"
        }
    }
}
