package com.kaamchor.ui.screens.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kaamchor.Graph
import com.kaamchor.data.remote.CommentDto
import com.kaamchor.data.remote.PostDto
import com.kaamchor.ui.components.Avatar
import com.kaamchor.ui.theme.LikeRed
import com.kaamchor.util.relativeTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PostDetailState(
    val post: PostDto? = null,
    val comments: List<CommentDto> = emptyList(),
    val loading: Boolean = true,
    val sending: Boolean = false,
    val error: String? = null,
)

class PostDetailViewModel : ViewModel() {
    private val repo = Graph.repository
    private val _state = MutableStateFlow(PostDetailState())
    val state: StateFlow<PostDetailState> = _state

    fun load(postId: Int) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val post = repo.post(postId)
                val comments = repo.comments(postId)
                post to comments
            }.onSuccess { (post, comments) ->
                _state.update { it.copy(post = post, comments = comments, loading = false) }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun toggleLike() {
        val post = _state.value.post ?: return
        _state.update { st ->
            st.copy(post = post.copy(likedByMe = !post.likedByMe, likeCount = post.likeCount + if (post.likedByMe) -1 else 1))
        }
        viewModelScope.launch {
            runCatching { repo.toggleLike(post.id) }
                .onSuccess { res ->
                    _state.update { st -> st.copy(post = st.post?.copy(likedByMe = res.liked, likeCount = res.likeCount)) }
                }
        }
    }

    fun addComment(postId: Int, text: String) {
        if (text.isBlank()) return
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            runCatching { repo.addComment(postId, text.trim()) }
                .onSuccess { c ->
                    _state.update {
                        it.copy(
                            comments = it.comments + c,
                            post = it.post?.copy(commentCount = (it.post?.commentCount ?: 0) + 1),
                            sending = false,
                        )
                    }
                }
                .onFailure { _state.update { s -> s.copy(sending = false, error = it.message) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    postId: Int,
    onBack: () -> Unit,
    onOpenProfile: (Int) -> Unit,
) {
    val vm: PostDetailViewModel = viewModel()
    val state by vm.state.collectAsState()

    androidx.compose.runtime.LaunchedEffect(postId) { vm.load(postId) }

    var draft by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Post") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(500) },
                    placeholder = { Text("Add a comment…") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        vm.addComment(postId, draft)
                        draft = ""
                    }),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { vm.addComment(postId, draft); draft = "" },
                    enabled = draft.isNotBlank() && !state.sending,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        },
    ) { inner ->
        if (state.loading && state.post == null) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val post = state.post
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 12.dp),
        ) {
            if (post != null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(
                            user = post.author,
                            modifier = Modifier.clickable { onOpenProfile(post.author?.id ?: post.userId) },
                        )
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(post.author?.name ?: "Unknown", fontWeight = FontWeight.SemiBold)
                            Text(
                                "@${post.author?.username ?: "user"} · ${relativeTime(post.createdAt)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    AsyncImage(
                        model = post.photoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { vm.toggleLike() }) {
                            Icon(
                                imageVector = if (post.likedByMe) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (post.likedByMe) LikeRed else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("${post.likeCount} likes", style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (post.caption.isNotBlank()) {
                    item {
                        Text(
                            post.caption,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                item {
                    Text(
                        "Comments (${state.comments.size})",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            items(state.comments, key = { it.id }) { c ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Avatar(user = c.author, size = 34.dp, modifier = Modifier.clickable { onOpenProfile(c.userId) })
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(c.author?.name ?: "User", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                        Text(c.text, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            relativeTime(c.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.comments.isEmpty()) {
                item {
                    Text(
                        "No comments yet. Be the first!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            if (state.error != null) {
                item {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
