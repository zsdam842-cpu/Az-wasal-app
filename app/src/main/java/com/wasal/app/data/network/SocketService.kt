package com.wasal.app.data.network

import com.wasal.app.data.local.AuthDataStore
import com.wasal.app.data.repository.TokenAuthenticator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * اتصال Socket.IO واحد للتطبيق كله مع خادم الرسائل.
 *
 * - المصادقة برمز الوصول عبر handshake.auth.token.
 * - إعادة اتصال تلقائي حتى 20 ثانية بين المحاولات.
 * - عند رفض الرمز: تجديده ثم إعادة فتح الاتصال برمز جديد.
 * - إرسال مؤقت للرسائل المكتوبة دون اتصال عند عودة الشبكة.
 */
object SocketService {

    private var socket: io.socket.client.Socket? = null
    private var authDataStore: AuthDataStore? = null
    private var tokenAuthenticator: TokenAuthenticator? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val listeners = mutableMapOf<String, MutableList<(JSONObject) -> Unit>>()

    /** رسائل أُرسلت دون اتصال وتُعاد عند عودة الاتصال. */
    private val pendingQueue = ConcurrentLinkedQueue<String>()

    @Volatile
    var isConnected: Boolean = false
        private set

    fun init(dataStore: AuthDataStore) {
        if (authDataStore === dataStore) return

        authDataStore = dataStore
        tokenAuthenticator = TokenAuthenticator(dataStore)
    }

    fun on(event: String, callback: (JSONObject) -> Unit) {
        val registered = synchronized(listeners) {
            listeners.getOrPut(event) { mutableListOf() }.add(callback)
        }

        val current = socket

        if (registered && current != null) {
            current.on(event) { args ->
                val payload = runCatching {
                    JSONObject(args.firstOrNull()?.toString().orEmpty())
                }.getOrNull() ?: return@on
                callback(payload)
            }
        }
    }

    fun connect() {
        val dataStore = authDataStore ?: return

        if (socket != null && isConnected) return

        scope.launch {
            val token = dataStore.accessToken.first().orEmpty()

            if (token.isBlank()) return@launch

            withContext(Dispatchers.Main) {
                openSocket(token)
            }
        }
    }

    private fun openSocket(token: String) {
        disconnect(keepPending = true)

        val socketUrl = runCatching {
            val uri = URI(ServerConfig.CORE_SERVER_URL)

            "${if (uri.scheme == "https") "wss" else "ws"}://" +
                uri.host +
                (uri.port.takeIf { it != -1 }?.let { ":$it" } ?: "")
        }.getOrDefault(ServerConfig.CORE_SERVER_URL)

        val options = io.socket.client.IO.Options().apply {
            auth = mapOf("token" to token)
            transports = arrayOf("websocket")
            reconnection = true
            reconnectionDelay = 1000L
            reconnectionDelayMax = 20000L
        }

        val newSocket = io.socket.client.IO.socket(socketUrl, options)

        newSocket.on(io.socket.client.Socket.EVENT_CONNECT) {
            isConnected = true
            flushPending()
        }

        newSocket.on(io.socket.client.Socket.EVENT_DISCONNECT) {
            isConnected = false
        }

        newSocket.on(io.socket.client.Socket.EVENT_CONNECT_ERROR) { args ->
            isConnected = false

            val message = args.firstOrNull()?.toString().orEmpty()

            if (
                message.contains("INVALID_OR_EXPIRED_TOKEN") ||
                message.contains("TOKEN_REQUIRED") ||
                message.contains("USER_NOT_FOUND")
            ) {
                scope.launch {
                    val refreshed =
                        tokenAuthenticator?.refreshAccessToken()?.isSuccess == true

                    if (refreshed) {
                        val fresh = authDataStore?.accessToken?.first().orEmpty()

                        if (fresh.isNotBlank()) {
                            withContext(Dispatchers.Main) {
                                openSocket(fresh)
                            }
                        }
                    }
                }
            }
        }

        synchronized(listeners) {
            for ((event, callbacks) in listeners) {
                for (callback in callbacks) {
                    newSocket.on(event) { args ->
                        val payload = runCatching {
                            JSONObject(args.firstOrNull()?.toString().orEmpty())
                        }.getOrNull() ?: return@on
                        callback(payload)
                    }
                }
            }
        }

        newSocket.connect()
        socket = newSocket
    }

    fun disconnect(keepPending: Boolean = false) {
        if (!keepPending) pendingQueue.clear()

        runCatching {
            socket?.off()
            socket?.disconnect()
        }

        socket = null
        isConnected = false
    }

    fun emit(event: String, payload: JSONObject) {
        val current = socket

        if (current != null && isConnected) {
            current.emit(event, payload)
        } else if (event == "send_message") {
            // إرسال مؤقت: يُعاد عند عودة الاتصال.
            pendingQueue.add(payload.toString())

            if (pendingQueue.size > 100) pendingQueue.poll()

            scope.launch {
                delay(2000)
                connect()
            }
        }
    }

    fun emitTyping(conversationId: String, isTyping: Boolean) {
        emit(
            "typing",
            JSONObject()
                .put("conversationId", conversationId)
                .put("isTyping", isTyping)
        )
    }

    private fun flushPending() {
        while (true) {
            val item = pendingQueue.poll() ?: break

            val payload = runCatching {
                JSONObject(item)
            }.getOrNull() ?: continue

            socket?.emit("send_message", payload)
        }
    }
}
