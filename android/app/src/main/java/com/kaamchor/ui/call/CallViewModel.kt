package com.kaamchor.ui.call

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaamchor.Graph
import com.kaamchor.data.realtime.RealtimeEvent
import com.kaamchor.data.remote.UserDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.SurfaceViewRenderer

enum class CallPhase { RINGING, CONNECTING, CONNECTED, ENDED }

data class Participant(val userId: Int, val name: String)

data class CallUiState(
    val phase: CallPhase = CallPhase.RINGING,
    val callType: String = "audio",
    val isGroup: Boolean = false,
    val title: String = "",
    val participants: List<Participant> = emptyList(),
    val remoteUserIds: Set<Int> = emptySet(),
    val connectedUserIds: Set<Int> = emptySet(),
    val micOn: Boolean = true,
    val cameraOn: Boolean = true,
    val isCaller: Boolean = true,
    val error: String? = null,
)

class CallViewModel(app: Application) : AndroidViewModel(app) {

    val engine = WebRtcEngine(app.applicationContext)

    private val _state = MutableStateFlow(CallUiState())
    val state: StateFlow<CallUiState> = _state

    var localRenderer: SurfaceViewRenderer? = null
        private set
    var remoteRenderer: SurfaceViewRenderer? = null
        private set

    private var callId: Int? = null
    private var groupId: Int? = null
    private var selfId: Int? = null
    private var pendingOffer: JSONObject? = null
    private var ringingUserId: Int? = null

    fun bindRenderers(local: SurfaceViewRenderer, remote: SurfaceViewRenderer) {
        localRenderer = local
        remoteRenderer = remote
        engine.localRenderer = local
        engine.remoteRenderer = remote
    }

    // ── Start outgoing call from a chat ──────────────────────────────────
    fun startOutgoing(groupId: Int, callType: String) {
        this.groupId = groupId
        _state.update { it.copy(callType = callType, isCaller = true, phase = CallPhase.RINGING) }
        viewModelScope.launch {
            selfId = com.kaamchor.util.Session.cachedUser()?.id
            runCatching { prepareEngine(callType == "video") }
            runCatching {
                val members = Graph.repository.members(groupId).filter { it.id != selfId }
                val isGroup = members.size > 1
                val call = Graph.repository.initiateCall(if (isGroup) groupId else null, callType)
                callId = call.id
                Triple(members, isGroup, call.id)
            }.onSuccess { (members, isGroup, id) ->
                _state.update {
                    it.copy(
                        isGroup = isGroup,
                        title = if (isGroup) "Group call" else members.firstOrNull()?.name ?: "Call",
                        participants = members.map { m -> Participant(m.id, m.name) },
                        remoteUserIds = members.map { m -> m.id }.toSet(),
                    )
                }
                observeSignaling()
                members.forEach { member ->
                    engine.createOffer(member.id) { offer ->
                        val payload = JSONObject().apply {
                            put("to_user_id", member.id)
                            put("offer", offer)
                            put("call_id", id)
                            put("call_type", callType)
                        }
                        Graph.socket.emit("call_offer", payload)
                    }
                }
                engine.onIceCandidate = { target, candidate ->
                    val payload = JSONObject().apply {
                        put("to_user_id", target)
                        put("candidate", candidate)
                    }
                    Graph.socket.emit("ice_candidate", payload)
                }
            }.onFailure { e ->
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    // ── Accept an incoming call payload ──────────────────────────────────
    fun onIncoming(raw: JSONObject) {
        _state.update {
            it.copy(
                callType = raw.optString("call_type", "audio"),
                isCaller = false,
                isGroup = raw.optBoolean("video") || raw.optString("group_id") != "",
                title = raw.optString("from_username", "Incoming call"),
                phase = CallPhase.RINGING,
            )
        }
        callId = raw.optInt("call_id").takeIf { it != 0 }
        val fromUser = raw.optInt("from_user_id")
        ringingUserId = fromUser
        _state.update {
            it.copy(
                remoteUserIds = setOf(fromUser),
                participants = listOf(Participant(fromUser, raw.optString("from_username", "Caller"))),
            )
        }
        pendingOffer = raw.optJSONObject("offer")
        raw.optString("group_id").takeIf { it.isNotBlank() }?.toIntOrNull()?.let { groupId = it }
    }

    fun accept() {
        viewModelScope.launch {
            selfId = com.kaamchor.util.Session.cachedUser()?.id
            val offer = pendingOffer ?: return@launch
            val from = ringingUserId ?: return@launch
            runCatching { prepareEngine(_state.value.callType == "video") }
            observeSignaling()
            callId?.let { runCatching { Graph.repository.answerCall(it) } }
            engine.setRemoteDescription(from, offer, isOffer = true)
            engine.createAnswer(from) { answer ->
                val payload = JSONObject().apply {
                    put("to_user_id", from)
                    put("answer", answer)
                    put("call_id", callId)
                }
                Graph.socket.emit("call_answer", payload)
            }
            engine.onIceCandidate = { target, candidate ->
                val payload = JSONObject().apply {
                    put("to_user_id", target)
                    put("candidate", candidate)
                }
                Graph.socket.emit("ice_candidate", payload)
            }
            _state.update { it.copy(phase = CallPhase.CONNECTING) }
        }
    }

    fun decline() {
        val payload = JSONObject().apply {
            put("to_user_id", ringingUserId)
            put("call_id", callId)
        }
        Graph.socket.emit("call_reject", payload)
        callId?.let { id -> viewModelScope.launch { runCatching { Graph.repository.rejectCall(id) } } }
        end()
    }

    fun end() {
        val to = ringingUserId ?: _state.value.remoteUserIds.firstOrNull()
        val payload = JSONObject().apply {
            put("call_id", callId)
            if (_state.value.isGroup) put("group_id", groupId) else put("to_user_id", to)
        }
        Graph.socket.emit("call_end", payload)
        callId?.let { id -> viewModelScope.launch { runCatching { Graph.repository.endCall(id) } } }
        _state.update { it.copy(phase = CallPhase.ENDED) }
    }

    fun toggleMic() {
        val on = !_state.value.micOn
        engine.setMicEnabled(on)
        _state.update { it.copy(micOn = on) }
    }

    fun toggleCamera() {
        val on = !_state.value.cameraOn
        engine.setCameraEnabled(on)
        _state.update { it.copy(cameraOn = on) }
    }

    fun switchCamera() = engine.switchCamera()

    private var signalingStarted = false

    private fun observeSignaling() {
        if (signalingStarted) return
        signalingStarted = true
        viewModelScope.launch {
            Graph.socket.events.collect { event ->
                when (event) {
                    is RealtimeEvent.CallAnswered -> {
                        val from = event.raw.optInt("from_user_id")
                        val answer = event.raw.optJSONObject("answer")
                        if (answer != null) {
                            engine.setRemoteDescription(from, answer, isOffer = false)
                        }
                        _state.update {
                            it.copy(
                                phase = CallPhase.CONNECTED,
                                connectedUserIds = it.connectedUserIds + from,
                            )
                        }
                    }
                    is RealtimeEvent.IceCandidate -> {
                        val from = event.raw.optInt("from_user_id")
                        event.raw.optJSONObject("candidate")?.let { engine.addRemoteIceCandidate(from, it) }
                    }
                    is RealtimeEvent.CallRejected -> {
                        val from = event.raw.optInt("from_user_id")
                        engine.removePeer(from)
                        _state.update { st ->
                            val remaining = st.remoteUserIds - from
                            if (remaining.isEmpty()) st.copy(phase = CallPhase.ENDED)
                            else st.copy(remoteUserIds = remaining, participants = st.participants.filterNot { p -> p.userId == from })
                        }
                    }
                    is RealtimeEvent.CallEnded -> {
                        val from = event.raw.optInt("from_user_id")
                        engine.removePeer(from)
                        _state.update { st ->
                            val remaining = st.remoteUserIds - from
                            if (remaining.isEmpty()) st.copy(phase = CallPhase.ENDED)
                            else st.copy(remoteUserIds = remaining)
                        }
                    }
                    is RealtimeEvent.IncomingCall -> {
                        // A new participant joined a group call: we offer to them.
                        val from = event.raw.optInt("from_user_id")
                        if (!_state.value.isCaller && from != 0) {
                            _state.update {
                                it.copy(
                                    remoteUserIds = it.remoteUserIds + from,
                                    participants = it.participants + Participant(from, event.raw.optString("from_username", "Guest")),
                                )
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private suspend fun prepareEngine(withVideo: Boolean) {
        val ice = Graph.repository.iceServers().iceServers.map {
            mapOf<String, Any?>("urls" to it.urls, "username" to it.username, "credential" to it.credential)
        }
        engine.initialize(ice)
        engine.createLocalMedia(withVideo)
    }

    override fun onCleared() {
        super.onCleared()
        runCatching { engine.dispose() }
    }
}
