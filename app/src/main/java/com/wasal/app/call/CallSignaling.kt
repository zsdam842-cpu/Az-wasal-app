package com.wasal.app.call

import android.util.Log
import com.wasal.app.data.network.ServerConfig
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

/**
 * قناة إشارات المكالمة (Socket.IO) على خادم الوسائط والمكالمات.
 *
 * منفصلة عن [com.wasal.app.data.network.SocketService] لأن الرسائل تعيش
 * على خادم الحسابات والمكالمات تعيش على خادم الوسائط؛ خلطهما كان يجعل
 * أحداث المكالمة تُرسل إلى خادم لا يعرفها فلا تصل أبدًا.
 */
class CallSignaling(
    private val listener: Listener
) {

    interface Listener {
        fun onConnected()
        fun onDisconnected(reason: String)
        fun onStarted(payload: JSONObject)
        fun onIncoming(payload: JSONObject)
        fun onAccepted(payload: JSONObject)
        fun onRejected(payload: JSONObject)
        fun onOffer(payload: JSONObject)
        fun onAnswer(payload: JSONObject)
        fun onIceCandidate(payload: JSONObject)
        fun onEnded(payload: JSONObject)
        fun onDetails(payload: JSONObject)
        fun onConnectionError(message: String)
    }

    private var socket: Socket? = null

    @Volatile
    var isConnected: Boolean = false
        private set

    fun connect(token: String) {
        disconnect()

        val options = IO.Options().apply {
            auth = mapOf("token" to token)
            transports = arrayOf("websocket")
            reconnection = true
            reconnectionDelay = 1000L
            reconnectionDelayMax = 8000L
            forceNew = true
        }

        val created = runCatching {
            IO.socket(ServerConfig.mediaSocketUrl, options)
        }.getOrElse { error ->
            listener.onConnectionError(
                error.message ?: "تعذر إنشاء اتصال المكالمات."
            )
            return
        }

        created.on(Socket.EVENT_CONNECT) {
            isConnected = true
            listener.onConnected()
        }

        created.on(Socket.EVENT_DISCONNECT) { args ->
            isConnected = false
            listener.onDisconnected(args.firstOrNull()?.toString().orEmpty())
        }

        created.on(Socket.EVENT_CONNECT_ERROR) { args ->
            isConnected = false
            listener.onConnectionError(
                args.firstOrNull()?.toString().orEmpty()
            )
        }

        created.on("call:started") { args ->
            payload(args)?.let(listener::onStarted)
        }

        created.on("call:incoming") { args ->
            payload(args)?.let(listener::onIncoming)
        }

        created.on("call:accept") { args ->
            payload(args)?.let(listener::onAccepted)
        }

        created.on("call:reject") { args ->
            payload(args)?.let(listener::onRejected)
        }

        created.on("call:offer") { args ->
            payload(args)?.let(listener::onOffer)
        }

        created.on("call:answer") { args ->
            payload(args)?.let(listener::onAnswer)
        }

        created.on("call:ice-candidate") { args ->
            payload(args)?.let(listener::onIceCandidate)
        }

        created.on("call:end") { args ->
            payload(args)?.let(listener::onEnded)
        }

        created.on("call:details") { args ->
            payload(args)?.let(listener::onDetails)
        }

        created.connect()
        socket = created
    }

    fun startCall(
        receiverId: String,
        type: String,
        receiverName: String,
        receiverCode: String,
        callerName: String,
        callerCode: String
    ) {
        emit(
            "call:start",
            JSONObject()
                .put("receiverId", receiverId)
                .put("type", type)
                .put("receiverName", receiverName)
                .put("receiverCode", receiverCode)
                .put("callerName", callerName)
                .put("callerCode", callerCode)
        )
    }

    /**
     * القبول والرفض والإنهاء تُرسل كمعرّف نصي مجرّد.
     *
     * إرسالها ككائن { callId } كان يجعل الخادم يتجاهلها لأنه يتوقع نصًا،
     * فلا يُقبل الاتصال ولا يُرسل عرض الوسائط وتبدو المكالمة صامتة أو لا تعمل.
     */
    fun accept(callId: String) {
        emitRaw("call:accept", callId)
    }

    fun reject(callId: String) {
        emitRaw("call:reject", callId)
    }

    fun end(callId: String) {
        emitRaw("call:end", callId)
    }

    fun sendOffer(
        callId: String,
        callerId: String,
        receiverId: String,
        type: String,
        sdp: String
    ) {
        emit(
            "call:offer",
            JSONObject()
                .put("callId", callId)
                .put("callerId", callerId)
                .put("receiverId", receiverId)
                .put("type", type)
                .put("offer", JSONObject().put("type", "offer").put("sdp", sdp))
        )
    }

    fun sendAnswer(callId: String, sdp: String) {
        emit(
            "call:answer",
            JSONObject()
                .put("callId", callId)
                .put("answer", JSONObject().put("type", "answer").put("sdp", sdp))
        )
    }

    fun sendIceCandidate(
        callId: String,
        sdpMid: String?,
        sdpMLineIndex: Int,
        candidate: String
    ) {
        emit(
            "call:ice-candidate",
            JSONObject()
                .put("callId", callId)
                .put(
                    "candidate",
                    JSONObject()
                        .put("candidate", candidate)
                        .put("sdpMid", sdpMid ?: "")
                        .put("sdpMLineIndex", sdpMLineIndex)
                )
        )
    }

    fun disconnect() {
        val current = socket ?: return

        runCatching {
            current.off()
            current.disconnect()
        }

        socket = null
        isConnected = false
    }

    private fun emitRaw(event: String, payload: String) {
        val current = socket

        if (current == null || !isConnected) {
            Log.w(TAG, "تعذر إرسال $event: قناة المكالمات غير متصلة")
            return
        }

        current.emit(event, payload)
    }

    private fun emit(event: String, payload: JSONObject) {
        val current = socket

        if (current == null || !isConnected) {
            Log.w(TAG, "تعذر إرسال $event: قناة المكالمات غير متصلة")
            return
        }

        current.emit(event, payload)
    }

    private fun payload(args: Array<Any>?): JSONObject? {
        val raw = args?.firstOrNull() ?: return null

        return runCatching { JSONObject(raw.toString()) }.getOrNull()
    }

    private companion object {
        const val TAG = "CallSignaling"
    }
}
