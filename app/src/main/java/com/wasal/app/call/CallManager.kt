package com.wasal.app.call

import android.content.Context
import android.util.Log
import com.wasal.app.data.local.AuthDataStore
import com.wasal.app.data.network.CoreApiClient
import com.wasal.app.data.network.ServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

enum class CallKind(val wireValue: String) {
    AUDIO("audio"),
    VIDEO("video");

    companion object {
        fun from(value: String): CallKind =
            if (value.equals("video", ignoreCase = true)) VIDEO else AUDIO
    }
}

enum class CallPhase {
    IDLE,
    OUTGOING,
    INCOMING,
    CONNECTING,
    ACTIVE,
    ENDED,
    FAILED
}

data class CallUiState(
    val phase: CallPhase = CallPhase.IDLE,
    val callId: String = "",
    val peerId: String = "",
    val peerName: String = "",
    val peerCode: String = "",
    val kind: CallKind = CallKind.AUDIO,
    val microphoneEnabled: Boolean = true,
    val cameraEnabled: Boolean = true,
    val connected: Boolean = false,
    val startedAtMillis: Long = 0L,
    val message: String? = null
) {
    val isBusy: Boolean
        get() = phase != CallPhase.IDLE
}

/**
 * مدير المكالمات في التطبيق.
 *
 * يربط ثلاث طبقات كانت منفصلة تمامًا:
 * 1) إشارات Socket.IO على خادم الوسائط والمكالمات.
 * 2) WebRTC الحقيقي (WebRtcEngine) للصوت والصورة.
 * 3) سجل المكالمات على الخادم (يُكتب عند البدء والإجابة والانتهاء).
 */
class CallManager(
    private val context: Context,
    private val authDataStore: AuthDataStore
) : CallSignaling.Listener, WebRtcEngine.Listener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(CallUiState())
    val state: StateFlow<CallUiState> = _state.asStateFlow()

    private val signaling = CallSignaling(this)
    private val engine = WebRtcEngine(context.applicationContext, this)

    private var signalingConnected = false
    private var pendingVideo = false
    private var localId = ""
    private var localName = "مستخدم وصال"
    private var localCode = ""

    /**
     * يفتح قناة الإشارات ويُبقيها مفتوحة.
     *
     * بدون هذا الاتصال الدائم لا تصل المكالمات الواردة أبدًا، لأن الأحداث
     * تُبثّ على user:<id> ولا يعرف التطبيق بها إن لم يكن متصلًا مسبقًا.
     */
    fun ensureSignaling() {
        if (signalingConnected) return

        scope.launch {
            if (signalingConnected) return@launch

            val token = authDataStore.accessToken.first().orEmpty()

            if (token.isBlank()) return@launch

            signaling.connect(token)
        }
    }

    fun updateLocalIdentity(displayName: String, wasalCode: String) {
        if (displayName.isNotBlank()) localName = displayName
        if (wasalCode.isNotBlank()) localCode = wasalCode
    }

    fun attachRemoteRenderer(view: SurfaceViewRenderer) {
        engine.attachRemoteRenderer(view)
    }

    fun attachLocalRenderer(view: SurfaceViewRenderer) {
        engine.attachLocalRenderer(view)
    }

    fun releaseRemoteRenderer() {
        engine.releaseRemoteRenderer()
    }

    fun releaseLocalRenderer() {
        engine.releaseLocalRenderer()
    }

    /** يبدأ مكالمة صادرة إلى مستخدم محدد. */
    fun startCall(
        peerId: String,
        peerName: String,
        peerCode: String,
        kind: CallKind
    ) {
        if (_state.value.isBusy) return

        if (peerId.isBlank()) {
            showTransientFailure(
                "تعذر تحديد هوية الطرف الآخر. أعد فتح المحادثة ثم حاول."
            )
            return
        }

        _state.value = CallUiState(
            phase = CallPhase.OUTGOING,
            peerId = peerId,
            peerName = peerName,
            peerCode = peerCode,
            kind = kind,
            message = "جارٍ الاتصال..."
        )

        pendingVideo = kind == CallKind.VIDEO

        scope.launch {
            val token = authDataStore.accessToken.first().orEmpty()
            localId = authDataStore.userId.first().orEmpty()

            if (token.isBlank()) {
                fail("يلزم تسجيل الدخول لبدء المكالمة.")
                return@launch
            }

            // تجهيز الوسائط أولًا حتى يكون الاتصال جاهزًا لحظة قبول الطرف الآخر.
            val ready = prepareEngine()

            if (!ready) return@launch

            if (signalingConnected) {
                emitStartCall()
            } else {
                signaling.connect(token)
            }
        }
    }

    fun acceptIncoming() {
        val current = _state.value

        if (current.phase != CallPhase.INCOMING) return

        _state.value = current.copy(
            phase = CallPhase.CONNECTING,
            message = "جارٍ الاتصال..."
        )

        stopRinging()

        pendingVideo = current.kind == CallKind.VIDEO

        scope.launch {
            if (prepareEngine()) {
                signaling.accept(current.callId)
            }
        }
    }

    fun rejectCall() {
        val current = _state.value

        if (current.callId.isNotBlank()) {
            signaling.reject(current.callId)
        }

        finishWith("تم رفض المكالمة.")
    }

    fun hangUp() {
        val current = _state.value

        if (current.callId.isNotBlank()) {
            signaling.end(current.callId)
        }

        finishWith("انتهت المكالمة.")
    }

    fun toggleMicrophone(): Boolean {
        val enabled = !_state.value.microphoneEnabled

        engine.setMicrophoneEnabled(enabled)
        _state.value = _state.value.copy(microphoneEnabled = enabled)

        return enabled
    }

    fun toggleCamera(): Boolean {
        val enabled = engine.setCameraEnabled(!_state.value.cameraEnabled)

        _state.value = _state.value.copy(cameraEnabled = enabled)

        return enabled
    }

    fun switchCamera(): Boolean = engine.switchCamera()

    fun dismissEndedState() {
        if (_state.value.phase == CallPhase.ENDED ||
            _state.value.phase == CallPhase.FAILED
        ) {
            reset()
        }
    }

    fun release() {
        stopRinging()
        engine.close()
        signaling.disconnect()
        signalingConnected = false
        _state.value = CallUiState()
    }

    // ---------------- إشارات المكالمة ----------------

    override fun onConnected() {
        signalingConnected = true

        val current = _state.value

        if (current.phase == CallPhase.OUTGOING && current.callId.isBlank()) {
            emitStartCall()
        }
    }

    override fun onDisconnected(reason: String) {
        signalingConnected = false

        // إعادة اتصال تلقائية حتى لا تضيع المكالمات الواردة بعد انقطاع الشبكة.
        scope.launch {
            delay(4000)
            ensureSignaling()
        }
    }

    override fun onStarted(payload: JSONObject) {
        val callId = payload.optString("callId")

        if (callId.isBlank()) return

        _state.value = _state.value.copy(
            phase = CallPhase.OUTGOING,
            callId = callId,
            message = "جارٍ الرنين..."
        )
    }

    override fun onIncoming(payload: JSONObject) {
        val callId = payload.optString("callId")

        if (callId.isBlank()) return

        // مكالمة أخرى جارية: تُرفض الجديدة بدل إفساد الحالية.
        if (_state.value.isBusy && _state.value.callId.isNotBlank()) {
            signaling.reject(callId)
            return
        }

        _state.value = CallUiState(
            phase = CallPhase.INCOMING,
            callId = callId,
            peerId = payload.optString("callerId"),
            peerName = payload.optString("callerName").ifBlank { "مستخدم وصال" },
            peerCode = payload.optString("callerCode"),
            kind = CallKind.from(payload.optString("type", "audio")),
            message = "مكالمة واردة"
        )

        startRinging()
    }

    override fun onAccepted(payload: JSONObject) {
        val current = _state.value

        if (payload.optString("callId") != current.callId) return

        _state.value = current.copy(
            phase = CallPhase.CONNECTING,
            message = "جارٍ الاتصال..."
        )

        scope.launch {
            if (!prepareEngine()) return@launch

            engine.createOffer { sdp ->
                scope.launch {
                    val callerId = authDataStore.userId.first().orEmpty()
                        .ifBlank { localId }

                    signaling.sendOffer(
                        callId = current.callId,
                        callerId = callerId,
                        receiverId = current.peerId,
                        type = current.kind.wireValue,
                        sdp = sdp
                    )
                }
            }
        }
    }

    override fun onRejected(payload: JSONObject) {
        if (payload.optString("callId") != _state.value.callId) return

        finishWith("رفض الطرف الآخر المكالمة.")
    }

    override fun onOffer(payload: JSONObject) {
        val current = _state.value

        if (payload.optString("callId") != current.callId) return

        val sdp = payload.optJSONObject("offer")?.optString("sdp").orEmpty()

        if (sdp.isBlank()) {
            fail("وصل عرض مكالمة غير صالح.")
            return
        }

        scope.launch {
            if (!prepareEngine()) return@launch

            engine.applyRemoteOffer(sdp) { answer ->
                signaling.sendAnswer(current.callId, answer)
            }
        }
    }

    override fun onAnswer(payload: JSONObject) {
        val sdp = payload.optJSONObject("answer")?.optString("sdp").orEmpty()

        if (sdp.isBlank()) return

        engine.applyRemoteAnswer(sdp)
    }

    override fun onIceCandidate(payload: JSONObject) {
        if (payload.optString("callId") != _state.value.callId) return

        val candidate = payload.optJSONObject("candidate") ?: return

        engine.addRemoteIceCandidate(
            sdpMid = candidate.optString("sdpMid"),
            sdpMLineIndex = candidate.optInt("sdpMLineIndex", 0),
            candidate = candidate.optString("candidate")
        )
    }

    override fun onEnded(payload: JSONObject) {
        if (payload.optString("callId") != _state.value.callId) return

        val reason = payload.optString("reason")

        finishWith(
            when (reason) {
                "missed" -> "مكالمة فائتة."
                "disconnect" -> "انقطع اتصال الطرف الآخر."
                else -> "انتهت المكالمة."
            }
        )
    }

    override fun onDetails(payload: JSONObject) {
        val current = _state.value

        if (payload.optString("callId") != current.callId) return
        if (current.connected) return

        val fromCaller = payload.optString("callerId") == current.peerId

        val name = if (fromCaller) {
            payload.optString("callerName")
        } else {
            payload.optString("receiverName")
        }

        if (name.isNotBlank()) {
            _state.value = current.copy(peerName = name)
        }
    }

    override fun onConnectionError(message: String) {
        if (_state.value.phase == CallPhase.OUTGOING &&
            _state.value.callId.isBlank()
        ) {
            fail("تعذر الوصول إلى خادم المكالمات. تحقق من اتصال الإنترنت.")
        }
    }

    // ---------------- WebRTC ----------------

    override fun onIceCandidate(candidate: IceCandidate) {
        signaling.sendIceCandidate(
            callId = _state.value.callId,
            sdpMid = candidate.sdpMid,
            sdpMLineIndex = candidate.sdpMLineIndex,
            candidate = candidate.sdp
        )
    }

    override fun onRemoteVideoTrack(track: VideoTrack) {
        // الربط بالعرض يتم داخل المحرك عند وجود SurfaceViewRenderer.
    }

    override fun onConnectionStateChanged(state: PeerConnection.PeerConnectionState) {
        when (state) {
            PeerConnection.PeerConnectionState.CONNECTED -> markActive()

            PeerConnection.PeerConnectionState.DISCONNECTED -> {
                if (_state.value.phase == CallPhase.ACTIVE) {
                    finishWith("انقطع الاتصال بالمكالمة.")
                }
            }

            PeerConnection.PeerConnectionState.FAILED -> {
                fail("تعذر تأسيس اتصال الصوت/الصورة.")
            }

            else -> Unit
        }
    }

    override fun onError(message: String) {
        Log.w(TAG, message)
        _state.value = _state.value.copy(message = message)
    }

    // ---------------- مساعدات ----------------

    private fun emitStartCall() {
        val current = _state.value

        signaling.startCall(
            receiverId = current.peerId,
            type = current.kind.wireValue,
            receiverName = current.peerName,
            receiverCode = current.peerCode,
            callerName = localName,
            callerCode = localCode
        )
    }

    /** يجهّز وسائط المكالمة، ويعيد true فقط عندما يكون الاتصال جاهزًا. */
    private suspend fun prepareEngine(): Boolean {
        val servers = fetchIceServers()

        val ready = withContext(Dispatchers.Default) {
            engine.prepareLocalMedia(
                video = pendingVideo || _state.value.kind == CallKind.VIDEO,
                iceServers = servers
            )
        }

        if (!ready) {
            fail("تعذر تجهيز الميكروفون أو الكاميرا لهذه المكالمة.")
        }

        return ready
    }

    private suspend fun fetchIceServers(): List<PeerConnection.IceServer> {
        val fallback = listOf(
            PeerConnection.IceServer.builder(ServerConfig.STUN_SERVER)
                .createIceServer()
        )

        val token = authDataStore.accessToken.first().orEmpty()

        if (token.isBlank()) return fallback

        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(
                        ServerConfig.MEDIA_CALLS_SERVER_URL.trimEnd('/') +
                            ServerConfig.ICE_SERVERS_ENDPOINT
                    )
                    .header("Authorization", "Bearer $token")
                    .build()

                CoreApiClient.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use fallback
                    }

                    val body = response.body?.string().orEmpty()
                    val array = JSONObject(body).optJSONArray("iceServers")
                        ?: return@use fallback

                    parseIceServers(array)
                }
            }.getOrDefault(fallback)
        }
    }

    private fun parseIceServers(
        array: JSONArray
    ): List<PeerConnection.IceServer> {
        val servers = mutableListOf<PeerConnection.IceServer>()

        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue

            val urls = mutableListOf<String>()

            when (val rawUrls = item.opt("urls")) {
                is String -> if (rawUrls.isNotBlank()) urls.add(rawUrls)

                is JSONArray -> {
                    for (i in 0 until rawUrls.length()) {
                        val url = rawUrls.optString(i)
                        if (url.isNotBlank()) urls.add(url)
                    }
                }
            }

            if (urls.isEmpty()) continue

            val builder = PeerConnection.IceServer.builder(urls)

            item.optString("username")
                .takeIf { it.isNotBlank() }
                ?.let { builder.setUsername(it) }

            item.optString("credential")
                .takeIf { it.isNotBlank() }
                ?.let { builder.setPassword(it) }

            servers.add(builder.createIceServer())
        }

        return servers.ifEmpty {
            listOf(
                PeerConnection.IceServer.builder(ServerConfig.STUN_SERVER)
                    .createIceServer()
            )
        }
    }

    private fun markActive() {
        val current = _state.value

        if (current.phase == CallPhase.ACTIVE) return

        stopRinging()

        _state.value = current.copy(
            phase = CallPhase.ACTIVE,
            connected = true,
            startedAtMillis = System.currentTimeMillis(),
            message = null
        )
    }

    private var ringtone: android.media.Ringtone? = null

    private fun startRinging() {
        stopRinging()

        runCatching {
            val uri = android.media.RingtoneManager.getDefaultUri(
                android.media.RingtoneManager.TYPE_RINGTONE
            )

            ringtone = android.media.RingtoneManager.getRingtone(context, uri)?.also {
                it.isLooping = true
                it.play()
            }
        }
    }

    private fun stopRinging() {
        runCatching { ringtone?.stop() }
        ringtone = null
    }

    private fun showTransientFailure(message: String) {
        _state.value = CallUiState(phase = CallPhase.FAILED, message = message)

        scope.launch {
            delay(3500)
            if (_state.value.phase == CallPhase.FAILED) {
                reset()
            }
        }
    }

    private fun fail(message: String) {
        stopRinging()
        engine.close()

        _state.value = _state.value.copy(
            phase = CallPhase.FAILED,
            connected = false,
            message = message
        )

        scope.launch {
            delay(4000)
            if (_state.value.phase == CallPhase.FAILED) {
                reset()
            }
        }
    }

    private fun finishWith(message: String) {
        stopRinging()
        engine.close()

        val current = _state.value

        _state.value = current.copy(
            phase = CallPhase.ENDED,
            connected = false,
            message = message
        )

        scope.launch {
            delay(1800)
            reset()
        }
    }

    private fun reset() {
        stopRinging()
        engine.close()
        pendingVideo = false
        _state.value = CallUiState()

        // نُبقي قناة الإشارات مفتوحة لوصول المكالمات القادمة.
        ensureSignaling()
    }

    private companion object {
        const val TAG = "WasalCallManager"
    }
}
