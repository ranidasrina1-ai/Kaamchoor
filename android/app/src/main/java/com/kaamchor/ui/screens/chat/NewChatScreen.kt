package com.kaamchor.ui.screens.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaamchor.Graph
import com.kaamchor.data.remote.UserDto
import com.kaamchor.ui.components.Avatar
import com.kaamchor.util.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip

data class NewChatState(
    val users: List<UserDto> = emptyList(),
    val selected: Set<Int> = emptySet(),
    val query: String = "",
    val groupName: String = "",
    val loading: Boolean = true,
    val creating: Boolean = false,
    val error: String? = null,
)

class NewChatViewModel : ViewModel() {
    private val repo = Graph.repository
    private val _state = MutableStateFlow(NewChatState())
    val state: StateFlow<NewChatState> = _state
    private var myId: Int? = null

    init {
        viewModelScope.launch {
            myId = Session.cachedUser()?.id
            runCatching { repo.users() }
                .onSuccess { u ->
                    _state.update {
                        it.copy(users = u.filter { user -> user.id != myId }, loading = false)
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun onQuery(v: String) = _state.update { it.copy(query = v) }
    fun onGroupName(v: String) = _state.update { it.copy(groupName = v) }

    fun toggle(id: Int) = _state.update {
        val sel = it.selected.toMutableSet()
        if (!sel.add(id)) sel.remove(id)
        it.copy(selected = sel, error = null)
    }

    fun create(onCreated: (Int) -> Unit) {
        val s = _state.value
        if (s.selected.isEmpty() || s.creating) return
        _state.update { it.copy(creating = true, error = null) }
        val name = if (s.selected.size > 1) s.groupName.trim().ifBlank { null } else null
        viewModelScope.launch {
            runCatching { repo.createGroup(name, s.selected.toList()) }
                .onSuccess { g -> _state.update { it.copy(creating = false) }; onCreated(g.id) }
                .onFailure { e -> _state.update { st -> st.copy(creating = false, error = e.message) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatScreen(onBack: () -> Unit, onCreated: (Int) -> Unit) {
    val vm: NewChatViewModel = viewModel()
    val state by vm.state.collectAsState()
    val isGroup = state.selected.size > 1

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("New chat") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.selected.isNotEmpty()) {
                        IconButton(
                            onClick = { vm.create(onCreated) },
                            enabled = !state.creating,
                        ) {
                            if (state.creating) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Check, contentDescription = "Create")
                            }
                        }
                    }
                },
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::onQuery,
                label = { Text("Search people") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (isGroup) {
                OutlinedTextField(
                    value = state.groupName,
                    onValueChange = vm::onGroupName,
                    label = { Text("Group name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (state.error != null) {
                Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Column
            }

            val filtered = state.users.filter {
                state.query.isBlank() || it.name.contains(state.query, ignoreCase = true) ||
                    it.username.contains(state.query, ignoreCase = true)
            }

            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(filtered, key = { it.id }) { user ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.toggle(user.id) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = state.selected.contains(user.id), onCheckedChange = { vm.toggle(user.id) })
                        Avatar(user = user, size = 40.dp, modifier = Modifier.padding(start = 4.dp))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(user.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "@${user.username}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
