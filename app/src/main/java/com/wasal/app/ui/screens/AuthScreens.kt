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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wasal.app.data.repository.AuthRepository
import kotlinx.coroutines.launch

private val AuthBg = Color(0xFF00112E)
private val AuthCard = Color(0xFF0A2444)
private val AuthAccent = Color(0xFF2BA0FF)
private val AuthText = Color(0xFFEAF1FA)
private val AuthMuted = Color(0xFF9FB2CC)

/**
 * أول ما يُفتح التطبيق بلا جلسة: خياران فقط.
 * ١) الدخول بالرمز السري (نقل الحساب من هاتف آخر).
 * ٢) تسجيل دخول جديد برمز يُنشأ تلقائياً.
 */
@Composable
fun WelcomeScreen(
    onSecretLogin: () -> Unit,
    onNewAccount: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuthBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(AuthCard, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = AuthAccent,
                    modifier = Modifier.size(46.dp)
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = "وصال",
                color = AuthText,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "اختر طريقة الدخول",
                color = AuthMuted,
                fontSize = 15.sp
            )

            Spacer(Modifier.height(28.dp))

            AuthOption(
                title = "الدخول بالرمز السري",
                subtitle = "لحسابك الموجود على هاتف آخر — يصلك رمز تأكيد داخل حسابك القديم",
                icon = Icons.Default.PhoneAndroid,
                onClick = onSecretLogin
            )

            Spacer(Modifier.height(12.dp))

            AuthOption(
                title = "تسجيل دخول جديد",
                subtitle = "يُنشأ لك رمز وصال تلقائياً وتبدأ مباشرة",
                icon = Icons.Default.Person,
                onClick = onNewAccount
            )

            Spacer(Modifier.height(26.dp))

            Text(
                text = "تغيير الرمز السري متاح مرتين فقط لكل هاتف.",
                color = AuthMuted,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun AuthOption(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AuthCard, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(AuthBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = AuthAccent)
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = AuthText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(3.dp))

            Text(
                text = subtitle,
                color = AuthMuted,
                fontSize = 12.sp
            )
        }
    }
}

/**
 * الدخول من هاتف آخر: رمز وصال + الرمز السري، ثم رمز التأكيد
 * الذي يظهر داخل الحساب القديم.
 */
@Composable
fun SecretLoginScreen(
    authRepository: AuthRepository,
    deviceId: String,
    deviceName: String,
    onSuccess: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(1) }
    var code by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var verification by remember { mutableStateOf("") }
    var requestId by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var hasBackup by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuthBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "رجوع",
                    tint = AuthText
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = if (step == 1) "الدخول بالرمز السري" else "رمز التأكيد",
                color = AuthText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (step == 1) {
                    "أدخل رمز وصال والرمز السري لحسابك."
                } else {
                    "أرسلنا رمزاً من ٦ أرقام إلى حسابك القديم داخل التطبيق. " +
                        "افتح الهاتف القديم وانسخ الرمز هنا."
                },
                color = AuthMuted,
                fontSize = 13.sp
            )

            Spacer(Modifier.height(20.dp))

            if (step == 1) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 15) code = it.trim() },
                    label = { Text("رمز وصال (١٥ خانة)") },
                    singleLine = true,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = secret,
                    onValueChange = { if (it.length <= 64) secret = it },
                    label = { Text("الرمز السري") },
                    singleLine = true,
                    enabled = !isBusy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = {
                        if (code.length < 15 || secret.length < 6) {
                            message = "أدخل رمز وصال كاملاً والرمز السري."
                            return@Button
                        }

                        isBusy = true
                        message = null

                        scope.launch {
                            val result = authRepository.requestSecretLogin(
                                code = code,
                                secret = secret,
                                deviceId = deviceId,
                                deviceName = deviceName
                            )

                            isBusy = false

                            result.onSuccess {
                                requestId = it
                                step = 2
                            }.onFailure {
                                message = it.message
                            }
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuthAccent
                    )
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text("إرسال رمز التأكيد")
                    }
                }
            } else {
                OutlinedTextField(
                    value = verification,
                    onValueChange = {
                        if (it.length <= 6 && it.all { ch -> ch.isDigit() }) {
                            verification = it
                        }
                    },
                    label = { Text("رمز التأكيد (٦ أرقام)") },
                    singleLine = true,
                    enabled = !isBusy,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = {
                        isBusy = true
                        message = null

                        scope.launch {
                            val result = authRepository.verifySecretLogin(
                                requestId = requestId,
                                verificationCode = verification,
                                deviceId = deviceId
                            )

                            isBusy = false

                            result.onSuccess { hasBackupValue ->
                                hasBackup = hasBackupValue

                                if (hasBackup) {
                                    message = "تم الدخول. توجد نسخة احتياطية، " +
                                        "وسيتم استرجاع محادثاتك."
                                } else {
                                    message = "تم الدخول إلى حسابك."
                                }

                                onSuccess()
                            }.onFailure {
                                message = it.message
                            }
                        }
                    },
                    enabled = !isBusy && verification.length == 6,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuthAccent
                    )
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text("تأكيد الدخول")
                    }
                }

                Spacer(Modifier.height(10.dp))

                TextButton(
                    onClick = {
                        step = 1
                        verification = ""
                        message = null
                    },
                    enabled = !isBusy
                ) {
                    Text("الرجوع لإدخال البيانات", color = AuthMuted)
                }
            }

            message?.let {
                Spacer(Modifier.height(16.dp))

                Text(
                    text = it,
                    color = if (hasBackup) AuthAccent else AuthText,
                    fontSize = 13.sp
                )
            }
        }
    }
}
