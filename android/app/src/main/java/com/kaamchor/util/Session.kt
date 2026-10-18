package com.kaamchor.util

import com.kaamchor.Graph
import com.kaamchor.data.remote.AuthResponse
import com.kaamchor.data.remote.GsonProvider
import com.kaamchor.data.remote.UserDto
import kotlinx.coroutines.flow.first

object Session {

    suspend fun saveAuth(auth: AuthResponse) {
        Graph.session.saveAuth(
            access = auth.token,
            refresh = auth.refreshToken,
            userJson = GsonProvider.gson.toJson(auth.user),
        )
        Graph.socket.setSelfUserId(auth.user.id)
        Graph.socket.connect()
    }

    suspend fun saveUser(user: UserDto) {
        Graph.session.saveUser(GsonProvider.gson.toJson(user))
        Graph.socket.setSelfUserId(user.id)
    }

    suspend fun logout() {
        Graph.socket.disconnect()
        Graph.session.clear()
    }

    suspend fun rawRefreshToken(): String? = Graph.session.refreshTokenBlocking()

    suspend fun hasSession(): Boolean = !Graph.session.accessTokenBlocking().isNullOrBlank()

    suspend fun cachedUser(): UserDto? {
        val json = Graph.session.userJson.first() ?: return null
        return runCatching { GsonProvider.gson.fromJson(json, UserDto::class.java) }.getOrNull()
    }
}
