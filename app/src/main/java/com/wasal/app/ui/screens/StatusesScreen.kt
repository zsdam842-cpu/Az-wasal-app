package com.wasal.app.ui.screens

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wasal.app.data.local.AuthDataStore
import com.wasal.app.data.network.CoreApiClient
import com.wasal.app.data.repository.AuthenticatedApiClient
import com.wasal.app.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val StatusBackground = Color(0xFF000E26)
private val StatusCard = Color(0xFF001636)
private val StatusAccent = Color(0xFF2BA0FF)
private val StatusText = Color(0xFFEAF3FF)
private val StatusMuted = Color(0xFF8FA9C9)

data class StatusItem(
    val id: Long,
    val authorName: String,
    val type: String,
    val text: String,
    val mediaUrl: String = "",
    val isMine: Boolean = false,
    val viewsCount: Int = 0,
    val likedByMe: Boolean = false,
    val likesCount: Int = 0,
    val audience: String = "contacts",
    val viewedByMe: Boolean = false,
    val createdAt: String = ""
)

@Composable
fun StatusesScreen(
    authDataStore: AuthDataStore
) {
    val apiClient = remember { AuthenticatedApiClient(authDataStore) }
    val scope = rememberCoroutineScope()

    var statuses by remember { mutableStateOf<List<StatusItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // يُحفظ عبر إعادة إنشاء الشاشة حتى لا تُغلق نافذة الإضافة بعد
    // اختيار صورة أو فيديو من المعرض.
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var draftText by rememberSaveable { mutableStateOf("") }
    var isPublishing by remember { mutableStateOf(false) }
    var audience by rememberSaveable { mutableStateOf("contacts") }
    var viewerStatusId by remember { mutableStateOf<Long?>(null) }
    var viewerNames by remember {
        mutableStateOf<List<String>>(emptyList())
    }
    var likerNames by remember {
        mutableStateOf<List<String>>(emptyList())
    }
    var openImageUrl by remember { mutableStateOf<String?>(null) }
    var openVideoUrl by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val mediaRepository = remember(context, authDataStore) {
        MediaRepository(context, authDataStore)
    }

    fun loadStatuses() {
        scope.launch {
            isLoading = true
            errorMessage = null

            try {
                val result = withContext(Dispatchers.IO) {
                    apiClient.execute { token ->
                        CoreApiClient.get("/api/statuses", token)
                    }
                }

                val response = result.getOrElse { throw it }

                if (!response.isSuccessful) {
                    throw Exception(
                        "تعذر تحميل الحالات. رمز الاستجابة: ${response.code}"
                    )
                }

                val root = JSONObject(response.body)
                val array = root.optJSONArray("statuses")
                val loaded = mutableListOf<StatusItem>()

                if (array != null) {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue

                        loaded.add(
                            StatusItem(
                                id = item.optLong("id", -1),
                                authorName = item.optString("authorName")
                                    .ifBlank { "مستخدم وصال" },
                                type = item.optString("type", "text"),
                                text = item.optString("text", ""),
                                mediaUrl = item.optString("mediaUrl", ""),
                                isMine = item.optBoolean("isMine", false),
                                viewsCount = item.optInt("viewsCount", 0),
                                likedByMe = item.optBoolean("likedByMe", false),
                                likesCount = item.optInt("likesCount", 0),
                                audience = item.optString("audience", "contacts"),
                                viewedByMe = item.optBoolean("viewedByMe", false),
                                createdAt = item.optString("createdAt", "")
                                    .take(10)
                            )
                        )
                    }
                }

                statuses = loaded
            } catch (e: Exception) {
                errorMessage = e.message ?: "تعذر تحميل الحالات."
            } finally {
                isLoading = false
            }
        }
    }

    fun publishMediaStatus(uri: android.net.Uri, isVideo: Boolean) {
        if (isPublishing) return
        scope.launch {
            isPublishing = true
            try {
                val uploaded = withContext(Dispatchers.IO) {
                    mediaRepository.upload(uri).getOrElse { throw it }
                }

                val payload = JSONObject()
                    .put("type", if (isVideo) "video" else "image")
                    .put("mediaUrl", uploaded.url)
                    .put("text", draftText.trim())
                    .put("audience", audience)
                    .toString()

                val result = withContext(Dispatchers.IO) {
                    apiClient.execute { token ->
                        CoreApiClient.post("/api/statuses", payload, token)
                    }
                }

                val response = result.getOrElse { throw it }
                if (!response.isSuccessful) {
                    throw Exception("تعذر نشر الحالة. رمز الاستجابة: ${response.code}")
                }

                draftText = ""
                showAddDialog = false
                loadStatuses()
            } catch (e: Exception) {
                errorMessage = e.message ?: "تعذر نشر الحالة."
            } finally {
                isPublishing = false
            }
        }
    }

    val statusMediaPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val isVideo = context.contentResolver.getType(uri)?.startsWith("video/") == true
            publishMediaStatus(uri, isVideo)
        }
    }


    fun publishStatus() {
        val text = draftText.trim()

        if (text.isEmpty() || isPublishing) return

        scope.launch {
            isPublishing = true

            try {
                val payload = JSONObject()
                    .put("type", "text")
                    .put("text", text)
                    .put("audience", audience)
                    .toString()

                val result = withContext(Dispatchers.IO) {
                    apiClient.execute { token ->
                        CoreApiClient.post("/api/statuses", payload, token)
                    }
                }

                val response = result.getOrElse { throw it }

                if (!response.isSuccessful) {
                    throw Exception(
                        "تعذر نشر الحالة. رمز الاستجابة: ${response.code}"
                    )
                }

                draftText = ""
                showAddDialog = false
                loadStatuses()
            } catch (e: Exception) {
                errorMessage = e.message ?: "تعذر نشر الحالة."
            } finally {
                isPublishing = false
            }
        }
    }

    fun deleteStatus(statusId: Long) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    apiClient.execute { token ->
                        CoreApiClient.delete("/api/statuses/$statusId", token)
                    }
                }

                statuses = statuses.filterNot { it.id == statusId }
            } catch (e: Exception) {
                errorMessage = e.message ?: "تعذر حذف الحالة."
            }
        }
    }

    fun toggleLike(item: StatusItem) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    apiClient.execute { token ->
                        if (item.likedByMe) {
                            CoreApiClient.delete("/api/statuses/${item.id}/like", token)
                        } else {
                            CoreApiClient.post(
                                "/api/statuses/${item.id}/like",
                                "{}",
                                token
                            )
                        }
                    }
                }

                statuses = statuses.map {
                    if (it.id != item.id) {
                        it
                    } else if (it.likedByMe) {
                        it.copy(likedByMe = false, likesCount = (it.likesCount - 1).coerceAtLeast(0))
                    } else {
                        it.copy(likedByMe = true, likesCount = it.likesCount + 1)
                    }
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "تعذر تنفيذ الإعجاب."
            }
        }
    }

    fun loadViewers(statusId: Long) {
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    apiClient.execute { token ->
                        CoreApiClient.get("/api/statuses/$statusId/viewers", token)
                    }
                }

                val response = result.getOrElse { throw it }

                if (!response.isSuccessful) {
                    throw Exception("تعذر تحميل المشاهدين.")
                }

                val root = JSONObject(response.body)

                fun names(arrayKey: String): List<String> {
                    val array = root.optJSONArray(arrayKey) ?: return emptyList()

                    return (0 until array.length()).mapNotNull { index ->
                        val item = array.optJSONObject(index) ?: return@mapNotNull null
                        item.optString("name").ifBlank { "مستخدم وصال" }
                    }
                }

                viewerNames = names("viewers")
                likerNames = names("likes")
            } catch (e: Exception) {
                errorMessage = e.message ?: "تعذر تحميل المشاهدين."
            }
        }
    }

    LaunchedEffect(Unit) {
        loadStatuses()
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        Scaffold(
            containerColor = StatusBackground,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = StatusAccent,
                    contentColor = StatusText
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "نشر حالة جديدة"
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                Text(
                    text = "الحالات",
                    color = StatusText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "تظهر الحالات 24 ساعة من نشرها",
                    color = StatusMuted,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(12.dp))

                when {
                    isLoading && statuses.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = StatusAccent)
                        }
                    }

                    statuses.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = errorMessage
                                    ?: "لا توجد حالات بعد.\nاضغط زر الإضافة لنشر حالتك.",
                                color = StatusMuted,
                                fontSize = 14.sp
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = statuses,
                                key = { it.id }
                            ) { item ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            StatusCard,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(
                                                    StatusAccent.copy(alpha = 0.25f),
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = item.authorName
                                                    .trim()
                                                    .take(1)
                                                    .ifBlank { "و" },
                                                color = StatusAccent,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = item.authorName +
                                                    if (item.isMine) " (أنت)" else "",
                                                color = StatusText,
                                                fontWeight = FontWeight.Medium
                                            )

                                            Text(
                                                text = buildString {
                                                    append(item.createdAt)

                                                    if (item.isMine) {
                                                        append(" • ")
                                                        append(item.viewsCount)
                                                        append(" مشاهدة")
                                                    }

                                                    if (item.likesCount > 0) {
                                                        append(" • ")
                                                        append(item.likesCount)
                                                        append(" إعجاب")
                                                    }
                                                },
                                                color = StatusMuted,
                                                fontSize = 11.sp
                                            )
                                        }

                                        if (item.isMine) {
                                            IconButton(
                                                onClick = {
                                                    viewerNames = emptyList()
                                                    likerNames = emptyList()
                                                    viewerStatusId = item.id
                                                    loadViewers(item.id)
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Default.Visibility,
                                                    contentDescription = "من شاهد الحالة",
                                                    tint = StatusAccent
                                                )
                                            }

                                            IconButton(
                                                onClick = { deleteStatus(item.id) }
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "حذف الحالة",
                                                    tint = StatusMuted
                                                )
                                            }
                                        } else {
                                            IconButton(
                                                onClick = { toggleLike(item) }
                                            ) {
                                                Icon(
                                                    if (item.likedByMe) {
                                                        Icons.Default.Favorite
                                                    } else {
                                                        Icons.Default.FavoriteBorder
                                                    },
                                                    contentDescription = "إعجاب",
                                                    tint = if (item.likedByMe) {
                                                        StatusAccent
                                                    } else {
                                                        StatusMuted
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    if (item.text.isNotBlank()) {
                                        Spacer(Modifier.height(8.dp))

                                        Text(
                                            text = item.text,
                                            color = StatusText,
                                            fontSize = 14.sp
                                        )
                                    }

                                    if (item.mediaUrl.isNotBlank()) {
                                        Spacer(Modifier.height(8.dp))

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 160.dp, max = 260.dp)
                                                .background(
                                                    StatusBackground,
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .clickable {
                                                    if (item.type == "video") {
                                                        openVideoUrl = item.mediaUrl
                                                    } else {
                                                        openImageUrl = item.mediaUrl
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (item.type == "video") {
                                                Icon(
                                                    Icons.Default.Visibility,
                                                    contentDescription = null,
                                                    tint = StatusMuted
                                                )

                                                Text(
                                                    text = "فيديو — اضغط للمشاهدة",
                                                    color = StatusMuted,
                                                    fontSize = 12.sp
                                                )
                                            } else {
                                                AsyncImage(
                                                    model = item.mediaUrl,
                                                    contentDescription = "صورة الحالة",
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                containerColor = StatusCard,
                title = {
                    Text(
                        text = "حالة جديدة",
                        color = StatusText,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        OutlinedTextField(
                            value = draftText,
                            onValueChange = { if (it.length <= 200) draftText = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isPublishing,
                            placeholder = {
                                Text("اكتب حالتك...", color = StatusMuted)
                            }
                        )
                        Spacer(Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    statusMediaPicker.launch("image/*")
                                },
                                enabled = !isPublishing
                            ) { Text("صورة", color = StatusAccent) }

                            TextButton(
                                onClick = {
                                    statusMediaPicker.launch("video/*")
                                },
                                enabled = !isPublishing
                            ) { Text("فيديو", color = StatusAccent) }
                        }

                        Spacer(Modifier.height(6.dp))

                        Text(
                            text = "من يمكنهم رؤية الحالة",
                            color = StatusMuted,
                            fontSize = 12.sp
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                "everyone" to "الجميع",
                                "contacts" to "جهات الاتصال"
                            ).forEach { (value, label) ->
                                TextButton(
                                    onClick = { audience = value },
                                    enabled = !isPublishing
                                ) {
                                    Text(
                                        text = if (audience == value) "● $label" else label,
                                        color = if (audience == value) StatusAccent else StatusMuted
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { publishStatus() },
                        enabled = draftText.isNotBlank() && !isPublishing
                    ) {
                        Text("نشر", color = StatusAccent)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showAddDialog = false }
                    ) {
                        Text("إلغاء", color = StatusMuted)
                    }
                }
            )
        }

        // قائمة من شاهد الحالة ومن أعجب بها (لصاحب الحالة)
        viewerStatusId?.let { statusId ->
            AlertDialog(
                onDismissRequest = { viewerStatusId = null },
                containerColor = StatusCard,
                title = { Text("مشاهدو الحالة", color = StatusText) },
                text = {
                    Column {
                        Text(
                            text = "شاهدها ${viewerNames.size} — أعجب بها ${likerNames.size}",
                            color = StatusMuted,
                            fontSize = 12.sp
                        )

                        Spacer(Modifier.height(8.dp))

                        if (viewerNames.isEmpty()) {
                            Text(
                                text = "لا أحد شاهدها بعد.",
                                color = StatusMuted,
                                fontSize = 13.sp
                            )
                        } else {
                            viewerNames.take(12).forEach { name ->
                                Text(text = name, color = StatusText, fontSize = 13.sp)
                            }
                        }

                        if (likerNames.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(text = "أعجبوا بها", color = StatusMuted, fontSize = 12.sp)
                            likerNames.take(12).forEach { name ->
                                Text(text = "♥ $name", color = StatusAccent, fontSize = 13.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewerStatusId = null }) {
                        Text("إغلاق", color = StatusAccent)
                    }
                }
            )
        }

        openImageUrl?.let { url ->
            com.wasal.app.ui.components.FullScreenImageDialog(
                url = url,
                accessToken = "",
                onClose = { openImageUrl = null }
            )
        }

        openVideoUrl?.let { url ->
            com.wasal.app.ui.components.FullScreenVideoDialog(
                url = url,
                accessToken = "",
                onClose = { openVideoUrl = null }
            )
        }
    }
}
