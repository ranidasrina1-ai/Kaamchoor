package com.kaamchor.ui.screens.chat

import android.Manifest
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.kaamchor.Graph
import com.kaamchor.data.realtime.RealtimeEvent
import com.kaamchor.data.remote.GroupDto
import com.kaamchor.data.remote.MessageDto
import com.kaamchor.ui.components.Avatar
import com.kaamchor.util.Session
import com.kaamchor.util.VoiceRecorder
import com.kaamchor.util.formatDuration
import com.kaamchor.util.formatMillis
import com.kaamchor.util.relativeTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatRoomState(
    val groupId: Int = 0,
    val group: GroupDto? = null,
    val messages: List<MessageDto> = emptyList(),
    val loading: Boolean = true,
    val sending: Boolean = false,
    val typingUser: String? = null,
    val myId: Int? = null,
    val error: String? = null,
)

class ChatRoomViewModel : ViewModel() {
    private val repo = Graph.repository
    private val _state = MutableStateFlow(ChatRoomState())
    val state: StateFlow<ChatRoomState> = _state
    private var observing = false

    fun start(groupId: Int) {
        if (_state.value.groupId == groupId && _state.value.group != null) return
        _state.value = ChatRoomState(groupId = groupId)
        observeRealtime()
        viewModelScope.launch {
            _state.update { it.copy(myId = Session.cachedUser()?.id) }
            runCatching {
                val group = repo.group(groupId)
                val msgs = repo.messages(groupId).messages
                group to msgs
            }.onSuccess { (group, msgs) ->
                _state.update { it.copy(group = group, messages = msgs, loading = false) }
                runCatching { repo.markAllRead(groupId) }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    private fun observeRealtime() {
        if (observing) return
        observing = true
        viewModelScope.launch {
            Graph.socket.events.collect { event ->
                when (event) {
                    is RealtimeEvent.NewMessage -> if (event.message.groupId == _state.value.groupId) {
                        upsert(event.message)
                        runCatching { repo.markAllRead(_state.value.groupId) }
                    }
                    is RealtimeEvent.MessageSent -> if (event.message.groupId == _state.value.groupId) {
                        upsert(event.message)
                    }
                    is RealtimeEvent.Typing -> if (event.groupId == _state.value.groupId && event.userId != _state.value.myId) {
                        _state.update { it.copy(typingUser = if (event.isTyping) event.username else null) }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun upsert(message: MessageDto) {
        _state.update { st ->
            if (st.messages.any { it.id == message.id }) st
            else st.copy(messages = (st.messages + message).sortedBy { it.id })
        }
    }

    fun sendText(text: String) {
        if (text.isBlank()) return
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            runCatching { repo.sendText(_state.value.groupId, text.trim()) }
                .onSuccess { m -> _state.update { it.copy(sending = false) }; upsert(m) }
                .onFailure { e -> _state.update { s -> s.copy(sending = false, error = e.message) } }
        }
    }

    fun sendMedia(uri: Uri, mediaType: String, durationMs: Long?, mimeOverride: String?, fileName: String?) {
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            runCatching {
                repo.sendMedia(_state.value.groupId, uri, mediaType, durationMs = durationMs, mimeOverride = mimeOverride, fileName = fileName)
            }.onSuccess { m -> _state.update { it.copy(sending = false) }; upsert(m) }
                .onFailure { e -> _state.update { s -> s.copy(sending = false, error = e.message) } }
        }
    }

    fun notifyTyping(isTyping: Boolean) {
        val groupId = _state.value.groupId
        if (groupId == 0) return
        val payload = org.json.JSONObject().apply {
            put("group_id", groupId)
            put("is_typing", isTyping)
        }
        Graph.socket.emit("typing", payload)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    groupId: Int,
    onBack: () -> Unit,
    onOpenProfile: (Int) -> Unit,
) {
    val vm: ChatRoomViewModel = viewModel(key = "chat_$groupId")
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }
    var attachOpen by remember { mutableStateOf(false) }
    var videoToPlay by remember { mutableStateOf<String?>(null) }

    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }

    val audioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* handled at press time */ }

    LaunchedEffect(groupId) {
        vm.start(groupId)
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.sendMedia(uri, "photo", null, null, null)
    }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.sendMedia(uri, "video", null, null, null)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    val other = state.group?.members?.firstOrNull { it.id != state.myId }
                    val title = if (state.group?.isGroup == true)
                        state.group?.name?.ifBlank { "Group" } ?: "Chat"
                    else other?.name ?: "Chat"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { other?.let { onOpenProfile(it.id) } },
                    ) {
                        if (state.group?.isGroup != true) {
                            Avatar(user = other, size = 36.dp)
                            Spacer(Modifier.width(10.dp))
                        }
                        Column {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            state.typingUser?.let {
                                Text(
                                    "$it is typing…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { startCall(context, state.groupId, false) }) {
                        Icon(Icons.Filled.Call, contentDescription = "Audio call")
                    }
                    IconButton(onClick = { startCall(context, state.groupId, true) }) {
                        Icon(Icons.Filled.Videocam, contentDescription = "Video call")
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .imePadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    IconButton(onClick = { attachOpen = true }) {
                        Icon(Icons.Filled.AttachFile, contentDescription = "Attach")
                    }
                    DropdownMenu(expanded = attachOpen, onDismissRequest = { attachOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Photo") },
                            leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                            onClick = {
                                attachOpen = false
                                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Video") },
                            leadingIcon = { Icon(Icons.Filled.Videocam, contentDescription = null) },
                            onClick = {
                                attachOpen = false
                                videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                            },
                        )
                    }
                }

                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        vm.notifyTyping(it.isNotBlank())
                    },
                    placeholder = { Text("Message…") },
                    modifier = Modifier.weight(1f),
                    maxLines = 5,
                )

                Spacer(Modifier.width(4.dp))

                if (draft.isBlank()) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (recording) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primaryContainer
                            )
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = press@{
                                        if (recorder.isRecording) return@press
                                        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.RECORD_AUDIO,
                                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                        if (!granted) {
                                            audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                                            return@press
                                        }
                                        recording = recorder.start()
                                        tryAwaitRelease()
                                        if (recorder.isRecording) {
                                            recording = false
                                            val result = recorder.stop()
                                            if (result != null) {
                                                vm.sendMedia(
                                                    result.first, "voice", result.second,
                                                    mimeOverride = "audio/mp4", fileName = "voice.m4a",
                                                )
                                            }
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Mic,
                            contentDescription = "Hold to record",
                            tint = if (recording) MaterialTheme.colorScheme.onError
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                } else {
                    IconButton(onClick = { vm.sendText(draft); draft = ""; vm.notifyTyping(false) }) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        },
    ) { inner ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.messages, key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    isMine = message.senderId == state.myId,
                    showSender = state.group?.isGroup == true,
                    onOpenProfile = onOpenProfile,
                    onPlayVideo = { videoToPlay = message.mediaUrl },
                )
            }
        }
    }

    videoToPlay?.let { url ->
        VideoPlayerDialog(url = url, onDismiss = { videoToPlay = null })
    }
}

@Composable
private fun MessageBubble(
    message: MessageDto,
    isMine: Boolean,
    showSender: Boolean,
    onOpenProfile: (Int) -> Unit,
    onPlayVideo: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isMine && showSender) {
            Avatar(user = message.sender, size = 28.dp, modifier = Modifier.clickable { onOpenProfile(message.senderId) })
            Spacer(Modifier.width(6.dp))
        }

        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomEnd = if (isMine) 4.dp else 18.dp,
                        bottomStart = if (isMine) 18.dp else 4.dp,
                    )
                )
                .background(
                    if (isMine) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                )
                .padding(if (message.mediaType == "text") 12.dp else 4.dp),
        ) {
            if (!isMine && showSender) {
                Text(
                    message.sender?.name ?: "User",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 6.dp, top = 4.dp),
                )
            }

            when (message.mediaType) {
                "photo" -> AsyncImage(
                    model = message.mediaUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(240.dp).height(240.dp).clip(RoundedCornerShape(14.dp)),
                )
                "video" -> Box(
                    modifier = Modifier.width(240.dp).height(160.dp).clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onPlayVideo),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = message.mediaUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.inverseOnSurface)
                    }
                    message.duration?.let {
                        Text(
                            formatDuration(it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                        )
                    }
                }
                "voice" -> VoiceBubble(url = message.mediaUrl.orEmpty(), durationSeconds = message.duration, isMine = isMine)
                else -> Unit
            }

            if (message.text.isNotBlank()) {
                Text(
                    message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            Text(
                relativeTime(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun VoiceBubble(url: String, durationSeconds: Double?, isMine: Boolean) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {
        onDispose {
            player?.release()
            player = null
        }
    }

    Row(
        modifier = Modifier.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = {
            val current = player
            if (playing && current != null) {
                current.pause()
                playing = false
            } else {
                val p = current ?: MediaPlayer().apply {
                    setDataSource(context, Uri.parse(url))
                    setOnCompletionListener { playing = false; progress = 0f }
                    prepare()
                }.also { player = it }
                p.start()
                playing = true
            }
        }) {
            Icon(
                imageVector = if (playing) Icons.AutoMirrored.Filled.VolumeUp else Icons.Filled.PlayArrow,
                contentDescription = "Play voice note",
            )
        }
        Text(formatDuration(durationSeconds), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
    }
}

@Composable
private fun VideoPlayerDialog(url: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(url)); prepare() } }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(14.dp)),
            ) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = true
                            this.player = player
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
    )
}

private fun startCall(context: Context, groupId: Int, video: Boolean) {
    val intent = android.content.Intent(context, com.kaamchor.ui.call.CallActivity::class.java).apply {
        putExtra("outgoing_group_id", groupId)
        putExtra("call_type", if (video) "video" else "audio")
    }
    context.startActivity(intent)
}
