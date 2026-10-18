package com.kaamchor.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaamchor.Graph
import com.kaamchor.data.realtime.RealtimeEvent
import com.kaamchor.data.remote.GroupDto
import com.kaamchor.ui.components.Avatar
import com.kaamchor.ui.components.GlassSurface
import com.kaamchor.ui.components.asHazeSource
import com.kaamchor.ui.theme.OnlineGreen
import com.kaamchor.util.Session
import com.kaamchor.util.relativeTime
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatListState(
    val groups: List<GroupDto> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val myId: Int? = null,
)

class ChatListViewModel : ViewModel() {
    private val repo = Graph.repository
    private val _state = MutableStateFlow(ChatListState())
    val state: StateFlow<ChatListState> = _state
    private var myId: Int? = null

    init {
        refresh()
        observeRealtime()
    }

    fun refresh() {
        viewModelScope.launch {
            myId = Session.cachedUser()?.id
            _state.update { it.copy(loading = it.groups.isEmpty(), myId = myId) }
            runCatching { repo.groups() }
                .onSuccess { g -> _state.update { it.copy(groups = g, loading = false, error = null) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    private fun observeRealtime() {
        viewModelScope.launch {
            Graph.socket.events.collect { event ->
                when (event) {
                    is RealtimeEvent.NewMessage -> onIncoming(event.message.groupId, event.message.senderId)
                    is RealtimeEvent.MessageSent -> onIncoming(event.message.groupId, null)
                    else -> Unit
                }
            }
        }
    }

    private fun onIncoming(groupId: Int, senderId: Int?) {
        _state.update { st ->
            val groups = st.groups.map { g ->
                if (g.id != groupId) g
                else if (senderId != null && senderId != myId) g.copy(unreadCount = g.unreadCount + 1)
                else g
            }.sortedByDescending { it.id }
            st.copy(groups = groups)
        }
        // Pull fresh metadata (last message preview) quietly.
        viewModelScope.launch {
            runCatching { repo.groups() }.onSuccess { g -> _state.update { it.copy(groups = g) } }
        }
    }
}

@Composable
fun ChatListScreen(
    inner: PaddingValues,
    hazeState: HazeState,
    onOpenChat: (Int) -> Unit,
    onNewChat: () -> Unit,
) {
    val vm: ChatListViewModel = viewModel()
    val state by vm.state.collectAsState()
    val myId = state.myId

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().asHazeSource(hazeState),
            contentPadding = PaddingValues(
                top = 76.dp,
                bottom = inner.calculateBottomPadding() + 16.dp,
                start = 12.dp,
                end = 12.dp,
            ),
        ) {
            if (state.loading && state.groups.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { CircularProgressIndicator() } }
            }
            if (state.groups.isEmpty() && !state.loading) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("No chats yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tap the + button to start a conversation.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            items(state.groups, key = { it.id }) { group ->
                ChatRow(group = group, myId = myId, onClick = { onOpenChat(group.id) })
            }
        }

        GlassSurface(
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.large,
            containerAlpha = 0.6f,
            padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Chats", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                androidx.compose.material3.IconButton(onClick = onNewChat) {
                    Icon(Icons.Filled.Add, contentDescription = "New chat")
                }
            }
        }
    }
}

@Composable
private fun ChatRow(group: GroupDto, myId: Int?, onClick: () -> Unit) {
    val other = group.members.firstOrNull { it.id != myId }
    val title = if (group.isGroup) (group.name.ifBlank { "Group" }) else (other?.name ?: "Chat")
    val preview = group.lastMessage?.let { m ->
        when (m.mediaType) {
            "photo" -> "📷 Photo"
            "video" -> "🎥 Video"
            "voice" -> "🎤 Voice note"
            else -> m.text
        }
    } ?: "No messages yet"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (group.isGroup) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        } else {
            Avatar(user = other, size = 48.dp)
        }

        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
            Text(
                preview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            group.lastMessage?.createdAt?.let {
                Text(
                    relativeTime(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (group.unreadCount > 0) {
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        group.unreadCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}
