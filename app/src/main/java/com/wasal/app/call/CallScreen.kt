package com.wasal.app.call

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.SurfaceViewRenderer

private val CallBg = Color(0xFF000E26)
private val CallPanel = Color(0xCC001C3D)
private val CallAccent = Color(0xFF2BA0FF)
private val CallDanger = Color(0xFFE23B4E)
private val CallText = Color(0xFFEAF3FF)
private val CallMuted = Color(0xFF8FA9C9)

/**
 * شاشة المكالمة الكاملة: واردة، صادرة، ونشطة مع فيديو حقيقي.
 */
@Composable
fun CallScreen(
    state: CallUiState,
    manager: CallManager,
    onDismiss: () -> Unit
) {
    var elapsedSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.startedAtMillis, state.phase) {
        if (state.phase != CallPhase.ACTIVE) {
            elapsedSeconds = 0
            return@LaunchedEffect
        }

        while (true) {
            elapsedSeconds = (
                (System.currentTimeMillis() - state.startedAtMillis) / 1000
                ).toInt().coerceAtLeast(0)

            kotlinx.coroutines.delay(1000)
        }
    }

    val showVideo = state.kind == CallKind.VIDEO &&
        state.phase != CallPhase.INCOMING &&
        state.phase != CallPhase.ENDED &&
        state.phase != CallPhase.FAILED

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CallBg)
    ) {
        if (showVideo) {
            RemoteVideo(
                manager = manager,
                visible = state.connected
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(CallPanel, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (state.kind == CallKind.VIDEO) {
                        Icons.Default.Videocam
                    } else {
                        Icons.Default.Call
                    },
                    contentDescription = null,
                    tint = CallAccent,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = state.peerName.ifBlank { "مستخدم وصال" },
                color = CallText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = when (state.phase) {
                    CallPhase.INCOMING -> "مكالمة واردة"
                    CallPhase.OUTGOING -> "جارٍ الرنين..."
                    CallPhase.CONNECTING -> "جارٍ الاتصال..."
                    CallPhase.ACTIVE -> formatDuration(elapsedSeconds)
                    CallPhase.ENDED, CallPhase.FAILED ->
                        state.message ?: "انتهت المكالمة"
                    CallPhase.IDLE -> ""
                },
                color = CallMuted,
                fontSize = 15.sp
            )

            state.message
                ?.takeIf {
                    state.phase == CallPhase.OUTGOING ||
                        state.phase == CallPhase.CONNECTING
                }
                ?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(text = it, color = CallMuted, fontSize = 13.sp)
                }

            Spacer(Modifier.weight(1f))

            when (state.phase) {
                CallPhase.INCOMING -> IncomingControls(
                    onAccept = { manager.acceptIncoming() },
                    onReject = { manager.rejectCall() }
                )

                CallPhase.ENDED, CallPhase.FAILED -> {
                    CallAction(
                        icon = Icons.Default.Call,
                        label = "إغلاق",
                        background = CallAccent,
                        onClick = {
                            manager.dismissEndedState()
                            onDismiss()
                        }
                    )
                }

                CallPhase.IDLE -> Unit

                else -> ActiveControls(
                    state = state,
                    onToggleMic = { manager.toggleMicrophone() },
                    onToggleCamera = { manager.toggleCamera() },
                    onSwitchCamera = { manager.switchCamera() },
                    onHangUp = { manager.hangUp() }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // صورة الكاميرا الخاصة بي: معاينة صغيرة أسفل الشاشة فوق شاشة الطرف الآخر.
    if (state.kind == CallKind.VIDEO && showVideo) {
        LocalPreview(
            manager = manager,
            visible = state.cameraEnabled,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 150.dp)
        )
    }

    // إغلاق الشاشة تلقائيًا بعد انتهاء المكالمة.
    LaunchedEffect(state.phase) {
        if (state.phase == CallPhase.IDLE) {
            onDismiss()
        }
    }
}

@Composable
private fun RemoteVideo(
    manager: CallManager,
    visible: Boolean
) {
    if (!visible) return

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            SurfaceViewRenderer(context).also {
                manager.attachRemoteRenderer(it)
            }
        },
        onRelease = {
            manager.releaseRemoteRenderer()
        }
    )
}

@Composable
private fun LocalPreview(
    manager: CallManager,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    Box(
        modifier = modifier
            .width(120.dp)
            .height(170.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CallPanel)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                SurfaceViewRenderer(context).also {
                    manager.attachLocalRenderer(it)
                }
            },
            onRelease = {
                manager.releaseLocalRenderer()
            }
        )
    }
}

@Composable
private fun IncomingControls(
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        CallAction(
            icon = Icons.Default.CallEnd,
            label = "رفض",
            background = CallDanger,
            onClick = onReject
        )

        CallAction(
            icon = Icons.Default.Call,
            label = "قبول",
            background = Color(0xFF1FA35A),
            onClick = onAccept
        )
    }
}

@Composable
private fun ActiveControls(
    state: CallUiState,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
    onHangUp: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CallAction(
            icon = if (state.microphoneEnabled) Icons.Default.Mic else Icons.Default.MicOff,
            label = if (state.microphoneEnabled) "كتم" else "تشغيل",
            background = CallPanel,
            onClick = onToggleMic
        )

        if (state.kind == CallKind.VIDEO) {
            CallAction(
                icon = if (state.cameraEnabled) {
                    Icons.Default.Videocam
                } else {
                    Icons.Default.VideocamOff
                },
                label = "الكاميرا",
                background = CallPanel,
                onClick = onToggleCamera
            )

            CallAction(
                icon = Icons.Default.Cameraswitch,
                label = "تبديل",
                background = CallPanel,
                onClick = onSwitchCamera
            )
        }

        CallAction(
            icon = Icons.Default.CallEnd,
            label = "إنهاء",
            background = CallDanger,
            onClick = onHangUp
        )
    }
}

@Composable
private fun CallAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    background: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(62.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(background, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(text = label, color = CallMuted, fontSize = 12.sp)
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return "%02d:%02d".format(minutes, seconds)
}
