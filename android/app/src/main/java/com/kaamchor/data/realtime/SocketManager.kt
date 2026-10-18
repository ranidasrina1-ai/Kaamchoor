package com.kaamchor.data.realtime

import com.google.gson.Gson
import com.kaamchor.data.local.SessionStore
import com.kaamchor.data.remote.GsonProvider
import com.kaamchor.data.remote.MessageDto
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.net.URI

sealed interface RealtimeEvent {
    data class NewMessage(val message: MessageDto) : RealtimeEvent
    data class MessageSent(val message: MessageDto) : RealtimeEvent
    data class Typing(val groupId: Int, val userId: Int, val username: String, val isTyping: Boolean) : RealtimeEvent
    data class MessagesRead(val groupId: Int, val messageIds: List<Int>, val readBy: Int) : RealtimeEvent
    data class MessagesDelivered(val groupId: Int, val messageIds: List<Int>) : RealtimeEvent
    data class Notification(val title: String, val message: String, val type: String, val raw: JSONObject) : RealtimeEvent
    data class IncomingCall(val raw: JSONObject) : RealtimeEvent
    data class CallAnswered(val raw: JSONObject) : RealtimeEvent
    data class CallRejected(val raw: JSONObject) : RealtimeEvent
    data class CallEnded(val raw: JSONObject) : RealtimeEvent
    data class IceCandidate(val raw: JSONObject) : RealtimeEvent
    data class UserOffline(val userId: Int) : RealtimeEvent
    data object Connected : RealtimeEvent
    data object Disconnected : RealtimeEvent
}

class SocketManager(private val session: SessionStore) {

    private val gson: Gson = GsonProvider.gson
    private var socket: Socket? = null

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _events = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<RealtimeEvent> = _events

    val currentUserId: Int?
        get() = _selfUserId
    private var _selfUserId: Int? = null

    fun setSelfUserId(id: Int) { _selfUserId = id }

    @Synchronized
    fun connect() {
        if (socket?.connected() == true) return
        val token = session.accessTokenBlocking() ?: return
        val base = com.kaamchor.data.remote.normalizeBaseUrl(session.serverUrlBlocking())

        try {
            val options = IO.Options.builder()
                .setQuery("token=$token")
                .setReconnection(true)
                .setForceNew(true)
                .build()
            val s = IO.socket(URI.create(base), options)
            register(s)
            socket = s
            s.connect()
        } catch (e: Exception) {
            _connected.value = false
        }
    }

    @Synchronized
    fun disconnect() {
        socket?.let {
            it.off()
            it.disconnect()
        }
        socket = null
        _connected.value = false
    }

    fun emit(event: String, payload: JSONObject) {
        socket?.emit(event, payload)
    }

    private fun register(s: Socket) {
        s.on(Socket.EVENT_CONNECT) { _ ->
            _connected.value = true
            _events.tryEmit(RealtimeEvent.Connected)
        }
        s.on(Socket.EVENT_DISCONNECT) { _ ->
            _connected.value = false
            _events.tryEmit(RealtimeEvent.Disconnected)
        }
        s.on("new_message") { args -> emitMessage(args, isSent = false) }
        s.on("message_sent") { args -> emitMessage(args, isSent = true) }
        s.on("user_typing") { args ->
            val o = args.firstOrNull() as? JSONObject ?: return@on
            _events.tryEmit(
                RealtimeEvent.Typing(
                    groupId = o.optInt("group_id"),
                    userId = o.optInt("user_id"),
                    username = o.optString("username"),
                    isTyping = o.optBoolean("is_typing"),
                )
            )
        }
        s.on("messages_read") { args ->
            val o = args.firstOrNull() as? JSONObject ?: return@on
            _events.tryEmit(
                RealtimeEvent.MessagesRead(
                    groupId = o.optInt("group_id"),
                    messageIds = o.optJSONArray("message_ids").toIntList(),
                    readBy = o.optInt("read_by"),
                )
            )
        }
        s.on("messages_delivered") { args ->
            val o = args.firstOrNull() as? JSONObject ?: return@on
            _events.tryEmit(
                RealtimeEvent.MessagesDelivered(
                    groupId = o.optInt("group_id"),
                    messageIds = o.optJSONArray("message_ids").toIntList(),
                )
            )
        }
        s.on("notification") { args ->
            val o = args.firstOrNull() as? JSONObject ?: return@on
            _events.tryEmit(
                RealtimeEvent.Notification(
                    title = o.optString("title"),
                    message = o.optString("message"),
                    type = o.optString("type"),
                    raw = o,
                )
            )
        }
        s.on("incoming_call") { args -> raw(RealtimeEvent::IncomingCall, args) }
        s.on("call_answered") { args -> raw(RealtimeEvent::CallAnswered, args) }
        s.on("call_rejected") { args -> raw(RealtimeEvent::CallRejected, args) }
        s.on("call_ended") { args -> raw(RealtimeEvent::CallEnded, args) }
        s.on("ice_candidate") { args -> raw(RealtimeEvent::IceCandidate, args) }
        s.on("user_offline") { args ->
            val o = args.firstOrNull() as? JSONObject ?: return@on
            _events.tryEmit(RealtimeEvent.UserOffline(o.optInt("user_id")))
        }
    }

    private fun raw(factory: (JSONObject) -> RealtimeEvent, args: Array<out Any?>) {
        val o = args.firstOrNull() as? JSONObject ?: return
        _events.tryEmit(factory(o))
    }

    private fun emitMessage(args: Array<out Any?>, isSent: Boolean) {
        val o = args.firstOrNull() as? JSONObject ?: return
        val dto = gson.fromJson(o.toString(), MessageDto::class.java)
        _events.tryEmit(if (isSent) RealtimeEvent.MessageSent(dto) else RealtimeEvent.NewMessage(dto))
    }
}

private fun org.json.JSONArray?.toIntList(): List<Int> {
    if (this == null) return emptyList()
    val out = ArrayList<Int>(length())
    for (i in 0 until length()) out.add(optInt(i))
    return out
}
