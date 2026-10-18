package com.kaamchor.ui.screens.home

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.kaamchor.Graph
import com.kaamchor.data.remote.UserDto
import com.kaamchor.service.SocketService
import com.kaamchor.ui.components.GlassSurface
import com.kaamchor.ui.components.rememberGlassState
import com.kaamchor.ui.nav.Routes
import com.kaamchor.ui.screens.chat.ChatListScreen
import com.kaamchor.ui.screens.feed.FeedScreen
import com.kaamchor.ui.screens.profile.ProfileScreen
import com.kaamchor.util.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {
    private val _me = MutableStateFlow<UserDto?>(null)
    val me: StateFlow<UserDto?> = _me

    init {
        viewModelScope.launch {
            _me.value = Session.cachedUser()
            runCatching { Graph.repository.me() }
                .onSuccess {
                    _me.value = it
                    Session.saveUser(it)
                }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { Graph.repository.me() }.onSuccess { _me.value = it }
        }
    }
}

private enum class Tab(val label: String, val filled: ImageVector, val outlined: ImageVector) {
    FEED("Feed", Icons.Filled.Home, Icons.Outlined.Home),
    CHATS("Chats", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.PersonOutline),
}

@Composable
fun MainShell(nav: NavController) {
    val vm: MainViewModel = viewModel()
    val me by vm.me.collectAsState()
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val haze = rememberGlassState()

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { startService(context) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startService(context)
        }
    }

    val insets = WindowInsets.navigationBars

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { nav.navigate(Routes.CREATE_POST) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Post") },
                )
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().padding(bottom = 0.dp)) {
                when (tab) {
                    0 -> FeedScreen(
                        inner = PaddingValues(bottom = 96.dp),
                        hazeState = haze,
                        onOpenPost = { nav.navigate(Routes.post(it)) },
                        onOpenProfile = { nav.navigate(Routes.profile(it)) },
                    )
                    1 -> ChatListScreen(
                        inner = PaddingValues(bottom = 96.dp),
                        hazeState = haze,
                        onOpenChat = { nav.navigate(Routes.chatRoom(it)) },
                        onNewChat = { nav.navigate(Routes.NEW_CHAT) },
                    )
                    else -> ProfileScreen(
                        userId = me?.id ?: -1,
                        inner = PaddingValues(bottom = 96.dp),
                        hazeState = haze,
                        onBack = null,
                        onEdit = { nav.navigate(Routes.EDIT_PROFILE) },
                        onOpenPost = { nav.navigate(Routes.post(it)) },
                        onMessage = { groupId, _ -> nav.navigate(Routes.chatRoom(groupId)) },
                        onSettings = { nav.navigate(Routes.SETTINGS) },
                    )
                }
            }

            // Liquid-glass bottom navigation
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(insets)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                GlassSurface(
                    hazeState = haze,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    containerAlpha = 0.7f,
                    padding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Tab.entries.forEachIndexed { index, t ->
                            val selected = tab == index
                            androidx.compose.foundation.layout.Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .selectable(selected = selected, onClick = { tab = index })
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    imageVector = if (selected) t.filled else t.outlined,
                                    contentDescription = t.label,
                                    tint = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                                Text(
                                    text = t.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun startService(context: android.content.Context) {
    val intent = Intent(context, SocketService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}
