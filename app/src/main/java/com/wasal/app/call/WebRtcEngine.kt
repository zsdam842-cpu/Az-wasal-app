package com.wasal.app.call

import android.content.Context
import android.media.AudioManager
import android.media.MediaRecorder
import android.util.Log
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.atomic.AtomicBoolean

/**
 * محرك WebRTC الحقيقي للمكالمات الصوتية والمرئية على أندرويد.
 *
 * المسؤول عن: إنشاء PeerConnection، التقاط الصوت والكاميرا، إنتاج
 * العرض/الإجابة، تبادل مرشحي ICE، وربط مسار الفيديو البعيد/القريب
 * بعناصر العرض (SurfaceViewRenderer).
 *
 * كان التطبيق يرسل إشارات المكالمة فقط بلا WebRTC، فتظهر شاشة
 * "مكالمة" بلا صوت ولا صورة. هذا المحرك يغلق تلك الفجوة.
 */
class WebRtcEngine(
    private val context: Context,
    private val listener: Listener
) {

    interface Listener {
        fun onIceCandidate(candidate: IceCandidate)
        fun onRemoteVideoTrack(track: VideoTrack)
        fun onConnectionStateChanged(state: PeerConnection.PeerConnectionState)
        fun onError(message: String)
    }

    private val eglBase: EglBase by lazy {
        EglBase.create()
    }

    private var factory: PeerConnectionFactory? = null
    private var audioDeviceModule: JavaAudioDeviceModule? = null

    private val audioManager: AudioManager? by lazy {
        context.applicationContext
            .getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private var remoteRenderer: SurfaceViewRenderer? = null
    private var localRenderer: SurfaceViewRenderer? = null

    private val remoteSinkAttached = AtomicBoolean(false)

    @Volatile
    private var pendingCandidates = mutableListOf<IceCandidate>()

    @Volatile
    private var remoteDescriptionSet = false

    val cameraEnabled: Boolean
        get() = videoTrack?.enabled() ?: false

    /** تجهيز عنصر عرض الفيديو البعيد (المتصل). */
    fun attachRemoteRenderer(view: SurfaceViewRenderer) {
        remoteRenderer = view
        remoteSinkAttached.set(false)

        view.init(eglBase.eglBaseContext, null)
        view.setEnableHardwareScaler(true)
        view.setScalingType(
            org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL
        )

        remoteVideoTrack?.let {
            it.addSink(view)
            remoteSinkAttached.set(true)
        }
    }

    /** تجهيز عنصر عرض الفيديو القريب (صورة المستخدم). */
    fun attachLocalRenderer(view: SurfaceViewRenderer) {
        localRenderer = view

        // المعاينة الصغيرة يجب أن تطفو فوق شاشة الطرف الآخر:
        // بدونها يعتمد ترتيب SurfaceView على ترتيب الإضافة فقط،
        // فتختفي صورتي خلف الفيديو البعيد على أجهزة كثيرة.
        runCatching { view.setZOrderMediaOverlay(true) }

        view.init(eglBase.eglBaseContext, null)
        view.setEnableHardwareScaler(true)
        view.setMirror(true)
        view.setScalingType(
            org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL
        )

        videoTrack?.addSink(view)
    }

    fun releaseRemoteRenderer() {
        val view = remoteRenderer ?: return

        runCatching { remoteVideoTrack?.removeSink(view) }
        runCatching { view.release() }

        remoteRenderer = null
        remoteSinkAttached.set(false)
    }

    fun releaseLocalRenderer() {
        val view = localRenderer ?: return

        runCatching { videoTrack?.removeSink(view) }
        runCatching { view.release() }

        localRenderer = null
    }

    private var remoteVideoTrack: VideoTrack? = null

    /** تجهيز المصادر المحلية قبل إنشاء العرض أو الإجابة. */
    @Synchronized
    fun prepareLocalMedia(
        video: Boolean,
        iceServers: List<PeerConnection.IceServer>
    ): Boolean {
        if (peerConnection != null) {
            return true
        }

        return try {
            startAudioSession()

            val createdFactory = ensureFactory() ?: run {
                listener.onError("تعذر تهيئة محرك المكالمات على هذا الجهاز.")
                return false
            }

            val configuration = PeerConnection.RTCConfiguration(iceServers).apply {
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
                iceTransportsType = PeerConnection.IceTransportsType.ALL
                continualGatheringPolicy =
                    PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY

                // مخزن اهتزاز أوسع: يقلّل تقطيع الصوت على الشبكات الضعيفة
                // بدل أن يسقط الصوت تمامًا عند تأخر الحزم.
                audioJitterBufferMaxPackets = 200
                audioJitterBufferFastAccelerate = true
            }

            val connection = createdFactory.createPeerConnection(
                configuration,
                observer
            ) ?: run {
                listener.onError("تعذر إنشاء اتصال المكالمة.")
                return false
            }

            peerConnection = connection

            val audioConstraints = MediaConstraints().apply {
                mandatory.add(
                    MediaConstraints.KeyValuePair("echoCancellation", "true")
                )
                mandatory.add(
                    MediaConstraints.KeyValuePair("noiseSuppression", "true")
                )
                mandatory.add(
                    MediaConstraints.KeyValuePair("autoGainControl", "true")
                )
            }

            audioSource = createdFactory.createAudioSource(audioConstraints)
            audioTrack = audioSource?.let {
                createdFactory.createAudioTrack("wasal-audio", it)
            }

            audioTrack?.let { track ->
                val sender = connection.addTrack(
                    track,
                    listOf("wasal-stream")
                )

                // سقف بِت ريت للصوت: جودة واضحة بلا استهلاك زائد للبيانات.
                runCatching {
                    val parameters = sender.parameters

                    parameters.encodings?.forEach { encoding ->
                        encoding.maxBitrateBps = 32000
                    }

                    sender.parameters = parameters
                }
            }

            if (video) {
                val capturer = createVideoCapturer()

                if (capturer == null) {
                    listener.onError(
                        "لا توجد كاميرا متاحة على هذا الجهاز؛ المكالمة ستبدأ صوتية."
                    )
                } else {
                    val helper = SurfaceTextureHelper.create(
                        "WasalCaptureThread",
                        eglBase.eglBaseContext
                    )

                    surfaceTextureHelper = helper
                    videoCapturer = capturer

                    val source = createdFactory.createVideoSource(
                        capturer.isScreencast
                    )

                    videoSource = source

                    capturer.initialize(
                        helper,
                        context,
                        source.capturerObserver
                    )

                    try {
                        capturer.startCapture(1280, 720, 30)
                    } catch (error: Exception) {
                        Log.w(TAG, "startCapture failed", error)
                        capturer.startCapture(640, 480, 24)
                    }

                    val track = createdFactory.createVideoTrack(
                        "wasal-video",
                        source
                    )

                    videoTrack = track
                    track.setEnabled(true)

                    connection.addTrack(track, listOf("wasal-stream"))

                    // سقف للبِت ريت يمنع التقطيع والاهتزاز على الشبكات الضعيفة،
                    // مع إبقاء الترميز التكيفي يعمل تحت هذا السقف.
                    runCatching {
                        connection.senders
                            .firstOrNull { it.track()?.kind() == "video" }
                            ?.let { sender ->
                                val params = sender.parameters
                                params.encodings.forEach { encoding ->
                                    encoding.maxBitrateBps = 900_000
                                    encoding.maxFramerate = 30
                                }
                                sender.parameters = params
                            }
                    }

                    localRenderer?.let { track.addSink(it) }
                }
            }

            true
        } catch (error: Exception) {
            Log.e(TAG, "prepareLocalMedia failed", error)
            listener.onError(
                error.message ?: "تعذر تجهيز الميكروفون أو الكاميرا."
            )
            false
        }
    }

    fun createOffer(onSdp: (String) -> Unit) {
        val connection = peerConnection ?: return

        connection.createOffer(
            object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    if (sdp == null) {
                        listener.onError("تعذر إنشاء عرض المكالمة.")
                        return
                    }

                    connection.setLocalDescription(
                        SilentSdpObserver,
                        sdp
                    )

                    onSdp(sdp.description)
                }

                override fun onSetSuccess() = Unit

                override fun onCreateFailure(error: String?) {
                    listener.onError(error ?: "تعذر إنشاء عرض المكالمة.")
                }

                override fun onSetFailure(error: String?) = Unit
            },
            offerConstraints
        )
    }

    fun applyRemoteOffer(sdp: String, onAnswer: (String) -> Unit) {
        val connection = peerConnection ?: return

        connection.setRemoteDescription(
            object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) = Unit

                override fun onSetSuccess() {
                    remoteDescriptionSet = true
                    flushPendingCandidates()
                    answer(connection, onAnswer)
                }

                override fun onCreateFailure(p0: String?) = Unit

                override fun onSetFailure(error: String?) {
                    listener.onError(error ?: "تعذر استقبال عرض المكالمة.")
                }
            },
            SessionDescription(SessionDescription.Type.OFFER, sdp)
        )
    }

    fun applyRemoteAnswer(sdp: String) {
        val connection = peerConnection ?: return

        connection.setRemoteDescription(
            object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) = Unit

                override fun onSetSuccess() {
                    remoteDescriptionSet = true
                    flushPendingCandidates()
                }

                override fun onCreateFailure(p0: String?) = Unit

                override fun onSetFailure(error: String?) {
                    listener.onError(error ?: "تعذر قبول إجابة المكالمة.")
                }
            },
            SessionDescription(SessionDescription.Type.ANSWER, sdp)
        )
    }

    fun addRemoteIceCandidate(
        sdpMid: String?,
        sdpMLineIndex: Int,
        candidate: String
    ) {
        val remote = IceCandidate(sdpMid ?: "0", sdpMLineIndex, candidate)
        val connection = peerConnection

        if (connection == null || !remoteDescriptionSet) {
            synchronized(pendingCandidates) {
                pendingCandidates.add(remote)
            }
            return
        }

        runCatching { connection.addIceCandidate(remote) }
            .onFailure { Log.w(TAG, "addIceCandidate failed", it) }
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        audioTrack?.setEnabled(enabled)
    }

    fun setCameraEnabled(enabled: Boolean): Boolean {
        val track = videoTrack ?: return false

        track.setEnabled(enabled)
        return track.enabled()
    }

    fun switchCamera(): Boolean {
        val capturer = videoCapturer as? CameraVideoCapturer ?: return false

        return runCatching {
            capturer.switchCamera(null)
            true
        }.getOrDefault(false)
    }

    fun close() {
        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }
        runCatching { surfaceTextureHelper?.dispose() }

        videoCapturer = null
        surfaceTextureHelper = null

        runCatching { videoTrack?.dispose() }
        runCatching { videoSource?.dispose() }
        runCatching { audioTrack?.dispose() }
        runCatching { audioSource?.dispose() }

        videoTrack = null
        videoSource = null
        audioTrack = null
        audioSource = null

        runCatching {
            peerConnection?.close()
            peerConnection?.dispose()
        }

        peerConnection = null
        remoteDescriptionSet = false
        synchronized(pendingCandidates) { pendingCandidates.clear() }

        stopAudioSession()
    }

    private fun answer(
        connection: PeerConnection,
        onAnswer: (String) -> Unit
    ) {
        connection.createAnswer(
            object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    if (sdp == null) {
                        listener.onError("تعذر إنشاء إجابة المكالمة.")
                        return
                    }

                    connection.setLocalDescription(
                        SilentSdpObserver,
                        sdp
                    )

                    onAnswer(sdp.description)
                }

                override fun onSetSuccess() = Unit

                override fun onCreateFailure(error: String?) {
                    listener.onError(error ?: "تعذر إنشاء إجابة المكالمة.")
                }

                override fun onSetFailure(error: String?) = Unit
            },
            offerConstraints
        )
    }

    private fun flushPendingCandidates() {
        val connection = peerConnection ?: return
        val snapshot = synchronized(pendingCandidates) {
            val copy = pendingCandidates.toList()
            pendingCandidates.clear()
            copy
        }

        for (candidate in snapshot) {
            runCatching { connection.addIceCandidate(candidate) }
        }
    }

    /**
     * تهيئة مسار الصوت للمكالمة: وضع الاتصال + مكبّر الصوت.
     * بدونه يخرج الصوت من سماعة الأذن أو لا يخرج إطلاقًا على أجهزة كثيرة.
     */
    private fun startAudioSession() {
        runCatching {
            val manager = audioManager ?: return

            @Suppress("DEPRECATION")
            manager.mode = AudioManager.MODE_IN_COMMUNICATION

            @Suppress("DEPRECATION")
            manager.isSpeakerphoneOn = true

            val maxVolume = manager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            manager.setStreamVolume(
                AudioManager.STREAM_VOICE_CALL,
                (maxVolume * 0.9f).toInt(),
                0
            )
        }
    }

    private fun stopAudioSession() {
        runCatching {
            val manager = audioManager ?: return

            @Suppress("DEPRECATION")
            manager.isSpeakerphoneOn = false

            @Suppress("DEPRECATION")
            manager.mode = AudioManager.MODE_NORMAL
        }
    }

    private fun ensureFactory(): PeerConnectionFactory? {
        factory?.let { return it }

        synchronized(FACTORY_LOCK) {
            factory?.let { return it }

            try {
                if (!initialized) {
                    PeerConnectionFactory.initialize(
                        PeerConnectionFactory
                            .InitializationOptions
                            .builder(context.applicationContext)
                            .setEnableInternalTracer(false)
                            .createInitializationOptions()
                    )
                    initialized = true
                }

                val encoderFactory = DefaultVideoEncoderFactory(
                    eglBase.eglBaseContext,
                    true,
                    true
                )

                val decoderFactory = DefaultVideoDecoderFactory(
                    eglBase.eglBaseContext
                )

                // وحدة صوت صريحة: الإعداد الافتراضي كان يترك بعض الأجهزة
                // بلا صوت إطلاقًا في المكالمة. هنا نفعّل إلغاء الصدى وكتم
                // الضجيج ونستخدم مصدر الصوت المخصص للمكالمات.
                val adm = JavaAudioDeviceModule.builder(context.applicationContext)
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                    .setUseHardwareAcousticEchoCanceler(true)
                    .setUseHardwareNoiseSuppressor(true)
                    .setUseStereoInput(false)
                    .setUseStereoOutput(false)
                    .createAudioDeviceModule()

                audioDeviceModule = adm

                val created = PeerConnectionFactory
                    .builder()
                    .setAudioDeviceModule(adm)
                    .setVideoEncoderFactory(encoderFactory)
                    .setVideoDecoderFactory(decoderFactory)
                    .createPeerConnectionFactory()

                factory = created
                return created
            } catch (error: Exception) {
                Log.e(TAG, "factory init failed", error)
                return null
            }
        }
    }

    private fun createVideoCapturer(): VideoCapturer? {
        val enumerator: CameraEnumerator = if (
            Camera2Enumerator.isSupported(context)
        ) {
            Camera2Enumerator(context)
        } else {
            Camera1Enumerator(true)
        }

        val names = enumerator.deviceNames

        val front = names.firstOrNull { enumerator.isFrontFacing(it) }
        val back = names.firstOrNull { enumerator.isBackFacing(it) }

        val chosen = front ?: back ?: names.firstOrNull() ?: return null

        return enumerator.createCapturer(chosen, null)
    }

    private val offerConstraints = MediaConstraints().apply {
        mandatory.add(
            MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true")
        )
        mandatory.add(
            MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true")
        )
    }

    private val observer = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            if (state == PeerConnection.IceConnectionState.FAILED) {
                listener.onError("تعذر تأسيس اتصال الصوت/الصورة.")
            }
        }

        override fun onStandardizedIceConnectionChange(
            newState: PeerConnection.IceConnectionState?
        ) = Unit

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
            listener.onConnectionStateChanged(
                newState ?: PeerConnection.PeerConnectionState.NEW
            )
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit

        override fun onIceCandidate(candidate: IceCandidate?) {
            if (candidate != null) {
                listener.onIceCandidate(candidate)
            }
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit

        override fun onAddStream(stream: MediaStream?) {
            val track = stream?.videoTracks?.firstOrNull()
            if (track != null) {
                attachRemoteVideo(track)
            }
        }

        override fun onRemoveStream(stream: MediaStream?) = Unit

        override fun onDataChannel(channel: DataChannel?) = Unit

        override fun onRenegotiationNeeded() = Unit

        override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
            val track = receiver?.track()

            if (track is VideoTrack) {
                attachRemoteVideo(track)
            }
        }

        override fun onTrack(transceiver: RtpTransceiver?) {
            val track = transceiver?.receiver?.track()

            if (track is VideoTrack) {
                attachRemoteVideo(track)
            }
        }
    }

    private fun attachRemoteVideo(track: VideoTrack) {
        remoteVideoTrack = track

        remoteRenderer?.let { renderer ->
            if (remoteSinkAttached.compareAndSet(false, true)) {
                track.addSink(renderer)
            }
        }

        listener.onRemoteVideoTrack(track)
    }

    private object SilentSdpObserver : SdpObserver {
        override fun onCreateSuccess(p0: SessionDescription?) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(p0: String?) = Unit
        override fun onSetFailure(p0: String?) = Unit
    }

    private companion object {
        const val TAG = "WasalWebRtc"

        val FACTORY_LOCK = Any()

        @Volatile
        var initialized = false
    }
}
