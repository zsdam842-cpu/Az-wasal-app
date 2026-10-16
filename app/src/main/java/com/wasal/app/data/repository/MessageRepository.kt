package com.wasal.app.data.repository

import android.content.Context
import com.wasal.app.data.local.AuthDataStore
import com.wasal.app.data.model.Message
import com.wasal.app.data.model.MessageStatus
import com.wasal.app.data.network.CoreApiClient
import com.wasal.app.data.network.ServerConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class MessageRepository(
    private val authDataStore: AuthDataStore
) {
    private val apiClient = AuthenticatedApiClient(authDataStore)

    private val preferences by lazy {
        authDataStore.appContext.getSharedPreferences(
            "wasal_message_cache",
            Context.MODE_PRIVATE
        )
    }

    suspend fun getMessages(
        conversationId: String,
        accessToken: String,
        currentUserId: String,
        limit: Int = 50
    ): Result<List<Message>> {
        if (conversationId.isBlank()) {
            return Result.failure(
                IllegalArgumentException("معرّف المحادثة غير موجود.")
            )
        }

        if (currentUserId.isBlank()) {
            return Result.failure(
                IllegalArgumentException("معرّف المستخدم غير موجود.")
            )
        }

        if (accessToken.isBlank()) {
            return cachedOrFailure(
                conversationId,
                currentUserId,
                IOException("رمز الدخول غير موجود.")
            )
        }

        val safeLimit = limit.coerceIn(1, 100)
        val path =
            "${ServerConfig.MESSAGES_ENDPOINT}/conversation/" +
                "$conversationId?limit=$safeLimit"

        val responseResult = apiClient.execute { token ->
            CoreApiClient.get(path, token)
        }

        val response = responseResult.getOrElse { error ->
            return cachedOrFailure(
                conversationId,
                currentUserId,
                error
            )
        }

        if (!response.isSuccessful) {
            val error = runCatching {
                JSONObject(response.body).optString("error")
            }.getOrDefault("")

            val exception = IOException(
                when (response.code) {
                    401 -> "انتهت جلسة الدخول."
                    403 -> "ليست لديك صلاحية عرض هذه المحادثة."
                    else -> error.ifBlank {
                        "تعذر تحميل الرسائل. رمز الاستجابة: ${response.code}"
                    }
                }
            )

            return cachedOrFailure(
                conversationId,
                currentUserId,
                exception
            )
        }

        return try {
            val root = JSONObject(response.body)
            val array = root.optJSONArray("messages")
                ?: root.optJSONObject("data")
                    ?.optJSONArray("messages")
                ?: throw IOException("لم يرجع الخادم قائمة الرسائل.")

            val messages = mutableListOf<Message>()

            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()

                if (id.isBlank()) continue

                val senderId = item.optString("senderId")
                val content = if (item.isNull("content")) {
                    ""
                } else {
                    item.optString("content", "")
                }

                val createdAt = item.optString("createdAt", "")
                val time = formatTime(createdAt)

                val status = parseStatus(item)

                messages.add(
                    Message(
                        id = id,
                        content = content,
                        isMine = senderId == currentUserId,
                        time = time,
                        type = normalizeType(
                            item.optString("type", "text")
                        ),
                        status = status
                    )
                )
            }

            saveCache(conversationId, currentUserId, messages)
            Result.success(messages)
        } catch (error: Exception) {
            cachedOrFailure(conversationId, currentUserId, error)
        }
    }

    suspend fun sendTextMessage(
        conversationId: String,
        content: String,
        accessToken: String,
        currentUserId: String
    ): Result<Message> {
        val cleanContent = content.trim()

        if (conversationId.isBlank() || accessToken.isBlank()) {
            return Result.failure(
                IllegalArgumentException(
                    "معرّف المحادثة أو رمز الدخول غير موجود."
                )
            )
        }

        if (cleanContent.isBlank()) {
            return Result.failure(
                IllegalArgumentException("لا يمكن إرسال رسالة فارغة.")
            )
        }

        val payload = JSONObject()
            .put("conversationId", conversationId)
            .put("type", "text")
            .put("content", cleanContent)
            .toString()

        val responseResult = apiClient.execute { token ->
            CoreApiClient.post(
                ServerConfig.MESSAGES_ENDPOINT,
                payload,
                token
            )
        }

        val response = responseResult.getOrElse {
            return Result.failure(it)
        }

        if (!response.isSuccessful) {
            val error = runCatching {
                JSONObject(response.body).optString("error")
            }.getOrDefault("")

            return Result.failure(
                IOException(
                    when (response.code) {
                        401 -> "انتهت جلسة الدخول."
                        403 -> "لا يمكنك الإرسال إلى هذه المحادثة."
                        else -> error.ifBlank {
                            "تعذر إرسال الرسالة. رمز الاستجابة: ${response.code}"
                        }
                    }
                )
            )
        }

        val result = parseSentMessage(
            responseBody = response.body,
            fallbackContent = cleanContent,
            fallbackType = "text",
            currentUserId = currentUserId
        )

        result.onSuccess { message ->
            saveSentMessage(conversationId, currentUserId, message)
        }

        return result
    }

    suspend fun sendMediaMessage(
        conversationId: String,
        media: UploadedMedia,
        accessToken: String,
        currentUserId: String
    ): Result<Message> {
        if (conversationId.isBlank() || accessToken.isBlank()) {
            return Result.failure(
                IllegalArgumentException(
                    "معرّف المحادثة أو رمز الدخول غير موجود."
                )
            )
        }

        if (media.url.isBlank()) {
            return Result.failure(
                IllegalArgumentException("رابط المرفق غير موجود.")
            )
        }

        val type = when {
            media.mimeType.startsWith("image/") -> "image"
            media.mimeType.startsWith("video/") -> "video"
            media.mimeType.startsWith("audio/") -> "voice"
            else -> "file"
        }

        val payload = JSONObject()
            .put("conversationId", conversationId)
            .put("type", type)
            .put("content", media.url)
            .toString()

        val responseResult = apiClient.execute { token ->
            CoreApiClient.post(
                ServerConfig.MESSAGES_ENDPOINT,
                payload,
                token
            )
        }

        val response = responseResult.getOrElse {
            return Result.failure(it)
        }

        if (!response.isSuccessful) {
            val error = runCatching {
                JSONObject(response.body).optString("error")
            }.getOrDefault("")

            return Result.failure(
                IOException(
                    when (response.code) {
                        401 -> "انتهت جلسة الدخول."
                        403 -> "لا يمكنك إرسال مرفق إلى هذه المحادثة."
                        else -> error.ifBlank {
                            "تعذر إرسال المرفق. رمز الاستجابة: ${response.code}"
                        }
                    }
                )
            )
        }

        val result = parseSentMessage(
            responseBody = response.body,
            fallbackContent = media.url,
            fallbackType = type,
            currentUserId = currentUserId
        )

        result.onSuccess { message ->
            saveSentMessage(conversationId, currentUserId, message)
        }

        return result
    }

    fun parseLiveMessage(payload: JSONObject, currentUserId: String): Message? {
        val id = payload.optString("id").trim()

        if (id.isBlank()) return null

        val senderId = payload.optString("senderId")
        val content = if (payload.isNull("content")) {
            ""
        } else {
            payload.optString("content", "")
        }

        val deleted = payload.optBoolean("deletedForEveryone", false)

        return Message(
            id = id,
            content = if (deleted) "" else content,
            isMine = senderId == currentUserId,
            time = formatTime(payload.optString("createdAt", "")),
            type = normalizeType(payload.optString("type", "text")),
            status = parseStatus(payload),
            senderCode = payload.optString("senderCode", ""),
            deletedForEveryone = deleted
        )
    }

    suspend fun deleteMessage(
        messageId: String,
        accessToken: String,
        forEveryone: Boolean
    ): Result<Unit> {
        if (accessToken.isBlank()) {
            return Result.failure(
                IllegalArgumentException("رمز الدخول غير موجود.")
            )
        }

        val scope = if (forEveryone) "everyone" else "me"
        val path = "${ServerConfig.MESSAGES_ENDPOINT}/$messageId?scope=$scope"

        val responseResult = apiClient.execute { token ->
            CoreApiClient.delete(path, token)
        }

        val response = responseResult.getOrElse {
            return Result.failure(it)
        }

        if (!response.isSuccessful) {
            val error = runCatching {
                JSONObject(response.body).optString("error")
            }.getOrDefault("")

            return Result.failure(
                IOException(
                    when (response.code) {
                        401 -> "انتهت جلسة الدخول."
                        403 -> "لا يمكنك حذف هذه الرسالة لدى الجميع."
                        404 -> "الرسالة غير موجودة."
                        else -> error.ifBlank {
                            "تعذر حذف الرسالة. رمز الاستجابة: ${response.code}"
                        }
                    }
                )
            )
        }

        return Result.success(Unit)
    }

    private fun parseStatus(item: JSONObject): MessageStatus {
        val rawStatus = item.optString("status")
            .ifBlank { item.optString("deliveryStatus") }
            .uppercase()

        if (item.has("readAt") && !item.isNull("readAt")) {
            return MessageStatus.READ
        }

        if (item.has("deliveredAt") && !item.isNull("deliveredAt")) {
            return MessageStatus.DELIVERED
        }

        return when (rawStatus) {
            "READ", "SEEN" -> MessageStatus.READ
            "DELIVERED" -> MessageStatus.DELIVERED
            "FAILED", "ERROR" -> MessageStatus.FAILED
            "SENDING", "PENDING" -> MessageStatus.SENDING
            else -> MessageStatus.SENT
        }
    }

    private fun normalizeType(type: String): String {
        return when (type.lowercase()) {
            "voice", "audio" -> "audio"
            "image" -> "image"
            "video" -> "video"
            "file", "document" -> "file"
            else -> "text"
        }
    }

    private fun formatTime(createdAt: String): String {
        return createdAt
            .replace("T", " ")
            .substringBefore(".")
            .removeSuffix("Z")
    }

    private fun cacheKey(
        conversationId: String,
        currentUserId: String
    ): String {
        return "messages_${currentUserId}_${conversationId}"
    }

    private fun saveCache(
        conversationId: String,
        currentUserId: String,
        messages: List<Message>
    ) {
        val array = JSONArray()

        messages.forEach { message ->
            array.put(
                JSONObject()
                    .put("id", message.id)
                    .put("content", message.content)
                    .put("isMine", message.isMine)
                    .put("time", message.time)
                    .put("type", message.type)
                    .put("status", message.status.name)
            )
        }

        preferences.edit()
            .putString(
                cacheKey(conversationId, currentUserId),
                array.toString()
            )
            .apply()
    }

    private fun readCache(
        conversationId: String,
        currentUserId: String
    ): List<Message>? {
        val json = preferences.getString(
            cacheKey(conversationId, currentUserId),
            null
        ) ?: return null

        return try {
            val array = JSONArray(json)
            val messages = mutableListOf<Message>()

            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()

                if (id.isBlank()) continue

                val status = runCatching {
                    MessageStatus.valueOf(
                        item.optString("status", "SENT")
                    )
                }.getOrDefault(MessageStatus.SENT)

                messages.add(
                    Message(
                        id = id,
                        content = item.optString("content", ""),
                        isMine = item.optBoolean("isMine", false),
                        time = item.optString("time", ""),
                        type = normalizeType(
                            item.optString("type", "text")
                        ),
                        status = status
                    )
                )
            }

            messages
        } catch (_: Exception) {
            null
        }
    }

    private fun cachedOrFailure(
        conversationId: String,
        currentUserId: String,
        error: Throwable
    ): Result<List<Message>> {
        val cached = readCache(conversationId, currentUserId)

        return if (cached != null) {
            Result.success(cached)
        } else {
            Result.failure(error)
        }
    }

    private fun saveSentMessage(
        conversationId: String,
        currentUserId: String,
        message: Message
    ) {
        val cached = readCache(conversationId, currentUserId).orEmpty()

        if (cached.none { it.id == message.id }) {
            saveCache(
                conversationId,
                currentUserId,
                cached + message
            )
        }
    }

    private fun parseSentMessage(
        responseBody: String,
        fallbackContent: String,
        fallbackType: String,
        currentUserId: String
    ): Result<Message> {
        return try {
            val root = JSONObject(responseBody)
            val item = root.optJSONObject("message")
                ?: root.optJSONObject("data")
                    ?.optJSONObject("message")
                ?: throw IOException("لم يرجع الخادم بيانات الرسالة.")

            val id = item.optString("id")

            if (id.isBlank()) {
                throw IOException("لم يرجع الخادم معرّف الرسالة.")
            }

            val status = parseStatus(item)

            Result.success(
                Message(
                    id = id,
                    content = item.optString("content", fallbackContent),
                    isMine = item.optString("senderId") == currentUserId,
                    time = formatTime(item.optString("createdAt", "")),
                    type = normalizeType(
                        item.optString("type", fallbackType)
                    ),
                    status = status
                )
            )
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
}
