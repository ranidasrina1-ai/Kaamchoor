package com.kaamchor.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kaamchor.Graph
import com.kaamchor.data.remote.PostDto
import com.kaamchor.data.remote.UserDto
import com.kaamchor.ui.components.Avatar
import com.kaamchor.ui.components.GlassSurface
import com.kaamchor.ui.components.asHazeSource
import com.kaamchor.ui.components.rememberGlassState
import com.kaamchor.util.Session
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: UserDto? = null,
    val posts: List<PostDto> = emptyList(),
    val loading: Boolean = true,
    val isMe: Boolean = false,
    val startingChat: Boolean = false,
    val error: String? = null,
)

class ProfileViewModel : ViewModel() {
    private val repo = Graph.repository
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state
    private var myId: Int? = null

    fun load(userId: Int) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            myId = Session.cachedUser()?.id
            val target = if (userId > 0) userId else myId ?: 0
            runCatching {
                val user = if (userId > 0) repo.profile(target) else repo.myProfile()
                val posts = repo.userPosts(target).posts
                (user to posts) to target
            }.onSuccess { (pair, id) ->
                val (user, posts) = pair
                _state.update {
                    it.copy(user = user, posts = posts, loading = false, isMe = id == myId)
                }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun startChat(userId: Int, name: String, onReady: (Int, String) -> Unit) {
        _state.update { it.copy(startingChat = true) }
        viewModelScope.launch {
            runCatching { repo.createGroup(null, listOf(userId)) }
                .onSuccess { g ->
                    _state.update { it.copy(startingChat = false) }
                    onReady(g.id, name)
                }
                .onFailure { _state.update { s -> s.copy(startingChat = false, error = it.message) } }
        }
    }
}

@Composable
fun ProfileScreen(
    userId: Int,
    onEdit: () -> Unit,
    onOpenPost: (Int) -> Unit,
    onMessage: (Int, String) -> Unit,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    inner: PaddingValues = PaddingValues(),
    hazeState: HazeState? = null,
) {
    val vm: ProfileViewModel = viewModel(key = "profile_$userId")
    val state by vm.state.collectAsState()
    val haze = hazeState ?: rememberGlassState()

    LaunchedEffect(userId) { vm.load(userId) }

    Box(Modifier.fillMaxSize()) {
        if (state.loading && state.user == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Box
        }

        val user = state.user

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().asHazeSource(haze),
            contentPadding = PaddingValues(
                top = 88.dp,
                bottom = inner.calculateBottomPadding() + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Avatar(user = user, size = 96.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        user?.name ?: "Profile",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "@${user?.username ?: ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!user?.bio.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            user!!.bio!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.isMe) {
                            Button(onClick = onEdit) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Edit profile")
                            }
                        } else if (user != null) {
                            Button(
                                onClick = { vm.startChat(user.id, user.name, onMessage) },
                                enabled = !state.startingChat,
                            ) {
                                Text("Message")
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${state.posts.size} posts",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.error != null) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            items(state.posts, key = { it.id }) { post ->
                AsyncImage(
                    model = post.photoUrl,
                    contentDescription = post.caption,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(0.dp))
                        .clickable { onOpenPost(post.id) },
                )
            }
        }

        // Glass top bar
        GlassSurface(
            hazeState = haze,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.large,
            containerAlpha = 0.6f,
            padding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    user?.name ?: "Profile",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                if (state.isMe && onSettings != null) {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            }
        }
    }
}
