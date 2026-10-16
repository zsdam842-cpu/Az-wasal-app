package com.wasal.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.request.ImageRequest
import coil.compose.SubcomposeAsyncImage
import com.wasal.app.data.model.Message
import com.wasal.app.data.model.MessageStatus

private val ChatBackground = Color(0xFF0B1020)
private val ChatHeader = Color(0xFF101B3A)
private val ChatBubbleMine = Color(0xFF344E9A)
private val ChatBubbleOther = Color(0xFF172342)
private val ChatAccent = Color(0xFF6C8CFF)
private val ChatText = Color(0xFFF4F6FF)
private val ChatMuted = Color(0xFF9AA7C7)
private val ReadStatusColor = Color(0xFF55D6FF)
private val FailedStatusColor = Color(0xFFFF6B6B)

@Composable
fun ConversationScreen(
    title: String,
    messages: List<Message> = emptyList(),
    onBack: () -> Unit = {},
    onSendMessage: (String) -> Unit = {},
    onAttachmentClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    isLoading: Boolean = false,
    isSending: Boolean = false,
    isOtherTyping: Boolean = false,
    onDraftChanged: (Boolean) -> Unit = {},
    onDeleteMessage: (Message, Boolean) -> Unit = { _, _ -> },
    onVoiceRecordStart: () -> Unit = {},
    onVoiceRecordStop: () -> Unit = {},
    isRecordingVoice: Boolean = false,
    statusMessage: String? = null,
    mediaAccessToken: String = ""
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    BackHandler {
        onBack()
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ChatBackground)
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ChatHeader)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = ChatText
                    )
                }

                Surface(
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    color = ChatBubbleOther
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = title.trim().take(1).ifBlank { "و" },
                            color = ChatAccent,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = ChatText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = if (isOtherTyping) {
                            "جارٍ الكتابة…"
                        } else {
                            "محادثة وصال"
                        },
                        color = if (isOtherTyping) ChatAccent else ChatMuted,
                        fontSize = 12.sp
                    )
                }

                IconButton(onClick = onMoreClick) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "خيارات",
                        tint = ChatText
                    )
                }
            }

            if (statusMessage != null) {
                Text(
                    text = statusMessage,
                    color = ChatAccent,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ChatHeader)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }

            when {
                isLoading && messages.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = ChatAccent)

                            Spacer(Modifier.size(12.dp))

                            Text(
                                text = "جارٍ تحميل الرسائل...",
                                color = ChatMuted
                            )
                        }
                    }
                }

                messages.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "ابدأ محادثتك",
                                color = ChatText,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.size(8.dp))

                            Text(
                                text = "اكتب رسالة أو أرفق صورة أو فيديو",
                                color = ChatMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        reverseLayout = false,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = messages,
                            key = { it.id }
                        ) { message ->
                            MessageBubble(
                                message = message,
                                mediaAccessToken = mediaAccessToken,
                                onDeleteMessage = onDeleteMessage
                            )
                        }
                    }
                }
            }

            if (isRecordingVoice) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(FailedStatusColor)
                    )

                    Spacer(Modifier.width(6.dp))

                    Text(
                        text = "جارٍ تسجيل رسالة صوتية... اضغط زر الإيقاف للإرسال",
                        color = ChatMuted,
                        fontSize = 12.sp
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ChatHeader)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onAttachmentClick,
                    enabled = !isSending
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = "إرفاق صورة أو فيديو أو ملف",
                        tint = ChatAccent
                    )
                }

                BasicTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        onDraftChanged(it.isNotBlank())
                    },
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            ChatBubbleOther,
                            RoundedCornerShape(22.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    enabled = !isSending,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = ChatText,
                        fontSize = 15.sp
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(
                        ChatAccent
                    ),
                    decorationBox = { innerTextField ->
                        Box {
                            if (draft.isEmpty()) {
                                Text(
                                    text = "اكتب رسالتك...",
                                    color = ChatMuted,
                                    fontSize = 14.sp
                                )
                            }

                            innerTextField()
                        }
                    }
                )

                Spacer(Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        when {
                            isRecordingVoice -> {
                                onVoiceRecordStop()
                            }

                            draft.isNotBlank() && !isSending -> {
                                onSendMessage(draft.trim())
                                draft = ""
                            }

                            !isSending -> {
                                onVoiceRecordStart()
                            }
                        }
                    },
                    enabled = true
                ) {
                    when {
                        isRecordingVoice -> {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = "إيقاف التسجيل وإرسال الرسالة الصوتية",
                                tint = FailedStatusColor
                            )
                        }

                        draft.isNotBlank() || isSending -> {
                            if (isSending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = ChatAccent,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "إرسال",
                                    tint = ChatAccent
                                )
                            }
                        }

                        else -> {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "تسجيل رسالة صوتية",
                                tint = ChatAccent
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoicePlayer(
    url: String,
    isMine: Boolean
) {
    val context = LocalContext.current

    var isPlaying by remember(url) {
        mutableStateOf(false)
    }

    var player by remember(url) {
        mutableStateOf<android.media.MediaPlayer?>(null)
    }

    fun releasePlayer() {
        runCatching {
            player?.release()
        }

        player = null
        isPlaying = false
    }

    DisposableEffect(url) {
        onDispose {
            releasePlayer()
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        IconButton(
            onClick = {
                if (isPlaying) {
                    releasePlayer()
                    return@IconButton
                }

                releasePlayer()

                val newPlayer = android.media.MediaPlayer()

                runCatching {
                    newPlayer.setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    newPlayer.setDataSource(url)
                    newPlayer.setOnCompletionListener {
                        releasePlayer()
                    }
                    newPlayer.prepare()
                    newPlayer.start()

                    player = newPlayer
                    isPlaying = true
                }.onFailure {
                    runCatching { newPlayer.release() }
                    isPlaying = false
                }
            },
            enabled = url.isNotBlank()
        ) {
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = if (isPlaying) {
                    "إيقاف تشغيل الرسالة الصوتية"
                } else {
                    "تشغيل الرسالة الصوتية"
                },
                tint = ChatAccent
            )
        }

        Text(
            text = if (isPlaying) {
                "جارٍ التشغيل... اضغط للإيقاف"
            } else {
                "رسالة صوتية — اضغط للتشغيل"
            },
            color = ChatText,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun MessageBubble(
    message: Message,
    mediaAccessToken: String,
    onDeleteMessage: (Message, Boolean) -> Unit = { _, _ -> }
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isMine) {
            Arrangement.Start
        } else {
            Arrangement.End
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(
                    color = if (message.isMine) {
                        ChatBubbleMine
                    } else {
                        ChatBubbleOther
                    },
                    shape = RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (message.isMine) 4.dp else 16.dp,
                        bottomEnd = if (message.isMine) 16.dp else 4.dp
                    )
                )
                .padding(horizontal = 13.dp, vertical = 9.dp)
        ) {
            when (message.type.lowercase()) {
                "deleted" -> {
                    Text(
                        text = "هذه الرسالة محذوفة",
                        color = ChatMuted,
                        fontSize = 15.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }

                "image" -> {
                    MessageImage(
                        imageUrl = message.content,
                        accessToken = mediaAccessToken
                    )
                }

                "video" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = null,
                            tint = ChatAccent
                        )

                        Spacer(Modifier.width(6.dp))

                        Text(
                            text = "فيديو مرفق",
                            color = ChatText,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    AttachmentLink(message.content)
                }

                "audio", "voice" -> {
                    VoicePlayer(
                        url = message.content,
                        isMine = message.isMine
                    )
                }

                "file" -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = ChatAccent
                        )

                        Spacer(Modifier.width(6.dp))

                        Text(
                            text = "ملف مرفق",
                            color = ChatText
                        )
                    }

                    AttachmentLink(message.content)
                }

                else -> {
                    Text(
                        text = message.content,
                        color = ChatText,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(Modifier.size(4.dp))

            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (message.isMine && !message.deletedForEveryone) {
                    IconButton(
                        onClick = {
                            onDeleteMessage(message, true)
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "حذف الرسالة لدى الجميع",
                            tint = ChatMuted,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                if (message.time.isNotBlank()) {
                    Text(
                        text = message.time,
                        color = ChatMuted,
                        fontSize = 10.sp
                    )
                }

                if (message.isMine) {
                    MessageStatusIndicator(message.status)
                }
            }
        }
    }
}

@Composable
private fun MessageImage(
    imageUrl: String,
    accessToken: String
) {
    val context = LocalContext.current

    if (imageUrl.isBlank()) {
        ImageLoadError()
        return
    }

    val imageRequest = remember(imageUrl, accessToken) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .apply {
                if (accessToken.isNotBlank()) {
                    addHeader(
                        "Authorization",
                        "Bearer $accessToken"
                    )
                }
            }
            .build()
    }

    SubcomposeAsyncImage(
        model = imageRequest,
        contentDescription = "صورة مرسلة",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .clip(RoundedCornerShape(12.dp)),
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 220.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = ChatAccent,
                    strokeWidth = 2.dp
                )
            }
        },
        error = {
            ImageLoadError()
        }
    )
}

@Composable
private fun ImageLoadError() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 90.dp)
            .background(
                ChatBubbleOther,
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Image,
                contentDescription = null,
                tint = ChatMuted,
                modifier = Modifier.size(30.dp)
            )

            Spacer(Modifier.size(6.dp))

            Text(
                text = "تعذر تحميل الصورة",
                color = ChatMuted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun MessageStatusIndicator(status: MessageStatus) {
    val (symbol, color, description) = when (status) {
        MessageStatus.SENDING -> Triple("◷", ChatMuted, "جارٍ الإرسال")
        MessageStatus.SENT -> Triple("✓", ChatMuted, "تم الإرسال")
        MessageStatus.DELIVERED -> Triple("✓✓", ChatMuted, "تم التسليم")
        MessageStatus.READ -> Triple("✓✓", ReadStatusColor, "تمت القراءة")
        MessageStatus.FAILED -> Triple("!", FailedStatusColor, "فشل الإرسال")
    }

    Text(
        text = symbol,
        color = color,
        fontSize = if (status == MessageStatus.DELIVERED ||
            status == MessageStatus.READ
        ) 11.sp else 12.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun AttachmentLink(url: String) {
    if (url.isNotBlank()) {
        Text(
            text = url,
            color = ChatAccent,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
