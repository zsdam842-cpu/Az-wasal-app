package com.wasal.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

/**
 * صورة المستخدم الشخصية: تُعرض دائرية، ويسقط إلى الحرف الأول
 * عندما لا توجد صورة أو تعذر تحميلها.
 */
@Composable
fun WasalAvatar(
    name: String,
    avatarUrl: String? = null,
    size: Dp = 42.dp,
    background: Color = Color(0xFF001636),
    accent: Color = Color(0xFF2BA0FF)
) {
    val context = LocalContext.current
    val initial = name.trim().take(1).ifBlank { "و" }

    @Composable
    fun Fallback() {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                color = accent,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    if (avatarUrl.isNullOrBlank()) {
        Fallback()
        return
    }

    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(avatarUrl)
            .crossfade(true)
            .build(),
        contentDescription = name,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(CircleShape),
        loading = { Fallback() },
        error = { Fallback() }
    )
}
