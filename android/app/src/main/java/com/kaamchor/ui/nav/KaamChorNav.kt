package com.kaamchor.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kaamchor.ui.screens.auth.AuthScreen
import com.kaamchor.ui.screens.chat.ChatRoomScreen
import com.kaamchor.ui.screens.chat.NewChatScreen
import com.kaamchor.ui.screens.home.MainShell
import com.kaamchor.ui.screens.post.CreatePostScreen
import com.kaamchor.ui.screens.post.PostDetailScreen
import com.kaamchor.ui.screens.profile.EditProfileScreen
import com.kaamchor.ui.screens.profile.ProfileScreen
import com.kaamchor.ui.screens.settings.SettingsScreen
import com.kaamchor.util.Session

@Composable
fun KaamChorNav() {
    val nav = rememberNavController()
    var startRoute by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        startRoute = if (Session.hasSession()) Routes.HOME else Routes.AUTH
    }

    if (startRoute == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    NavHost(navController = nav, startDestination = startRoute!!) {

        composable(Routes.AUTH) {
            AuthScreen(onAuthed = {
                nav.navigate(Routes.HOME) {
                    popUpTo(Routes.AUTH) { inclusive = true }
                }
            })
        }

        composable(Routes.HOME) {
            MainShell(nav)
        }

        composable(Routes.CREATE_POST) {
            CreatePostScreen(onDone = { nav.popBackStack() })
        }

        composable(
            Routes.POST_DETAIL,
            arguments = listOf(navArgument("postId") { type = NavType.IntType }),
        ) { entry ->
            val postId = entry.arguments?.getInt("postId") ?: return@composable
            PostDetailScreen(
                postId = postId,
                onBack = { nav.popBackStack() },
                onOpenProfile = { nav.navigate(Routes.profile(it)) },
            )
        }

        composable(
            Routes.PROFILE,
            arguments = listOf(navArgument("userId") { type = NavType.IntType }),
        ) { entry ->
            val userId = entry.arguments?.getInt("userId") ?: return@composable
            ProfileScreen(
                userId = userId,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate(Routes.EDIT_PROFILE) },
                onOpenPost = { nav.navigate(Routes.post(it)) },
                onMessage = { groupId, name ->
                    nav.navigate(Routes.chatRoom(groupId))
                },
            )
        }

        composable(Routes.EDIT_PROFILE) {
            EditProfileScreen(onBack = { nav.popBackStack() })
        }

        composable(
            Routes.CHAT_ROOM,
            arguments = listOf(navArgument("groupId") { type = NavType.IntType }),
        ) { entry ->
            val groupId = entry.arguments?.getInt("groupId") ?: return@composable
            ChatRoomScreen(
                groupId = groupId,
                onBack = { nav.popBackStack() },
                onOpenProfile = { nav.navigate(Routes.profile(it)) },
            )
        }

        composable(Routes.NEW_CHAT) {
            NewChatScreen(
                onBack = { nav.popBackStack() },
                onCreated = { groupId ->
                    nav.navigate(Routes.chatRoom(groupId)) {
                        popUpTo(Routes.NEW_CHAT) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onLoggedOut = {
                    nav.navigate(Routes.AUTH) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
    }
}
