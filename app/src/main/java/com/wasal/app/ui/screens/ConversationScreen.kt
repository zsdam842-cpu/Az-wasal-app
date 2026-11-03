package com.wasal.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.graphics.BitmapFactory
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import coil.compose.SubcomposeAsyncImage
import com.wasal.app.data.model.Message
import com.wasal.app.data.model.MessageStatus
import com.wasal.app.data.model.isLocalPending
import com.wasal.app.ui.components.WasalAvatar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private val ChatBackground = Color(0xFF000E26)
private val ChatHeader = Color(0xFF001636)
private val ChatBubbleMine = Color(0xFF0A2A52)
private val ChatBubbleOther = Color(0xFF0E2E5D)
private val ChatAccent = Color(0xFF2BA0FF)
private val ChatText = Color(0xFFEAF3FF)
private val ChatMuted = Color(0xFF8FA9C9)
private val ReadStatusColor = Color(0xFF2BA0FF)
private val FailedStatusColor = Color(0xFFFF6B6B)
private val QuoteGreen = Color(0xFF00BB85)
private val QuoteBackground = Color(0xFF00152F)
private val InputPill = Color(0xFF001B3C)

@Composable
fun ConversationScreen(
    title: String,
    otherAvatarUrl: String? = null,
    otherBio: String = "",
    messages: List<Message> = emptyList(),
    onBack: () -> Unit = {},
    onSendMessage: (String, String?) -> Unit = { _, _ -> },
    onAttachmentClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    onEditMessage: (Message, String) -> Unit = { _, _ -> },
    onClearChat: () -> Unit = {},
    onArchive: () -> Unit = {},
    isLoading: Boolean = false,
    isSending: Boolean = false,
    isOtherTyping: Boolean = false,
    onDraftChanged: (Boolean) -> Unit = {},
    onDeleteMessage: (Message, Boolean) -> Unit = { _, _ -> },
    onRetryMedia: (Message) -> Unit = {},
    onReact: (Message, String) -> Unit = { _, _ -> },
    replyTarget: Message? = null,
    onReply: (Message?) -> Unit = {},
    currentUserCode: String = "",
    onVoiceRecordStart: () -> Unit = {},
    onVoiceRecordStop: () -> Unit = {},
    isRecordingVoice: Boolean = false,
    statusMessage: String? = null,
    mediaAccessToken: String = "",
    onStartVoiceCall: () -> Unit = {},
    onStartVideoCall: () -> Unit = {}
) {
    var draft by remember { mutableStateOf("") }
    var showConversationMenu by remember { mutableStateOf(false) }
    var showMediaList by remember { mutableStateOf(false) }
    var showClearChatConfirm by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var editDraft by remember { mutableStateOf("") }
    var actionTarget by remember { mutableStateOf<Message?>(null) }
    var lastTypingState by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var localHint by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()

    BackHandler {
        onBack()
    }

    LaunchedEffect(messages.size, isOtherTyping) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        val patternBrush = remember(context) {
            val bitmap = BitmapFactory.decodeResource(
                context.resources,
                com.wasal.app.R.drawable.chat_pattern
            )

            ShaderBrush(
                ImageShader(
                    bitmap.asImageBitmap(),
                    TileMode.Repeated,
                    TileMode.Repeated
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ChatBackground)
                .drawBehind { drawRect(brush = patternBrush) }
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

                WasalAvatar(
                    name = title,
                    avatarUrl = otherAvatarUrl,
                    size = 42.dp,
                    background = ChatBubbleOther,
                    accent = ChatAccent
                )

                Spacer(Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = ChatText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = when {
                            isOtherTyping -> "جارٍ الكتابة…"
                            otherBio.isNotBlank() -> otherBio
                            else -> "محادثة وصال"
                        },
                        color = if (isOtherTyping) ChatAccent else ChatMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onStartVideoCall
                ) {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = "مكالمة فيديو",
                        tint = ChatText
                    )
                }

                IconButton(
                    onClick = onStartVoiceCall
                ) {
                    Icon(
                        Icons.Default.Call,
                        contentDescription = "مكالمة صوتية",
                        tint = ChatText
                    )
                }

                Box {
                    IconButton(onClick = { showConversationMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "خيارات",
                            tint = ChatText
                        )
                    }

                    DropdownMenu(
                        expanded = showConversationMenu,
                        onDismissRequest = { showConversationMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("وسائط وروابط ومستندات") },
                            onClick = {
                                showConversationMenu = false
                                showMediaList = true
                            }
                        )
                        val lastMineText = messages.lastOrNull {
                            it.isMine && it.type.equals("text", true) && !it.deletedForEveryone
                        }
                        DropdownMenuItem(
                            text = { Text("تعديل آخر رسالة أرسلتها") },
                            enabled = lastMineText != null,
                            onClick = {
                                showConversationMenu = false
                                editingMessage = lastMineText
                                editDraft = lastMineText?.content.orEmpty()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("أرشفة المحادثة") },
                            onClick = {
                                showConversationMenu = false
                                onArchive()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("حذف محتوى الدردشة") },
                            onClick = {
                                showConversationMenu = false
                                showClearChatConfirm = true
                            }
                        )
                    }
                }
            }

            if (isOtherTyping) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ChatHeader)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "جارٍ الكتابة",
                        color = ChatAccent,
                        fontSize = 12.sp
                    )

                    Spacer(Modifier.width(6.dp))

                    AnimatedDots(color = ChatAccent)
                }
            }

            val bannerText = localHint ?: statusMessage

            if (bannerText != null) {
                Text(
                    text = bannerText,
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
                                otherName = title,
                                otherAvatarUrl = otherAvatarUrl,
                                currentUserCode = currentUserCode,
                                mediaAccessToken = mediaAccessToken,
                                selected = actionTarget?.id == message.id,
                                onLongPress = { actionTarget = it },
                                onSwipeReply = { onReply(it) },
                                onRetryMedia = onRetryMedia
                            )
                        }

                        if (isOtherTyping) {
                            item(key = "typing-indicator") {
                                TypingBubble()
                            }
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

            replyTarget?.let { target ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ChatBubbleOther)
                        .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(34.dp)
                            .background(ChatAccent, RoundedCornerShape(2.dp))
                    )

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (target.isMine) {
                                "رد على رسالتك"
                            } else {
                                "رد على $title"
                            },
                            color = ChatAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(2.dp))

                        Text(
                            text = quoteSnippet(target),
                            color = ChatMuted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { onReply(null) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "إلغاء الرد",
                            tint = ChatMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ChatHeader)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(ChatAccent)
                        .clickable {
                            when {
                                isRecordingVoice -> onVoiceRecordStop()

                                draft.isNotBlank() && !isSending -> {
                                    onSendMessage(draft.trim(), replyTarget?.id)
                                    draft = ""
                                    onReply(null)
                                }

                                !isSending -> onVoiceRecordStart()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isRecordingVoice -> Icon(
                            Icons.Default.Stop,
                            contentDescription = "إيقاف التسجيل وإرسال الرسالة الصوتية",
                            tint = Color.White
                        )

                        isSending -> CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )

                        draft.isNotBlank() -> Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "إرسال",
                            tint = Color.White
                        )

                        else -> Icon(
                            Icons.Default.Mic,
                            contentDescription = "تسجيل رسالة صوتية",
                            tint = Color.White
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(InputPill, RoundedCornerShape(26.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onAttachmentClick,
                        enabled = !isSending,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = "كاميرا",
                            tint = ChatMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = onAttachmentClick,
                        enabled = !isSending,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = "إرفاق ملف",
                            tint = ChatMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    BasicTextField(
                        value = draft,
                        onValueChange = {
                            draft = it

                            val typingNow = it.isNotBlank()
                            if (typingNow != lastTypingState) {
                                lastTypingState = typingNow
                                onDraftChanged(typingNow)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp, vertical = 12.dp),
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
                                        text = if (replyTarget != null) {
                                            "اكتب ردًا..."
                                        } else {
                                            "مراسلة"
                                        },
                                        color = ChatMuted,
                                        fontSize = 15.sp
                                    )
                                }

                                innerTextField()
                            }
                        }
                    )

                    IconButton(
                        onClick = {
                            draft = draft + "🙂"
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.EmojiEmotions,
                            contentDescription = "إيموجي",
                            tint = ChatMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }

    if (showMediaList) {
        val media = messages.filter {
            it.type.lowercase() in setOf("image", "video", "file", "link", "audio", "voice")
        }
        AlertDialog(
            onDismissRequest = { showMediaList = false },
            title = { Text("وسائط وروابط ومستندات") },
            text = {
                if (media.isEmpty()) {
                    Text("لا توجد وسائط أو روابط في هذه المحادثة.", color = ChatMuted)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(media) { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val kind = item.type.lowercase()

                                // معاينة مصغّرة بدل طباعة الرابط الخام.
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ChatBubbleOther),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (kind == "image" && item.content.isNotBlank()) {
                                        SubcomposeAsyncImage(
                                            model = ImageRequest.Builder(
                                                androidx.compose.ui.platform
                                                    .LocalContext.current
                                            )
                                                .data(item.content)
                                                .build(),
                                            contentDescription = "صورة",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                            loading = { },
                                            error = {
                                                Icon(
                                                    Icons.Default.Image,
                                                    contentDescription = null,
                                                    tint = ChatMuted
                                                )
                                            }
                                        )
                                    } else {
                                        Icon(
                                            imageVector = when (kind) {
                                                "video" -> Icons.Default.PlayCircle
                                                "audio", "voice" -> Icons.Default.PlayCircle
                                                "file" -> Icons.Default.Description
                                                else -> Icons.Default.Link
                                            },
                                            contentDescription = null,
                                            tint = ChatAccent
                                        )
                                    }
                                }

                                Spacer(Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = when (kind) {
                                            "image" -> "صورة"
                                            "video" -> "فيديو"
                                            "audio", "voice" -> "رسالة صوتية"
                                            "file" -> "مستند"
                                            else -> "رابط"
                                        },
                                        color = ChatText,
                                        fontSize = 13.sp
                                    )

                                    if (kind == "link" || kind == "file") {
                                        Text(
                                            text = item.content.take(70),
                                            color = ChatMuted,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Text(
                                    text = item.time,
                                    color = ChatMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMediaList = false }) { Text("إغلاق") }
            }
        )
    }

    if (showClearChatConfirm) {
        AlertDialog(
            onDismissRequest = { showClearChatConfirm = false },
            title = { Text("حذف محتوى الدردشة") },
            text = { Text("ستُحذف كل رسائل هذه المحادثة لديك فقط، وتبقى عند الطرف الآخر.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearChatConfirm = false
                    onClearChat()
                }) { Text("حذف", color = FailedStatusColor) }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatConfirm = false }) { Text("إلغاء") }
            }
        )
    }

    editingMessage?.let { target ->
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("تعديل الرسالة") },
            text = {
                OutlinedTextField(
                    value = editDraft,
                    onValueChange = { editDraft = it },
                    label = { Text("نص الرسالة") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val updated = editDraft.trim()
                    if (updated.isNotEmpty()) {
                        onEditMessage(target, updated)
                    }
                    editingMessage = null
                }) { Text("حفظ") }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) { Text("إلغاء") }
            }
        )
    }

    actionTarget?.let { target ->
        @OptIn(ExperimentalMaterial3Api::class)
        ModalBottomSheet(
            onDismissRequest = { actionTarget = null },
            containerColor = ChatHeader
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 18.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("❤️", "👍", "😂", "😮", "😢", "🙏").forEach { emoji ->
                        val mine = target.reactions.any {
                            it.userCode == currentUserCode && it.emoji == emoji
                        }

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (mine) ChatBubbleMine else ChatBubbleOther
                                )
                                .clickable {
                                    onReact(target, emoji)
                                    actionTarget = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 20.sp)
                        }
                    }
                }

                if (!target.isLocalPending) {
                    SheetAction(
                        label = "نسخ النص",
                        icon = Icons.Default.ContentCopy,
                        enabled = target.content.isNotBlank() &&
                            !target.deletedForEveryone
                    ) {
                        clipboard.setText(AnnotatedString(target.content))
                        actionTarget = null
                    }

                    SheetAction(
                        label = "رد",
                        icon = Icons.AutoMirrored.Filled.Reply
                    ) {
                        onReply(target)
                        actionTarget = null
                    }

                    if (target.isMine &&
                        target.type.equals("text", true) &&
                        !target.deletedForEveryone
                    ) {
                        SheetAction(
                            label = "تعديل الرسالة",
                            icon = Icons.Default.Edit
                        ) {
                            editingMessage = target
                            editDraft = target.content
                            actionTarget = null
                        }
                    }

                    SheetAction(
                        label = "حذف لدي",
                        icon = Icons.Default.Delete
                    ) {
                        onDeleteMessage(target, false)
                        actionTarget = null
                    }

                    if (target.isMine) {
                        SheetAction(
                            label = "حذف لدى الجميع",
                            icon = Icons.Default.Delete,
                            tint = FailedStatusColor
                        ) {
                            onDeleteMessage(target, true)
                            actionTarget = null
                        }
                    }
                }

                SheetAction(
                    label = "إلغاء",
                    icon = Icons.Default.Close
                ) {
                    actionTarget = null
                }
            }
        }
    }
}

@Composable
private fun VoicePlayer(
    url: String,
    isMine: Boolean,
    senderName: String = "",
    senderAvatarUrl: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isPlaying by remember(url) { mutableStateOf(false) }
    var isPreparing by remember(url) { mutableStateOf(false) }
    var durationSeconds by remember(url) { mutableIntStateOf(0) }
    var progress by remember(url) { mutableFloatStateOf(0f) }
    var player by remember(url) {
        mutableStateOf<android.media.MediaPlayer?>(null)
    }

    fun releasePlayer() {
        runCatching { player?.release() }
        player = null
        isPlaying = false
        progress = 0f
    }

    DisposableEffect(url) {
        onDispose { releasePlayer() }
    }

    // تحديث مؤشر التقدم أثناء التشغيل
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val current = player

            if (current != null && current.duration > 0) {
                progress = (current.currentPosition.toFloat() / current.duration)
                    .coerceIn(0f, 1f)
            }

            kotlinx.coroutines.delay(200)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.widthIn(min = 210.dp, max = 260.dp)
    ) {
        IconButton(
            enabled = url.isNotBlank() && !isPreparing,
            onClick = {
                if (isPlaying) {
                    releasePlayer()
                    return@IconButton
                }

                releasePlayer()
                isPreparing = true

                scope.launch {
                    // تنزيل الملف ثم تشغيله محليًا: البث المباشر كان ينتج
                    // صوتًا مشوّشًا وصفيرًا في بعض الأجهزة.
                    val file = runCatching {
                        withContext(Dispatchers.IO) {
                            val request = okhttp3.Request.Builder().url(url).build()

                            val response = com.wasal.app.data.network
                                .CoreApiClient.client
                                .newCall(request)
                                .execute()

                            if (!response.isSuccessful) {
                                response.close()
                                throw java.io.IOException("تعذر تنزيل الرسالة الصوتية.")
                            }

                            val body = response.body
                                ?: throw java.io.IOException("الملف فارغ.")

                            val target = java.io.File(
                                context.cacheDir,
                                "voice_" + url.hashCode() + ".m4a"
                            )

                            body.byteStream().use { input ->
                                target.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }

                            response.close()
                            target
                        }
                    }.getOrNull()

                    if (file == null || !file.exists() || file.length() == 0L) {
                        isPreparing = false
                        return@launch
                    }

                    val newPlayer = android.media.MediaPlayer()

                    runCatching {
                        newPlayer.setAudioAttributes(
                            android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                .setContentType(
                                    android.media.AudioAttributes.CONTENT_TYPE_SPEECH
                                )
                                .build()
                        )
                        newPlayer.setDataSource(file.absolutePath)
                        newPlayer.setOnCompletionListener { releasePlayer() }
                        newPlayer.prepare()

                        durationSeconds = (newPlayer.duration / 1000).coerceAtLeast(1)

                        newPlayer.start()

                        player = newPlayer
                        isPlaying = true
                    }.onFailure {
                        runCatching { newPlayer.release() }
                        isPlaying = false
                    }

                    isPreparing = false
                }
            }
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "تشغيل الرسالة الصوتية",
                tint = ChatAccent,
                modifier = Modifier.size(28.dp)
            )
        }

        // موجات صوتية ثابتة تعطي شكل الرسالة الصوتية المعروف.
        Row(
            modifier = Modifier
                .weight(1f)
                .height(22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            val heights = listOf(
                8, 14, 20, 11, 17, 9, 22, 13, 18, 10,
                16, 21, 12, 8, 19, 14, 9, 17, 11, 20
            )

            heights.forEachIndexed { index, h ->
                val played = index.toFloat() / heights.size <= progress

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((h * (if (played) 1f else 0.75f)).dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (played) ChatAccent else ChatMuted.copy(alpha = 0.55f)
                        )
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Text(
            text = if (durationSeconds > 0) {
                formatVoiceDuration(durationSeconds)
            } else {
                "0:00"
            },
            color = ChatMuted,
            fontSize = 11.sp
        )

        // صورة المرسل المصغّرة مع شارة ميكروفون، للرسائل الواردة فقط.
        if (!isMine) {
            Spacer(Modifier.width(8.dp))

            Box {
                WasalAvatar(
                    name = senderName.ifBlank { "مستخدم وصال" },
                    avatarUrl = senderAvatarUrl,
                    size = 30.dp,
                    background = ChatBubbleMine,
                    accent = ChatAccent
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(ChatAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(9.dp)
                    )
                }
            }
        }
    }
}

private fun formatVoiceDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return "$minutes:" + seconds.toString().padStart(2, '0')
}

@Composable
private fun AnimatedDots(
    color: Color,
    dotSize: androidx.compose.ui.unit.Dp = 6.dp
) {
    val transition = rememberInfiniteTransition(label = "typing-dots")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 550,
                        delayMillis = index * 170,
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot-$index"
            )

            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(color.copy(alpha = alpha))
            )
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 13.dp, vertical = 12.dp)
                .background(ChatBubbleOther, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            AnimatedDots(color = ChatAccent, dotSize = 7.dp)
        }
    }
}

@Composable
private fun SheetAction(
    label: String,
    icon: ImageVector,
    tint: Color = ChatText,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) tint else ChatMuted,
                modifier = Modifier.size(20.dp)
            )

            Spacer(Modifier.width(12.dp))

            Text(
                text = label,
                color = if (enabled) tint else ChatMuted,
                fontSize = 15.sp
            )
        }
    }
}

private fun quoteSnippet(message: Message): String = when (message.type.lowercase()) {
    "image" -> "صورة"
    "video" -> "فيديو"
    "audio", "voice" -> "رسالة صوتية"
    "file" -> "مستند"
    else -> message.content
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    otherName: String,
    otherAvatarUrl: String? = null,
    currentUserCode: String,
    mediaAccessToken: String,
    selected: Boolean,
    onLongPress: (Message) -> Unit,
    onSwipeReply: (Message) -> Unit,
    onRetryMedia: (Message) -> Unit = {}
) {
    val density = LocalDensity.current
    val threshold = with(density) { 64.dp.toPx() }

    var dragOffset by remember(message.id) { mutableFloatStateOf(0f) }

    Box(modifier = Modifier.fillMaxWidth()) {
        // أيقونة الرد تظهر خلف الرسالة أثناء السحب عليها
        if (dragOffset != 0f) {
            Icon(
                Icons.AutoMirrored.Filled.Reply,
                contentDescription = null,
                tint = ChatAccent,
                modifier = Modifier
                    .align(
                        if (dragOffset > 0f) {
                            Alignment.CenterStart
                        } else {
                            Alignment.CenterEnd
                        }
                    )
                    .padding(horizontal = 10.dp)
                    .size(22.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(dragOffset.roundToInt(), 0) }
                .pointerInput(message.id, message.isLocalPending) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (abs(dragOffset) >= threshold) {
                                onSwipeReply(message)
                            }
                            dragOffset = 0f
                        },
                        onDragCancel = { dragOffset = 0f },
                        onHorizontalDrag = { _, amount ->
                            if (!message.isLocalPending) {
                                dragOffset = (dragOffset + amount)
                                    .coerceIn(-threshold * 2, threshold * 2)
                            }
                        }
                    )
                },
            horizontalArrangement = if (message.isMine) {
                Arrangement.Start
            } else {
                Arrangement.End
            }
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .padding(vertical = 2.dp),
                horizontalAlignment = if (message.isMine) Alignment.Start else Alignment.End
            ) {
                val bubbleShape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (message.isMine) 4.dp else 16.dp,
                    bottomEnd = if (message.isMine) 16.dp else 4.dp
                )

                Box(
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { onLongPress(message) }
                        )
                        .background(
                            color = if (message.isMine) {
                                ChatBubbleMine
                            } else {
                                ChatBubbleOther
                            },
                            shape = bubbleShape
                        )
                        .then(
                            if (selected) {
                                Modifier.border(1.5.dp, ChatAccent, bubbleShape)
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 13.dp, vertical = 9.dp)
                ) {
                    Column {
                        if (!message.isLocalPending && message.replyToId != null) {
                            QuotedMessage(
                                message = message,
                                otherName = otherName,
                                currentUserCode = currentUserCode
                            )

                            Spacer(Modifier.height(6.dp))
                        }

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
                                    imageUrl = message.localUri ?: message.content,
                                    accessToken = if (message.localUri == null) {
                                        mediaAccessToken
                                    } else {
                                        ""
                                    },
                                    pending = message.localUri != null &&
                                        message.status == MessageStatus.SENDING,
                                    failed = message.localUri != null &&
                                        message.status == MessageStatus.FAILED,
                                    onRetry = { onRetryMedia(message) }
                                )
                            }

                            "video" -> {
                                MessageVideo(
                                    videoUrl = message.localUri ?: message.content,
                                    accessToken = if (message.localUri == null) {
                                        mediaAccessToken
                                    } else {
                                        ""
                                    },
                                    pending = message.localUri != null &&
                                        message.status == MessageStatus.SENDING,
                                    failed = message.localUri != null &&
                                        message.status == MessageStatus.FAILED,
                                    onRetry = { onRetryMedia(message) }
                                )
                            }

                            "audio", "voice" -> {
                                VoicePlayer(
                                    url = message.localUri ?: message.content,
                                    isMine = message.isMine,
                                    senderName = otherName,
                                    senderAvatarUrl = otherAvatarUrl
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

                    }
                }

                // التاريخ والساعة وعلامة التسليم خارج الفقاعة (تحتها)، كما في
                // تطبيقات المراسلة المعروفة.
                if (message.time.isNotBlank() || message.isMine) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (message.isMine) {
                            MessageStatusIndicator(message.status)
                        }

                        if (message.time.isNotBlank()) {
                            Text(
                                text = message.time,
                                color = ChatMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                if (message.reactions.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))

                    ReactionChips(
                        reactions = message.reactions,
                        currentUserCode = currentUserCode
                    )
                }

            }
        }
    }
}

@Composable
private fun QuotedMessage(
    message: Message,
    otherName: String,
    currentUserCode: String
) {
    val senderLabel = if (
        message.replyToSenderId.isNotBlank() &&
        message.replyToSenderId == currentUserCode
    ) {
        "أنت"
    } else {
        otherName
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                QuoteBackground,
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(30.dp)
                .background(QuoteGreen, RoundedCornerShape(2.dp))
        )

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = senderLabel,
                color = QuoteGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = message.replyToPreview.ifBlank { "رسالة" },
                color = ChatMuted,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ReactionChips(
    reactions: List<com.wasal.app.data.model.MessageReaction>,
    currentUserCode: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        reactions.groupBy { it.emoji }.forEach { (emoji, users) ->
            val mine = users.any { it.userCode == currentUserCode }

            Row(
                modifier = Modifier
                    .background(
                        if (mine) ChatBubbleMine else ChatBubbleOther,
                        RoundedCornerShape(12.dp)
                    )
                    .border(
                        1.dp,
                        if (mine) ChatAccent else Color.Transparent,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = emoji, fontSize = 12.sp)

                if (users.size > 1) {
                    Spacer(Modifier.width(3.dp))

                    Text(
                        text = users.size.toString(),
                        color = ChatMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageImage(
    imageUrl: String,
    accessToken: String,
    pending: Boolean = false,
    failed: Boolean = false,
    onRetry: () -> Unit = {}
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

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
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

        if (pending || failed) {
            MediaSendOverlay(
                failed = failed,
                onRetry = onRetry,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }
}

@Composable
private fun MessageVideo(
    videoUrl: String,
    accessToken: String,
    pending: Boolean = false,
    failed: Boolean = false,
    onRetry: () -> Unit = {}
) {
    val context = LocalContext.current

    if (videoUrl.isBlank()) {
        VideoPlaceholder()
        return
    }

    val videoRequest = remember(videoUrl, accessToken) {
        ImageRequest.Builder(context)
            .data(videoUrl)
            .videoFrameMillis(500)
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

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = videoRequest,
            contentDescription = "فيديو مرسل",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp, max = 300.dp)
                .clip(RoundedCornerShape(12.dp)),
            loading = { VideoPlaceholder() },
            error = { VideoPlaceholder() }
        )

        if (pending || failed) {
            MediaSendOverlay(
                failed = failed,
                onRetry = onRetry,
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(12.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = "فيديو",
                    tint = ChatText,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

/**
 * غطاء شفاف فوق المرفق أثناء الرفع: دوّار بسيط مثل واتساب،
 * وعند الفشل زر إعادة محاولة بدل نص أعلى الشاشة.
 */
@Composable
private fun MediaSendOverlay(
    failed: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = if (failed) 0.55f else 0.35f))
            .then(
                if (failed) {
                    Modifier.clickable { onRetry() }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (failed) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "إعادة المحاولة",
                    tint = ChatText,
                    modifier = Modifier.size(30.dp)
                )

                Spacer(Modifier.size(6.dp))

                Text(
                    text = "تعذر الإرسال — اضغط للمحاولة مجددًا",
                    color = ChatText,
                    fontSize = 12.sp
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(30.dp)
                )

                Spacer(Modifier.size(8.dp))

                Text(
                    text = "جارٍ الإرسال…",
                    color = ChatText,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun VideoPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ChatBubbleOther),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.PlayCircle,
            contentDescription = null,
            tint = ChatAccent,
            modifier = Modifier.size(38.dp)
        )
    }
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
    when (status) {
        MessageStatus.SENDING -> Icon(
            Icons.Default.Schedule,
            contentDescription = "جارٍ الإرسال",
            tint = ChatMuted,
            modifier = Modifier.size(13.dp)
        )

        MessageStatus.SENT -> Icon(
            Icons.Default.Check,
            contentDescription = "تم الإرسال",
            tint = ChatMuted,
            modifier = Modifier.size(14.dp)
        )

        MessageStatus.DELIVERED -> Icon(
            Icons.Default.DoneAll,
            contentDescription = "تم التسليم",
            tint = ChatMuted,
            modifier = Modifier.size(16.dp)
        )

        MessageStatus.READ -> Icon(
            Icons.Default.DoneAll,
            contentDescription = "تمت القراءة",
            tint = ReadStatusColor,
            modifier = Modifier.size(16.dp)
        )

        MessageStatus.FAILED -> Icon(
            Icons.Default.Schedule,
            contentDescription = "فشل الإرسال",
            tint = FailedStatusColor,
            modifier = Modifier.size(13.dp)
        )
    }
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
