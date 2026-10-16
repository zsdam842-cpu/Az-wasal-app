
package com.wasal.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SetBackground = Color(0xFF0B1020)
private val SetCard = Color(0xFF172342)
private val SetAccent = Color(0xFF6C8CFF)
private val SetText = Color(0xFFF4F6FF)
private val SetMuted = Color(0xFF9AA7C7)

@Composable
fun SettingsScreen(
    displayName: String = "مستخدم وصال",
    wasalCode: String = "",
    bio: String = "",
    statusMessage: String? = null,
    isBusy: Boolean = false,
    darkMode: Boolean = true,
    useSystemTheme: Boolean = false,
    onToggleDarkMode: (Boolean) -> Unit = {},
    onToggleUseSystemTheme: (Boolean) -> Unit = {},
    onLogout: () -> Unit = {},
    onBioSave: (String) -> Unit = {},
    onSaveDisplayName: (String) -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current

    var editedName by remember {
        mutableStateOf(displayName)
    }

    LaunchedEffect(displayName) {
        editedName = displayName
    }

    var editedBio by remember {
        mutableStateOf(bio)
    }

    LaunchedEffect(bio) {
        editedBio = bio
    }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SetBackground)
                .padding(16.dp)
        ) {
            Text(
                text = "الإعدادات",
                color = SetText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        SetCard,
                        RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = "الحساب",
                    color = SetMuted,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = editedName,
                    onValueChange = {
                        if (it.length <= 40) {
                            editedName = it
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy,
                    singleLine = true,
                    label = {
                        Text("اسم العرض", color = SetMuted)
                    },
                    placeholder = {
                        Text("اكتب اسمك", color = SetMuted)
                    }
                )

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        onSaveDisplayName(editedName.trim())
                    },
                    enabled = !isBusy &&
                        editedName.trim().isNotBlank() &&
                        editedName.trim() != displayName.trim(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SetAccent,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (isBusy) {
                            "جارٍ الحفظ..."
                        } else {
                            "حفظ اسم العرض"
                        }
                    )
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "نبذة تعريفية",
                    color = SetMuted,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = editedBio,
                    onValueChange = {
                        if (it.length <= 200) {
                            editedBio = it
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy,
                    label = {
                        Text("اكتب نبذة قصيرة عنك", color = SetMuted)
                    }
                )

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        onBioSave(editedBio.trim())
                    },
                    enabled = !isBusy && editedBio.trim() != bio.trim(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SetAccent,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = if (isBusy) {
                            "جارٍ الحفظ..."
                        } else {
                            "حفظ النبذة"
                        }
                    )
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "رمز وصال الخاص بك",
                    color = SetMuted,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = wasalCode.ifBlank {
                            "جارٍ تحميل الرمز..."
                        },
                        color = SetAccent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (wasalCode.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))

                        IconButton(
                            onClick = {
                                clipboard.setText(
                                    AnnotatedString(wasalCode)
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "نسخ رمز وصال",
                                tint = SetAccent
                            )
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "شارك هذا الرمز مع الشخص الذي تريد بدء محادثة معه.",
                    color = SetMuted,
                    fontSize = 12.sp
                )
            }

            if (!statusMessage.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))

                Text(
                    text = statusMessage,
                    color = SetMuted,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(18.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        SetCard,
                        RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = "المظهر",
                    color = SetMuted,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الوضع الداكن",
                        color = SetText,
                        modifier = Modifier.weight(1f)
                    )

                    Switch(
                        checked = darkMode,
                        onCheckedChange = onToggleDarkMode,
                        enabled = !useSystemTheme
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "اعتماد إعداد النظام تلقائيًا",
                        color = SetText,
                        modifier = Modifier.weight(1f)
                    )

                    Switch(
                        checked = useSystemTheme,
                        onCheckedChange = onToggleUseSystemTheme
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = onLogout,
                enabled = !isBusy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SetCard,
                    contentColor = SetText
                )
            ) {
                Text(
                    text = if (isBusy) {
                        "جارٍ تنفيذ الطلب..."
                    } else {
                        "مسح الجلسة وإنشاء حساب جديد"
                    }
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = "تنبيه: إنشاء حساب جديد قد يمنحك رمزًا مختلفًا عن رمزك الحالي.",
                color = SetMuted,
                fontSize = 12.sp
            )
        }
    }
}
