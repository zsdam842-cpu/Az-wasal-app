
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.wasal.app.ui.components.WasalAvatar

private val SetBackground = Color(0xFF000E26)
private val SetCard = Color(0xFF001636)
private val SetAccent = Color(0xFF2BA0FF)
private val SetText = Color(0xFFEAF3FF)
private val SetMuted = Color(0xFF8FA9C9)

@Composable
fun SettingsScreen(
    displayName: String = "مستخدم وصال",
    avatarUrl: String? = null,
    isAvatarUploading: Boolean = false,
    onChangeAvatar: () -> Unit = {},
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
    onSaveDisplayName: (String) -> Unit = {},
    onOpenPrivacy: () -> Unit = {}
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
                .verticalScroll(rememberScrollState())
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

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        WasalAvatar(
                            name = displayName,
                            avatarUrl = avatarUrl,
                            size = 76.dp,
                            background = SetAccent.copy(alpha = 0.18f),
                            accent = SetAccent
                        )

                        if (isAvatarUploading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(76.dp),
                                color = SetAccent,
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "الصورة الشخصية",
                            color = SetText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = if (avatarUrl.isNullOrBlank()) {
                                "أضف صورة تظهر في محادثاتك وجهات اتصالك."
                            } else {
                                "يمكنك تغييرها في أي وقت."
                            },
                            color = SetMuted,
                            fontSize = 12.sp
                        )

                        Spacer(Modifier.height(6.dp))

                        OutlinedButton(
                            onClick = onChangeAvatar,
                            enabled = !isAvatarUploading,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = SetAccent,
                                modifier = Modifier.size(18.dp)
                            )

                            Spacer(Modifier.width(8.dp))

                            Text(
                                text = if (isAvatarUploading) {
                                    "جارٍ الرفع..."
                                } else {
                                    "تغيير الصورة"
                                },
                                color = SetAccent,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SetCard, RoundedCornerShape(14.dp))
                    .clickable { onOpenPrivacy() }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = SetAccent,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "معلومات الحساب والخصوصية",
                        color = SetText,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(3.dp))

                    Text(
                        text = "الرمز السري، مسح الجلسة، النسخ الاحتياطي",
                        color = SetMuted,
                        fontSize = 12.sp
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = SetMuted,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.height(18.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SetCard, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "عن التطبيق",
                    color = SetMuted,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "وصال — تطبيق المحادثات",
                    color = SetText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "مطور البرنامج",
                    color = SetMuted,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = "صدام عبده طه أحمد الصنعاني",
                    color = SetAccent,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "الإصدار 1.5",
                    color = SetMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun PrivacySecurityScreen(
    wasalCode: String = "",
    isBusy: Boolean = false,
    onBack: () -> Unit = {},
    onWipeSession: () -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current
    var showWipeConfirm by remember { mutableStateOf(false) }

    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SetBackground)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع",
                        tint = SetText
                    )
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    text = "الخصوصية والحماية",
                    color = SetText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SetCard, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "الرمز السري",
                    color = SetMuted,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = wasalCode.ifBlank { "—" },
                        color = SetText,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            if (wasalCode.isNotBlank()) {
                                clipboard.setText(AnnotatedString(wasalCode))
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "نسخ الرمز السري",
                            tint = SetAccent
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "هذا الرمز هو مفتاح حسابك. لا تشاركه مع أي شخص.",
                    color = SetMuted,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SetCard, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "الجلسة على هذا الجهاز",
                    color = SetMuted,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "مسح الجلسة يحذف الدخول من هذا الهاتف فقط، ويُنشئ حسابًا جديدًا برمز مختلف. احتفظ برمزك الحالي قبل المتابعة.",
                    color = SetText,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(14.dp))

                OutlinedButton(
                    onClick = { showWipeConfirm = true },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (isBusy) {
                            "جارٍ تنفيذ الطلب..."
                        } else {
                            "مسح الجلسة وإنشاء حساب جديد"
                        },
                        color = SetText
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
        }
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text("تأكيد مسح الجلسة") },
            text = {
                Text("سيتم حذف جلستك من هذا الهاتف. تأكد من حفظ رمزك السري قبل المتابعة.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showWipeConfirm = false
                    onWipeSession()
                }) {
                    Text("متابعة", color = SetAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}
