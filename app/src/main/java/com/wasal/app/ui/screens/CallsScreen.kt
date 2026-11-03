
package com.wasal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import com.wasal.app.data.network.MediaCallsApiClient
import com.wasal.app.data.network.ServerConfig
import com.wasal.app.data.repository.AuthenticatedApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val CallsBackground = Color(0xFF000E26)
private val CallsCard = Color(0xFF001636)
private val CallsAccent = Color(0xFF2BA0FF)
private val CallsText = Color(0xFFEAF3FF)
private val CallsMuted = Color(0xFF8FA9C9)

data class CallLogItem(
    val id: String,
    val callerName: String,
    val kind: String,
    val direction: String,
    val createdAt: String,
    val peerId: String = "",
    val peerCode: String = "",
    val durationSeconds: Int = 0
)

@Composable
fun CallsScreen(
    authDataStore: AuthDataStore,
    onRedial: (CallLogItem) -> Unit = {}
) {
    val apiClient = remember { AuthenticatedApiClient(authDataStore) }
    val scope = rememberCoroutineScope()

    var calls by remember { mutableStateOf<List<CallLogItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadCalls() {
        scope.launch {
            isLoading = true
            errorMessage = null

            try {
                // سجل المكالمات يعيش في قاعدة بيانات خادم الوسائط والمكالمات،
                // وطلبه من خادم الرسائل كان يُرجع قائمة فارغة دائمًا.
                val result = withContext(Dispatchers.IO) {
                    apiClient.execute { token ->
                        MediaCallsApiClient.get(
                            ServerConfig.CALLS_ENDPOINT,
                            token
                        )
                    }
                }

                val response = result.getOrElse { throw it }

                if (!response.isSuccessful) {
                    throw Exception(
                        "تعذر تحميل سجل المكالمات. رمز الاستجابة: ${response.code}"
                    )
                }

                val root = JSONObject(response.body)
                val array = root.optJSONArray("calls")
                val loaded = mutableListOf<CallLogItem>()

                if (array != null) {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue

                        loaded.add(
                            CallLogItem(
                                id = item.optString("id").ifBlank { index.toString() },
                                callerName = item.optString("callerName")
                                    .ifBlank { item.optString("name") }
                                    .ifBlank { item.optString("peerName") }
                                    .ifBlank { "مستخدم وصال" },
                                kind = item.optString("kind", "audio"),
                                direction = item.optString("direction", "outgoing"),
                                createdAt = item.optString("createdAt"),
                                peerId = item.optString("peerId"),
                                peerCode = item.optString("peerCode"),
                                durationSeconds = item.optInt("durationSeconds", 0)
                            )
                        )
                    }
                }

                calls = loaded
            } catch (e: Exception) {
                errorMessage = e.message
                    ?: "حدث خطأ أثناء تحميل سجل المكالمات."
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(authDataStore) {
        loadCalls()
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(CallsBackground)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "سجل المكالمات",
                    color = CallsText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 12.dp, bottom = 8.dp)
                )

                Text(
                    text = "تحديث",
                    color = CallsAccent,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable { loadCalls() }
                        .padding(8.dp)
                )
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = CallsAccent)
                    }
                }

                errorMessage != null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = errorMessage ?: "",
                                color = CallsMuted,
                                fontSize = 14.sp
                            )

                            Spacer(Modifier.height(14.dp))

                            Button(
                                onClick = { loadCalls() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CallsAccent,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(text = "إعادة المحاولة")
                            }
                        }
                    }
                }

                calls.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .background(CallsCard, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = null,
                                    tint = CallsAccent,
                                    modifier = Modifier.size(38.dp)
                                )
                            }

                            Spacer(Modifier.height(18.dp))

                            Text(
                                text = "لا توجد مكالمات بعد",
                                color = CallsText,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(8.dp))

                            Text(
                                text = "ستظهر هنا المكالمات الصوتية والمرئية بعد إجرائها.",
                                color = CallsMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(calls) { call ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CallsCard, RoundedCornerShape(14.dp))
                                    .clickable { onRedial(call) }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (call.kind == "video") {
                                        Icons.Default.Videocam
                                    } else {
                                        Icons.Default.Call
                                    },
                                    contentDescription = null,
                                    tint = CallsAccent,
                                    modifier = Modifier.size(24.dp)
                                )

                                Spacer(Modifier.size(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = call.callerName,
                                        color = CallsText,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Text(
                                        text = buildString {
                                            append(
                                                when (call.direction) {
                                                    "incoming" -> "مكالمة واردة"
                                                    "missed" -> "مكالمة فائتة"
                                                    else -> "مكالمة صادرة"
                                                }
                                            )

                                            if (call.durationSeconds > 0) {
                                                append(" · ")
                                                append(call.durationSeconds / 60)
                                                append(":")
                                                append(
                                                    (call.durationSeconds % 60)
                                                        .toString()

                                                        .padStart(2, '0')
                                                )
                                            }
                                        },
                                        color = CallsMuted,
                                        fontSize = 12.sp
                                    )
                                }

                                Text(
                                    text = call.createdAt,
                                    color = CallsMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
