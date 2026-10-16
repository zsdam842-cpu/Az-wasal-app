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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import com.wasal.app.data.local.AuthDataStore
import com.wasal.app.data.network.CoreApiClient
import com.wasal.app.data.repository.AuthenticatedApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val StatusBackground = Color(0xFF0B1020)
private val StatusCard = Color(0xFF172342)
private val StatusAccent = Color(0xFF6C8CFF)
private val StatusText = Color(0xFFF4F6FF)
private val StatusMuted = Color(0xFF9AA7C7)

data class StatusItem(
    val id: Long,
    val authorName: String,
    val type: String,
    val text: String,
    val isMine: Boolean,
    val viewsCount: Int,
    val viewedByMe: Boolean,
    val createdAt: String
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
    var showAddDialog by remember { mutableStateOf(false) }
    var draftText by remember { mutableStateOf("") }
    var isPublishing by remember { mutableStateOf(false) }

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
                                isMine = item.optBoolean("isMine", false),
                                viewsCount = item.optInt("viewsCount", 0),
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

    fun publishStatus() {
        val text = draftText.trim()

        if (text.isEmpty() || isPublishing) return

        scope.launch {
            isPublishing = true

            try {
                val payload = JSONObject()
                    .put("type", "text")
                    .put("text", text)
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
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            StatusCard,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
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

                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = item.authorName +
                                                if (item.isMine) " (أنت)" else "",
                                            color = StatusText,
                                            fontWeight = FontWeight.Medium
                                        )

                                        Text(
                                            text = item.text,
                                            color = StatusMuted,
                                            fontSize = 13.sp,
                                            maxLines = 2
                                        )

                                        Text(
                                            text = item.createdAt +
                                                if (item.isMine) {
                                                    " • ${item.viewsCount} مشاهدة"
                                                } else {
                                                    ""
                                                },
                                            color = StatusMuted,
                                            fontSize = 11.sp
                                        )
                                    }

                                    if (item.isMine) {
                                        IconButton(
                                            onClick = { deleteStatus(item.id) }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "حذف الحالة",
                                                tint = StatusMuted
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
                    OutlinedTextField(
                        value = draftText,
                        onValueChange = { if (it.length <= 200) draftText = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isPublishing,
                        placeholder = {
                            Text("اكتب حالتك...", color = StatusMuted)
                        }
                    )
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
    }
}
