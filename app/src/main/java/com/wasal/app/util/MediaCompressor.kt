package com.wasal.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

/**
 * ضغط الصور والفيديو قبل الرفع.
 *
 * الهدف: إرسال أسرع بكثير وتوفير بيانات المستخدم. صورة من كاميرا الهاتف
 * تصل عادة إلى 3–8 ميجابايت، وبعد الضغط تصبح 150–400 كيلوبايت بنفس
 * الوضوح على شاشة الهاتف — أي رفع أسرع بأكثر من 10 مرات.
 *
 * في وضع توفير البيانات تُضغط الوسائط أكثر (أبعاد أقل وجودة أقل).
 */
data class CompressionProfile(
    val maxImageEdge: Int,
    val imageQuality: Int,
    val maxVideoEdge: Int
)

data class PreparedMedia(
    val uri: Uri,
    val file: File?,
    val mimeType: String,
    val fileName: String,
    val length: Long,
    val originalSize: Long,
    val compressedSize: Long
) {
    val compressed: Boolean
        get() = file != null && compressedSize in 1 until originalSize
}

object MediaCompressor {
    private const val MIN_IMAGE_BYTES = 120L * 1024L

    fun profileFor(dataSaver: Boolean): CompressionProfile =
        if (dataSaver) {
            CompressionProfile(
                maxImageEdge = 1024,
                imageQuality = 62,
                maxVideoEdge = 854
            )
        } else {
            CompressionProfile(
                maxImageEdge = 1600,
                imageQuality = 80,
                maxVideoEdge = 1280
            )
        }

    /**
     * يجهّز الملف للإرسال: يعيد ملفًا مضغوطًا إن كان الضغط مفيدًا،
     * وإلا يعيد الأصل كما هو (بلا أي تغيير).
     *
     * لا يرمي استثناءات: أي فشل في الضغط يعني إرسال الملف الأصلي.
     */
    suspend fun prepare(
        context: Context,
        uri: Uri,
        mimeType: String,
        fileName: String,
        dataSaver: Boolean,
        originalSize: Long,
        onStage: (String) -> Unit = {}
    ): PreparedMedia {
        val profile = profileFor(dataSaver)

        val unchanged = PreparedMedia(
            uri = uri,
            file = null,
            mimeType = mimeType,
            fileName = fileName,
            length = originalSize,
            originalSize = originalSize,
            compressedSize = originalSize
        )

        return try {
            when {
                mimeType.startsWith("image/") &&
                    !mimeType.equals("image/gif", ignoreCase = true) -> {
                    if (originalSize in 1 until MIN_IMAGE_BYTES) {
                        unchanged
                    } else {
                        onStage("جارٍ ضغط الصورة...")

                        val compressed = withContext(Dispatchers.IO) {
                            compressImage(context, uri, profile)
                        }

                        val notSmaller = compressed != null &&
                            originalSize > 0 &&
                            compressed.length() >= originalSize * 95 / 100

                        if (
                            compressed == null ||
                            compressed.length() <= 0 ||
                            notSmaller
                        ) {
                            compressed?.delete()
                            unchanged
                        } else {
                            PreparedMedia(
                                uri = Uri.fromFile(compressed),
                                file = compressed,
                                mimeType = "image/jpeg",
                                fileName = fileName.replaceAfterLast(
                                    '.',
                                    "jpg",
                                    fileName
                                ),
                                length = compressed.length(),
                                originalSize = originalSize,
                                compressedSize = compressed.length()
                            )
                        }
                    }
                }

                mimeType.startsWith("video/") -> {
                    val longEdge = videoLongEdge(context, uri)

                    if (longEdge <= profile.maxVideoEdge) {
                        unchanged
                    } else {
                        onStage("جارٍ ضغط الفيديو...")

                        val compressed = compressVideo(
                            context = context,
                            uri = uri,
                            profile = profile,
                            longEdge = longEdge,
                            onProgress = onStage
                        )

                        val notSmaller = compressed != null &&
                            originalSize > 0 &&
                            compressed.length() >= originalSize * 95 / 100

                        if (
                            compressed == null ||
                            compressed.length() <= 0 ||
                            notSmaller
                        ) {
                            compressed?.delete()
                            unchanged
                        } else {
                            PreparedMedia(
                                uri = Uri.fromFile(compressed),
                                file = compressed,
                                mimeType = "video/mp4",
                                fileName = fileName.replaceAfterLast(
                                    '.',
                                    "mp4",
                                    fileName
                                ),
                                length = compressed.length(),
                                originalSize = originalSize,
                                compressedSize = compressed.length()
                            )
                        }
                    }
                }

                else -> unchanged
            }
        } catch (error: Throwable) {
            unchanged
        }
    }

    // ---------------- الصور ----------------

    private fun compressImage(
        context: Context,
        uri: Uri,
        profile: CompressionProfile
    ): File? {
        val resolver = context.contentResolver

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        var sampleSize = 1
        val longEdge = max(bounds.outWidth, bounds.outHeight)

        while (longEdge / (sampleSize * 2) >= profile.maxImageEdge) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        val rotation = exifRotation(context, uri)

        val scale = min(
            1f,
            profile.maxImageEdge.toFloat() /
                max(decoded.width, decoded.height).toFloat()
        )

        val matrix = Matrix().apply {
            if (rotation != 0f) postRotate(rotation)
            if (scale < 1f) postScale(scale, scale)
        }

        val transformed = if (rotation != 0f || scale < 1f) {
            Bitmap.createBitmap(
                decoded,
                0,
                0,
                decoded.width,
                decoded.height,
                matrix,
                true
            )
        } else {
            decoded
        }

        val output = File(
            context.cacheDir,
            "wasal-img-${UUID.randomUUID()}.jpg"
        )

        return try {
            FileOutputStream(output).use { stream ->
                transformed.compress(
                    Bitmap.CompressFormat.JPEG,
                    profile.imageQuality,
                    stream
                )
            }

            if (transformed !== decoded) {
                transformed.recycle()
            }

            decoded.recycle()

            output.takeIf { it.length() > 0 }
        } catch (error: Throwable) {
            output.delete()
            null
        }
    }

    private fun exifRotation(context: Context, uri: Uri): Float =
        try {
            val orientation = context.contentResolver
                .openInputStream(uri)
                ?.use { stream ->
                    ExifInterface(stream)
                        .getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                }
                ?: ExifInterface.ORIENTATION_NORMAL

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (error: Throwable) {
            0f
        }

    // ---------------- الفيديو ----------------

    private fun videoLongEdge(context: Context, uri: Uri): Int {
        val retriever = MediaMetadataRetriever()

        return try {
            retriever.setDataSource(context, uri)

            val width = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0

            val height = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0

            max(width, height)
        } catch (error: Throwable) {
            0
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * تحويل الفيديو إلى H.264 بأبعاد أصغر (نسبة تصغير واحدة).
     *
     * Transformer يحتاج Looper، فيبدأ من الخيط الرئيسي، أما الترميز نفسه
     * فيجري في خيوط المكتبة. أي فشل يعني الرجوع للأصل بلا ضغط.
     */
    private suspend fun compressVideo(
        context: Context,
        uri: Uri,
        profile: CompressionProfile,
        longEdge: Int,
        onProgress: (String) -> Unit
    ): File? = withContext(Dispatchers.Main) {
        val scale = (profile.maxVideoEdge.toFloat() / longEdge.toFloat())
            .coerceIn(0.2f, 1f)

        if (scale >= 0.99f) {
            return@withContext null
        }

        val output = File(
            context.cacheDir,
            "wasal-vid-${UUID.randomUUID()}.mp4"
        )

        suspendCancellableCoroutine { continuation ->
            val videoEffect = ScaleAndRotateTransformation.Builder()
                .setScale(scale, scale)
                .build()

            val item = EditedMediaItem.Builder(MediaItem.fromUri(uri))
                .setEffects(
                    Effects(
                        emptyList<AudioProcessor>(),
                        listOf<Effect>(videoEffect)
                    )
                )
                .build()

            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(
                    object : Transformer.Listener {
                        override fun onCompleted(
                            composition: Composition,
                            exportResult: ExportResult
                        ) {
                            if (continuation.isActive) {
                                continuation.resume(output)
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            if (continuation.isActive) {
                                output.delete()
                                continuation.resume(null)
                            }
                        }
                    }
                )
                .build()

            continuation.invokeOnCancellation {
                runCatching { transformer.cancel() }
                output.delete()
            }

            onProgress("جارٍ ضغط الفيديو...")

            try {
                transformer.start(item, output.absolutePath)
            } catch (error: Throwable) {
                output.delete()

                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
    }
}
