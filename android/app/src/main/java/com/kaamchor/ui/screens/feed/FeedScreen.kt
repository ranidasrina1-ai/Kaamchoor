package com.kaamchor.ui.screens.feed

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.kaamchor.ui.components.Avatar
import com.kaamchor.ui.components.GlassSurface
import com.kaamchor.ui.components.TranslucentCard
import com.kaamchor.ui.components.asHazeSource
import com.kaamchor.ui.theme.LikeRed
import com.kaamchor.ui.theme.WordmarkStyle
import com.kaamchor.util.relativeTime
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedUiState(
    val posts: List<PostDto> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
    val nextCursor: Int? = null,
)

class FeedViewModel : ViewModel() {
    private val repo = Graph.repository
    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state

    init { refresh() }

    fun refresh() {
        _state.update { it.copy(refreshing = it.posts.isNotEmpty(), loading = it.posts.isEmpty(), error = null) }
        viewModelScope.launch {
            runCatching { repo.feed() }
                .onSuccess { res ->
                    _state.update {
                        it.copy(
                            posts = res.posts,
                            nextCursor = res.nextCursor,
                            hasMore = res.hasMore,
                            loading = false,
                            refreshing = false,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, refreshing = false, error = e.message) }
                }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (!s.hasMore || s.loading || s.refreshing) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            runCatching { repo.feed(cursor = s.nextCursor) }
                .onSuccess { res ->
                    _state.update {
                        it.copy(
                            posts = it.posts + res.posts.filterNot { p -> it.posts.any { e -> e.id == p.id } },
                            nextCursor = res.nextCursor,
                            hasMore = res.hasMore,
                            loading = false,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun toggleLike(postId: Int) {
        val original = _state.value.posts
        _state.update { st ->
            st.copy(posts = st.posts.map { p ->
                if (p.id == postId) p.copy(
                    likedByMe = !p.likedByMe,
                    likeCount = p.likeCount + if (p.likedByMe) -1 else 1,
                ) else p
            })
        }
        viewModelScope.launch {
            runCatching { repo.toggleLike(postId) }
                .onSuccess { res ->
                    _state.update { st ->
                        st.copy(posts = st.posts.map { p ->
                            if (p.id == postId) p.copy(likedByMe = res.liked, likeCount = res.likeCount) else p
                        })
                    }
                }
                .onFailure { _state.update { it.copy(posts = original) } }
        }
    }
}

@Composable
fun FeedScreen(
    inner: PaddingValues,
    hazeState: HazeState,
    onOpenPost: (Int) -> Unit,
    onOpenProfile: (Int) -> Unit,
) {
    val vm: FeedViewModel = viewModel()
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.posts.size - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) vm.loadMore()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().asHazeSource(hazeState),
            contentPadding = PaddingValues(
                top = 72.dp,
                bottom = inner.calculateBottomPadding() + 16.dp,
                start = 12.dp,
                end = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(state.posts, key = { it.id }) { post ->
                PostCard(
                    post = post,
                    onLike = { vm.toggleLike(post.id) },
                    onOpen = { onOpenPost(post.id) },
                    onOpenProfile = { onOpenProfile(post.author?.id ?: post.userId) },
                )
            }
            if (state.posts.isEmpty() && state.loading) {
                item { Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { CircularProgressIndicator() } }
            }
            if (state.error != null && state.posts.isEmpty()) {
                item {
                    Text(
                        state.error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                    )
                }
            }
            if (state.posts.isNotEmpty() && !state.hasMore) {
                item {
                    Text(
                        "You're all caught up",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }

        GlassTopBar(
            hazeState = hazeState,
            title = "Kaam Chor",
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
fun GlassTopBar(
    hazeState: HazeState,
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    GlassSurface(
        hazeState = hazeState,
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        containerAlpha = 0.6f,
        padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = WordmarkStyle, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
    }
}

@Composable
fun PostCard(
    post: PostDto,
    onLike: () -> Unit,
    onOpen: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    TranslucentCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        containerAlpha = 0.5f,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(user = post.author, modifier = Modifier.clickable(onClick = onOpenProfile))
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(post.author?.name ?: "Unknown", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "@${post.author?.username ?: "user"} · ${relativeTime(post.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            AsyncImage(
                model = post.photoUrl,
                contentDescription = post.caption,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(0.dp))
                    .clickable(onClick = onOpen),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onLike) {
                    Icon(
                        imageVector = if (post.likedByMe) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (post.likedByMe) LikeRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("${post.likeCount}", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.size(10.dp))
                IconButton(onClick = onOpen) {
                    Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Comments", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${post.commentCount}", style = MaterialTheme.typography.labelLarge)
            }

            if (post.caption.isNotBlank()) {
                Text(
                    post.caption,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                )
            }
        }
    }
}