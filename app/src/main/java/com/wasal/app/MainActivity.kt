package com.wasal.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.wasal.app.data.local.AuthDataStore
import com.wasal.app.util.VoiceRecorderManager
import com.wasal.app.data.local.SettingsDataStore
import com.wasal.app.data.model.Contact
import com.wasal.app.data.model.Conversation
import com.wasal.app.data.model.Message
import com.wasal.app.data.model.MessageStatus
import com.wasal.app.data.network.CoreApiClient
import com.wasal.app.data.network.SocketService
import com.wasal.app.data.repository.AuthRepository
import com.wasal.app.data.repository.ChatRepository
import com.wasal.app.data.repository.ContactsRepository
import com.wasal.app.data.repository.MediaRepository
import com.wasal.app.data.repository.MessageRepository
import com.wasal.app.data.repository.ProfileRepository
import com.wasal.app.ui.screens.CallsScreen
import com.wasal.app.ui.screens.ChatsScreen
import com.wasal.app.ui.screens.ContactsScreen
import com.wasal.app.ui.screens.ConversationScreen
import com.wasal.app.ui.screens.SettingsScreen
import com.wasal.app.ui.screens.StatusesScreen
import com.wasal.app.ui.theme.WasalTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val WasalNavy = Color(0xFF101B3A)
private val WasalBackground = Color(0xFF0B1020)
private val WasalCard = Color(0xFF172342)
private val WasalAccent = Color(0xFF6C8CFF)
private val WasalText = Color(0xFFF4F6FF)
private val WasalMuted = Color(0xFF9AA7C7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val settingsDataStore = remember {
                SettingsDataStore(applicationContext)
            }

            val systemDarkMode by settingsDataStore.darkMode
                .collectAsState(initial = true)

            val systemUseThemeSetting by settingsDataStore.useSystemTheme
                .collectAsState(initial = false)

            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl
            ) {
                WasalTheme(
                    darkMode = systemDarkMode,
                    useSystemDefault = systemUseThemeSetting
                ) {
                    WasalHomeScreen(
                        applicationContext,
                        settingsDataStore
                    )
                }
            }
        }
    }
}

@Composable
private fun WasalHomeScreen(
    context: Context,
    settingsDataStore: SettingsDataStore
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    var openAddFormSignal by remember { mutableIntStateOf(0) }

    var selectedConversation by remember {
        mutableStateOf<Conversation?>(null)
    }

    var isLoading by remember { mutableStateOf(true) }
    var isAddingContact by remember { mutableStateOf(false) }
    var isSavingDisplayName by remember {
        mutableStateOf(false)
    }

    var isSavingBio by remember {
        mutableStateOf(false)
    }

    var conversations by remember {
        mutableStateOf<List<Conversation>>(emptyList())
    }

    var contacts by remember {
        mutableStateOf<List<Contact>>(emptyList())
    }

    var displayName by remember {
        mutableStateOf("مستخدم وصال")
    }

    var wasalCode by remember {
        mutableStateOf("")
    }

    var bio by remember {
        mutableStateOf("")
    }

    var errorMessage by remember {
        mutableStateOf<String?>(null)
    }

    val authDataStore = remember(context) {
        AuthDataStore(context)
    }

    val darkModeState by settingsDataStore.darkMode
        .collectAsState(initial = true)

    val useSystemThemeState by settingsDataStore.useSystemTheme
        .collectAsState(initial = false)

    val authRepository = remember(authDataStore) {
        AuthRepository(authDataStore)
    }

    val chatRepository = remember(authDataStore) {
        ChatRepository(authDataStore)
    }

    val contactsRepository = remember {
        ContactsRepository()
    }

    val profileRepository = remember {
        ProfileRepository()
    }

    val scope = rememberCoroutineScope()

    suspend fun refreshConversations(
        token: String
    ): List<Conversation> {
        val result = withContext(Dispatchers.IO) {
            chatRepository.getConversations(token)
        }

        val loaded = result.getOrElse {
            throw it
        }

        conversations = loaded
        return loaded
    }

    LaunchedEffect(authDataStore) {
        isLoading = true
        errorMessage = null

        try {
            var token = authDataStore.accessToken.first().orEmpty()
            var userId = authDataStore.userId.first().orEmpty()

            if (token.isBlank() && userId.isBlank()) {
                val result = withContext(Dispatchers.IO) {
                    authRepository.createAccount()
                }

                val createdWasalCode = result.getOrElse {
                    throw it
                }

                wasalCode = createdWasalCode

                token = authDataStore.accessToken.first().orEmpty()
                userId = authDataStore.userId.first().orEmpty()
            }

            if (userId.isBlank()) {
                throw IllegalStateException(
                    "معرّف المستخدم غير موجود على هذا الجهاز."
                )
            }

            refreshConversations(token)

            if (token.isNotBlank()) {
                val profileResult = withContext(Dispatchers.IO) {
                    profileRepository.getMyProfile(
                        userId = userId,
                        accessToken = token
                    )
                }

                profileResult.onSuccess { profile ->
                    displayName = profile.displayName
                    bio = profile.bio

                    if (profile.wasalCode.isNotBlank()) {
                        wasalCode = profile.wasalCode
                    }
                }.onFailure { error ->
                    errorMessage = error.message
                        ?: "تعذر تحديث بيانات الحساب."
                }
            } else if (conversations.isEmpty()) {
                errorMessage =
                    "لا يوجد اتصال بالإنترنت ولا توجد محادثات محفوظة بعد."
            }
        } catch (e: Exception) {
            errorMessage = e.message
                ?: "تعذر تحميل بيانات وصال."
        } finally {
            isLoading = false
        }
    }

    val currentConversation = selectedConversation

    if (currentConversation != null) {
        ConversationFlow(
            context = context,
            authDataStore = authDataStore,
            conversation = currentConversation,
            onBack = {
                selectedConversation = null
            }
        )
        return
    }

    val tabs = listOf(
        "المحادثات",
        "جهات الاتصال",
        "الحالات",
        "المكالمات",
        "الإعدادات"
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = WasalBackground,
        contentColor = WasalText,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WasalNavy)
                    .padding(
                        horizontal = 18.dp,
                        vertical = 16.dp
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "وصال",
                        color = WasalText,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = tabs[selectedTab],
                        color = WasalMuted,
                        fontSize = 13.sp
                    )
                }

                IconButton(
                    onClick = {
                        selectedTab = 0
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "بحث",
                        tint = WasalText,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = WasalNavy,
                contentColor = WasalText
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            Icons.Default.Chat,
                            contentDescription = "المحادثات"
                        )
                    },
                    label = { Text("المحادثات") }
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            Icons.Default.Contacts,
                            contentDescription = "جهات الاتصال"
                        )
                    },
                    label = { Text("جهات الاتصال") }
                )

                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            Icons.Default.Campaign,
                            contentDescription = "الحالات"
                        )
                    },
                    label = { Text("الحالات") }
                )

                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = {
                        Icon(
                            Icons.Default.Call,
                            contentDescription = "المكالمات"
                        )
                    },
                    label = { Text("المكالمات") }
                )

                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "الإعدادات"
                        )
                    },
                    label = { Text("الإعدادات") }
                )
            }
        }
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (isLoading) {
                            Text(
                                text = "جارٍ تحميل المحادثات...",
                                color = WasalMuted,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = 16.dp,
                                        vertical = 8.dp
                                    )
                            )
                        } else if (
                            errorMessage != null &&
                            conversations.isEmpty()
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = WasalMuted,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = 16.dp,
                                        vertical = 8.dp
                                    )
                            )
                        }

                        ChatsScreen(
                            conversations = conversations,
                            onConversationClick = { conversation ->
                                selectedConversation = conversation
                            },
                            onNewConversation = {
                                selectedTab = 1
                                openAddFormSignal += 1
                            }
                        )
                    }
                }

                1 -> {
                    ContactsScreen(
                        contacts = contacts,
                        onContactClick = { contact ->
                            val matchingConversation =
                                conversations.firstOrNull {
                                    it.title == contact.name
                                }

                            if (matchingConversation != null) {
                                selectedConversation =
                                    matchingConversation
                            } else {
                                errorMessage =
                                    "لم يتم العثور على محادثة لهذا المستخدم."
                            }
                        },
                        openAddFormSignal = openAddFormSignal,
                        onAddContact = { code ->
                            if (!isAddingContact) {
                                scope.launch {
                                    isAddingContact = true
                                    errorMessage = null

                                    try {
                                        val token = authDataStore
                                            .accessToken
                                            .first()
                                            .orEmpty()

                                        if (token.isBlank()) {
                                            throw IllegalStateException(
                                                "يلزم الاتصال بالإنترنت وتوفر جلسة دخول لإضافة جهة اتصال."
                                            )
                                        }

                                        val found =
                                            withContext(Dispatchers.IO) {
                                                contactsRepository
                                                    .findUserByWasalCode(
                                                        wasalCode = code,
                                                        accessToken = token
                                                    )
                                            }.getOrElse {
                                                throw it
                                            }

                                        val conversationId =
                                            withContext(Dispatchers.IO) {
                                                contactsRepository
                                                    .createConversation(
                                                        wasalCode = code,
                                                        accessToken = token
                                                    )
                                            }.getOrElse {
                                                throw it
                                            }

                                        contacts = (
                                            contacts.filterNot {
                                                it.id == found.id
                                            } + found
                                        )

                                        val updatedConversations =
                                            refreshConversations(token)

                                        val createdConversation =
                                            updatedConversations.firstOrNull {
                                                it.id == conversationId
                                            }

                                        if (createdConversation != null) {
                                            selectedConversation =
                                                createdConversation
                                            selectedTab = 0
                                        } else {
                                            selectedTab = 0
                                            errorMessage =
                                                "لم يرجع الخادم المحادثة الجديدة ضمن القائمة. تحقق من استجابة الخادم."
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = e.message
                                            ?: "تعذرت إضافة جهة الاتصال."
                                    } finally {
                                        isAddingContact = false
                                    }
                                }
                            }
                        }
                    )
                }

                2 -> {
                    StatusesScreen(
                        authDataStore = authDataStore
                    )
                }

                3 -> {
                    CallsScreen(
                        authDataStore = authDataStore
                    )
                }

                else -> {
                    SettingsScreen(
                        displayName = displayName,
                        wasalCode = wasalCode,
                        bio = bio,
                        statusMessage = if (isLoading) {
                            "جارٍ تحميل بيانات الحساب..."
                        } else {
                            errorMessage
                        },
                        isBusy = isLoading || isSavingDisplayName,
                        darkMode = darkModeState,
                        useSystemTheme = useSystemThemeState,
                        onToggleDarkMode = { enabled ->
                            scope.launch {
                                settingsDataStore.setDarkMode(enabled)
                            }
                        },
                        onToggleUseSystemTheme = { enabled ->
                            scope.launch {
                                settingsDataStore.setUseSystemTheme(enabled)
                            }
                        },
                        onBioSave = { newBio ->
                            scope.launch {
                                if (isSavingBio) {
                                    return@launch
                                }

                                isSavingBio = true
                                errorMessage = null

                                try {
                                    val token = authDataStore
                                        .accessToken
                                        .first()
                                        .orEmpty()

                                    if (token.isBlank()) {
                                        throw IllegalStateException(
                                            "لا توجد جلسة دخول صالحة. تحقق من اتصالك بالإنترنت."
                                        )
                                    }

                                    val result = withContext(Dispatchers.IO) {
                                        profileRepository.updateBio(
                                            bio = newBio,
                                            accessToken = token
                                        )
                                    }

                                    val savedProfile = result.getOrElse {
                                        throw it
                                    }

                                    bio = savedProfile.bio
                                    errorMessage = "تم حفظ النبذة بنجاح."
                                } catch (e: Exception) {
                                    errorMessage = e.message
                                        ?: "تعذر حفظ النبذة."
                                } finally {
                                    isSavingBio = false
                                }
                            }
                        },
                        onLogout = {
                            scope.launch {
                                try {
                                    val refreshToken = authDataStore
                                        .refreshToken
                                        .first()
                                        .orEmpty()

                                    if (refreshToken.isNotBlank()) {
                                        withContext(Dispatchers.IO) {
                                            runCatching {
                                                CoreApiClient.execute(
                                                    CoreApiClient.post(
                                                        "/api/auth/logout",
                                                        JSONObject()
                                                            .put("refreshToken", refreshToken)
                                                            .toString(),
                                                        authDataStore.accessToken
                                                            .first()
                                                            .orEmpty()
                                                            .ifBlank { null }
                                                    )
                                                ).close()
                                            }
                                        }
                                    }
                                } finally {
                                    SocketService.disconnect()
                                    authDataStore.clear()
                                    selectedConversation = null
                                    conversations = emptyList()
                                    contacts = emptyList()
                                    errorMessage =
                                        "تم مسح الجلسة. سيُنشأ حساب جديد عند إعادة تشغيل التطبيق."
                                }
                            }
                        },
                        onSaveDisplayName = { newName ->
                            scope.launch {
                                if (isSavingDisplayName) {
                                    return@launch
                                }

                                isSavingDisplayName = true
                                errorMessage = null

                                try {
                                    val token = authDataStore
                                        .accessToken
                                        .first()
                                        .orEmpty()

                                    if (token.isBlank()) {
                                        throw IllegalStateException(
                                            "لا توجد جلسة دخول صالحة. تحقق من اتصالك بالإنترنت."
                                        )
                                    }

                                    val result = withContext(Dispatchers.IO) {
                                        profileRepository.updateDisplayName(
                                            displayName = newName,
                                            accessToken = token
                                        )
                                    }

                                    val savedProfile = result.getOrElse {
                                        throw it
                                    }

                                    displayName = savedProfile.displayName

                                    if (savedProfile.wasalCode.isNotBlank()) {
                                        wasalCode = savedProfile.wasalCode
                                    }

                                    errorMessage =
                                        "تم حفظ اسم العرض بنجاح."
                                } catch (e: Exception) {
                                    errorMessage = e.message
                                        ?: "تعذر حفظ اسم العرض."
                                } finally {
                                    isSavingDisplayName = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationFlow(
    context: Context,
    authDataStore: AuthDataStore,
    conversation: Conversation,
    onBack: () -> Unit
) {
    val messageRepository = remember(authDataStore) {
        MessageRepository(authDataStore)
    }

    val mediaRepository = remember(context, authDataStore) {
        MediaRepository(context, authDataStore)
    }

    val scope = rememberCoroutineScope()

    var messages by remember(conversation.id) {
        mutableStateOf<List<Message>>(emptyList())
    }

    var isOtherTyping by remember(conversation.id) {
        mutableStateOf(false)
    }

    // الاتصال الفوري: رسائل جديدة ومؤشر الكتابة لكل من يدخل المحادثة
    DisposableEffect(conversation.id, authDataStore) {
        SocketService.init(authDataStore)
        SocketService.connect()

        val messageListener: (JSONObject) -> Unit = { payload ->
            scope.launch {
                val userId = authDataStore.userId.first().orEmpty()
                val incoming = messageRepository.parseLiveMessage(
                    payload,
                    userId
                ) ?: return@launch

                if (incoming.senderCode.isNotBlank() &&
                    !conversation.id.contains(incoming.senderCode)
                ) {
                    return@launch
                }

                val alreadyListed = messages.any { it.id == incoming.id }

                if (!alreadyListed) {
                    messages = messages + incoming
                }
            }
        }

        val typingListener: (JSONObject) -> Unit = { payload ->
            if (payload.optString("conversationId") == conversation.id) {
                isOtherTyping = payload.optBoolean("isTyping", false)

                if (isOtherTyping) {
                    scope.launch {
                        delay(4000)
                        isOtherTyping = false
                    }
                }
            }
        }

        SocketService.on("message", messageListener)
        SocketService.on("typing", typingListener)

        onDispose { }
    }

    var mediaAccessToken by remember(conversation.id) {
        mutableStateOf("")
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var isSending by remember {
        mutableStateOf(false)
    }

    var showAttachmentOptions by remember {
        mutableStateOf(false)
    }

    var statusMessage by remember {
        mutableStateOf<String?>(null)
    }

    val voiceRecorder = remember(conversation.id) {
        VoiceRecorderManager(context)
    }

    var isRecordingVoice by remember(conversation.id) {
        mutableStateOf(false)
    }

    fun startVoiceRecording() {
        if (isRecordingVoice || isSending) return

        val started = voiceRecorder.start()

        if (started) {
            isRecordingVoice = true
        } else {
            statusMessage =
                "تعذر بدء التسجيل الصوتي. تحقق من إذن الميكروفون وحاول مجددًا."
        }
    }

    fun stopVoiceRecordingAndSend() {
        if (!isRecordingVoice) return

        isRecordingVoice = false

        val recordedFile = voiceRecorder.stopAndReturnFile()

        if (recordedFile == null) {
            statusMessage = "التسجيل فارغ أو فشل، ولم يُرسل شيء."
            return
        }

        scope.launch {
            isSending = true
            statusMessage = "جارٍ رفع الرسالة الصوتية..."

            try {
                val token = authDataStore.accessToken
                    .first()
                    .orEmpty()

                mediaAccessToken = token

                val userId = authDataStore.userId
                    .first()
                    .orEmpty()

                if (token.isBlank() || userId.isBlank()) {
                    throw IllegalStateException(
                        "يلزم جلسة دخول لإرسال الرسالة الصوتية."
                    )
                }

                val uploaded = withContext(Dispatchers.IO) {
                    mediaRepository.upload(
                        uri = Uri.fromFile(recordedFile),
                        mimeTypeOverride = "audio/mp4"
                    )
                }

                val media = uploaded.getOrElse { throw it }

                statusMessage = "تم رفع التسجيل، جارٍ إرسال الرسالة..."

                val sent = withContext(Dispatchers.IO) {
                    messageRepository.sendMediaMessage(
                        conversationId = conversation.id,
                        media = media,
                        accessToken = token,
                        currentUserId = userId
                    )
                }

                val message = sent.getOrElse { throw it }

                messages = messages + message
                statusMessage = "تم إرسال الرسالة الصوتية."
            } catch (e: Exception) {
                statusMessage = e.message
                    ?: "تعذر إرسال الرسالة الصوتية."
            } finally {
                isSending = false
                recordedFile.delete()
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceRecording()
        } else {
            statusMessage =
                "رُفض إذن الميكروفون. لا يمكن تسجيل رسالة صوتية دون الإذن."
        }
    }

    fun requestVoiceRecordStart() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            startVoiceRecording()
        } else {
            micPermissionLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    DisposableEffect(conversation.id) {
        onDispose {
            voiceRecorder.cancel()
        }
    }

    fun sendSelectedMedia(uri: Uri?) {
        if (uri == null || isSending) return

        scope.launch {
            isSending = true
            statusMessage = "جارٍ رفع المرفق..."

            try {
                val token = authDataStore.accessToken
                    .first()
                    .orEmpty()

                mediaAccessToken = token

                val userId = authDataStore.userId
                    .first()
                    .orEmpty()

                if (token.isBlank() || userId.isBlank()) {
                    throw IllegalStateException(
                        "يلزم الاتصال بالإنترنت وتوفر جلسة دخول لإرسال المرفق."
                    )
                }

                val uploaded = withContext(Dispatchers.IO) {
                    mediaRepository.upload(uri)
                }

                val media = uploaded.getOrElse {
                    throw it
                }

                statusMessage = "تم رفع الملف، جارٍ إرسال الرسالة..."

                val sent = withContext(Dispatchers.IO) {
                    messageRepository.sendMediaMessage(
                        conversationId = conversation.id,
                        media = media,
                        accessToken = token,
                        currentUserId = userId
                    )
                }

                val message = sent.getOrElse {
                    throw it
                }

                messages = messages + message
                statusMessage = "تم إرسال المرفق."
            } catch (e: Exception) {
                statusMessage = e.message
                    ?: "تعذر إرسال المرفق."
            } finally {
                isSending = false
            }
        }
    }

    val galleryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        sendSelectedMedia(uri)
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        sendSelectedMedia(uri)
    }

    LaunchedEffect(conversation.id) {
        isLoading = true
        statusMessage = null

        try {
            val token = authDataStore.accessToken
                .first()
                .orEmpty()

            mediaAccessToken = token

            val userId = authDataStore.userId
                .first()
                .orEmpty()

            if (userId.isBlank()) {
                throw IllegalStateException(
                    "معرّف المستخدم غير موجود."
                )
            }

            val result = withContext(Dispatchers.IO) {
                messageRepository.getMessages(
                    conversationId = conversation.id,
                    accessToken = token,
                    currentUserId = userId
                )
            }

            messages = result.getOrElse {
                throw it
            }
        } catch (e: Exception) {
            statusMessage = e.message
                ?: "تعذر تحميل الرسائل."
        } finally {
            isLoading = false
        }
    }

    ConversationScreen(
        title = conversation.title,
        messages = messages,
        onBack = onBack,
        isLoading = isLoading,
        isSending = isSending,
        statusMessage = statusMessage,
        mediaAccessToken = mediaAccessToken,
        isOtherTyping = isOtherTyping,
        onDraftChanged = { isTyping ->
            SocketService.emitTyping(conversation.id, isTyping)
        },
        onDeleteMessage = { message, forEveryone ->
            scope.launch {
                try {
                    val token = authDataStore.accessToken
                        .first()
                        .orEmpty()

                    withContext(Dispatchers.IO) {
                        messageRepository.deleteMessage(
                            messageId = message.id,
                            accessToken = token,
                            forEveryone = forEveryone
                        ).getOrElse { throw it }
                    }

                    messages = messages.filterNot { it.id == message.id }
                    statusMessage = if (forEveryone) {
                        "تم حذف الرسالة لدى الجميع."
                    } else {
                        "تم حذف الرسالة لديك."
                    }
                } catch (e: Exception) {
                    statusMessage = e.message
                        ?: "تعذر حذف الرسالة."
                }
            }
        },
        onAttachmentClick = {
            if (!isSending) {
                showAttachmentOptions = true
            }
        },
        onVoiceRecordStart = {
            requestVoiceRecordStart()
        },
        onVoiceRecordStop = {
            stopVoiceRecordingAndSend()
        },
        isRecordingVoice = isRecordingVoice,
        onSendMessage = { text ->
            scope.launch {
                isSending = true
                statusMessage = null
                SocketService.emitTyping(conversation.id, false)

                try {
                    val token = authDataStore.accessToken
                        .first()
                        .orEmpty()

                    mediaAccessToken = token

                    val userId = authDataStore.userId
                        .first()
                        .orEmpty()

                    if (token.isBlank() || userId.isBlank()) {
                        throw IllegalStateException(
                            "يلزم الاتصال بالإنترنت وتوفر جلسة دخول لإرسال الرسائل."
                        )
                    }

                    val result = withContext(Dispatchers.IO) {
                        messageRepository.sendTextMessage(
                            conversationId = conversation.id,
                            content = text,
                            accessToken = token,
                            currentUserId = userId
                        )
                    }

                    val message = result.getOrElse {
                        throw it
                    }

                    messages = messages + message
                } catch (e: Exception) {
                    statusMessage = e.message
                        ?: "تعذر إرسال الرسالة."
                } finally {
                    isSending = false
                }
            }
        }
    )

    if (showAttachmentOptions) {
        AlertDialog(
            onDismissRequest = {
                showAttachmentOptions = false
            },
            containerColor = WasalCard,
            title = {
                Text(
                    text = "إرسال مرفق",
                    color = WasalText,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "اختر مصدر المرفق",
                        color = WasalMuted
                    )

                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showAttachmentOptions = false
                            galleryPicker.launch("image/*")
                        }
                    ) {
                        Text(
                            text = "المعرض — الصور",
                            color = WasalAccent
                        )
                    }

                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showAttachmentOptions = false
                            filePicker.launch(
                                arrayOf(
                                    "image/*",
                                    "video/*",
                                    "audio/*",
                                    "application/pdf",
                                    "application/octet-stream"
                                )
                            )
                        }
                    ) {
                        Text(
                            text = "الملفات — فيديو وصوت ومستندات",
                            color = WasalAccent
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAttachmentOptions = false
                    }
                ) {
                    Text(
                        text = "إلغاء",
                        color = WasalMuted
                    )
                }
            }
        )
    }
}

@Composable
private fun StatusSection(
    title: String,
    message: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(92.dp),
            shape = CircleShape,
            color = WasalCard
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Chat,
                    contentDescription = null,
                    tint = WasalAccent,
                    modifier = Modifier.size(42.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = title,
            color = WasalText,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = message,
            color = WasalMuted,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    WasalCard,
                    RoundedCornerShape(16.dp)
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "وصال — تواصل أقرب",
                color = WasalAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
