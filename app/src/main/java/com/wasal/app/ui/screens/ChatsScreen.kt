
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wasal.app.data.model.Conversation

private val Background = Color(0xFF0B1020)
private val CardColor = Color(0xFF172342)
private val Accent = Color(0xFF6C8CFF)
private val MainText = Color(0xFFF4F6FF)
private val MutedText = Color(0xFF9AA7C7)

@Composable
fun ChatsScreen(
    conversations: List<Conversation> = emptyList(),
    onConversationClick: (Conversation) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onNewConversation: () -> Unit = {}
) {
    var searchText by remember { mutableStateOf("") }

    val filteredConversations = conversations.filter {
        it.title.contains(searchText, ignoreCase = true) ||
            it.lastMessage.contains(searchText, ignoreCase = true)
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Background)
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "ابحث في المحادثات",
                            color = MutedText
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "بحث",
                            tint = Accent
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MainText,
                        unfocusedTextColor = MainText,
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = CardColor,
                        cursorColor = Accent,
                        focusedContainerColor = CardColor,
                        unfocusedContainerColor = CardColor
                    )
                )

                IconButton(
                    onClick = onNewConversation,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(48.dp)
                        .background(Accent, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "محادثة جديدة",
                        tint = Color.White,
                        modifier = Modifier.size(29.dp)
                    )
                }
            }

            if (filteredConversations.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .background(CardColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = null,
                                tint = Accent,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Spacer(modifier = Modifier.size(18.dp))

                        Text(
                            text = if (searchText.isBlank()) {
                                "لا توجد محادثات حتى الآن"
                            } else {
                                "لا توجد نتائج مطابقة"
                            },
                            color = MainText,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.size(8.dp))

                        Text(
                            text = if (searchText.isBlank()) {
                                "اضغط على ＋ لبدء محادثة جديدة"
                            } else {
                                "جرّب كلمة بحث أخرى"
                            },
                            color = MutedText,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = filteredConversations,
                        key = { it.id }
                    ) { conversation ->
                        ConversationRow(
                            conversation = conversation,
                            onClick = {
                                onConversationClick(conversation)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = CardColor,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(
                    color = Accent.copy(alpha = 0.18f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = conversation.title
                    .trim()
                    .take(1)
                    .ifBlank { "و" },
                color = Accent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = conversation.title,
                color = MainText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.size(5.dp))

            Text(
                text = conversation.lastMessage.ifBlank {
                    "ابدأ المحادثة"
                },
                color = MutedText,
                fontSize = 13.sp,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = conversation.time,
                color = MutedText,
                fontSize = 11.sp
            )

            if (conversation.unreadCount > 0) {
                Spacer(modifier = Modifier.size(7.dp))

                Box(
                    modifier = Modifier
                        .background(Accent, CircleShape)
                        .padding(
                            horizontal = 7.dp,
                            vertical = 3.dp
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = conversation.unreadCount.toString(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
