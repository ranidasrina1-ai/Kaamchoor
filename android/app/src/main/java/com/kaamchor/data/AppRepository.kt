package com.kaamchor.data

import android.content.Context
import android.net.Uri
import com.kaamchor.data.remote.ApiService
import com.kaamchor.data.remote.CommentDto
import com.kaamchor.data.remote.CommentRequest
import com.kaamchor.data.remote.CreateGroupRequest
import com.kaamchor.data.remote.FeedResponse
import com.kaamchor.data.remote.GroupDto
import com.kaamchor.data.remote.InitiateCallRequest
import com.kaamchor.data.remote.IceServersResponse
import com.kaamchor.data.remote.LikeResponse
import com.kaamchor.data.remote.LoginRequest
import com.kaamchor.data.remote.MessageDto
import com.kaamchor.data.remote.MessagesResponse
import com.kaamchor.data.remote.PostDto
import com.kaamchor.data.remote.SignupRequest
import com.kaamchor.data.remote.TextMessageRequest
import com.kaamchor.data.remote.UserDto
import retrofit2.Response

class AppException(message: String) : Exception(message)

fun <T> Response<T>.orThrow(): T {
    if (isSuccessful) {
        return body() ?: throw AppException("Empty response")
    }
    val raw = errorBody()?.string().orEmpty()
    val parsed = runCatching {
        com.kaamchor.data.remote.GsonProvider.gson
            .fromJson(raw, ErrorBody::class.java)?.error
    }.getOrNull()
    throw AppException(parsed ?: "Request failed (${code()})")
}

data class ErrorBody(val error: String?)

class AppRepository(
    private val context: Context,
    private val apiProvider: () -> ApiService,
) {
    private val api: ApiService get() = apiProvider()

    suspend fun signup(username: String, email: String, password: String, displayName: String) =
        api.signup(SignupRequest(username, email, password, displayName)).orThrow()

    suspend fun login(identifier: String, password: String) =
        api.login(LoginRequest(identifier, password)).orThrow()

    suspend fun me(): UserDto = api.me().orThrow()
    suspend fun users(): List<UserDto> = api.users().orThrow()

    suspend fun createPost(uri: Uri, caption: String): PostDto {
        val part = MediaParts.filePart(context, uri, "photo") ?: throw AppException("Could not read image")
        return api.createPost(part, MediaParts.text(caption)).orThrow()
    }

    suspend fun feed(cursor: Int? = null): FeedResponse = api.feed(cursor = cursor).orThrow()
    suspend fun post(id: Int): PostDto = api.post(id).orThrow()
    suspend fun userPosts(userId: Int): FeedResponse = api.userPosts(userId).orThrow()
    suspend fun toggleLike(postId: Int): LikeResponse = api.toggleLike(postId).orThrow()
    suspend fun comments(postId: Int): List<CommentDto> = api.comments(postId).orThrow()
    suspend fun addComment(postId: Int, text: String): CommentDto =
        api.addComment(postId, CommentRequest(text)).orThrow()

    suspend fun profile(id: Int): UserDto = api.profile(id).orThrow()
    suspend fun myProfile(): UserDto = api.myProfile().orThrow()

    suspend fun editProfile(displayName: String, bio: String, avatar: Uri?): UserDto {
        val avatarPart = avatar?.let { MediaParts.filePart(context, it, "avatar") }
        return api.editProfileMultipart(
            MediaParts.text(displayName),
            MediaParts.text(bio),
            avatarPart,
        ).orThrow()
    }

    suspend fun groups(): List<GroupDto> = api.groups().orThrow()
    suspend fun createGroup(name: String?, memberIds: List<Int>): GroupDto =
        api.createGroup(CreateGroupRequest(name, memberIds)).orThrow()
    suspend fun group(id: Int): GroupDto = api.group(id).orThrow()
    suspend fun members(groupId: Int): List<UserDto> = api.members(groupId).orThrow()

    suspend fun messages(groupId: Int, cursor: Int? = null): MessagesResponse =
        api.messages(groupId, cursor = cursor).orThrow()

    suspend fun sendText(groupId: Int, text: String): MessageDto =
        api.sendText(groupId, TextMessageRequest(text)).orThrow()

    suspend fun sendMedia(
        groupId: Int,
        uri: Uri,
        mediaType: String,
        text: String = "",
        durationMs: Long? = null,
        mimeOverride: String? = null,
        fileName: String? = null,
    ): MessageDto {
        val part = MediaParts.filePart(context, uri, mimeOverride = mimeOverride, fileName = fileName)
            ?: throw AppException("Could not read file")
        val durationBody = durationMs?.let { MediaParts.text((it / 1000.0).toString()) }
        return api.sendMedia(groupId, part, MediaParts.text(mediaType), MediaParts.text(text), durationBody).orThrow()
    }

    suspend fun markAllRead(groupId: Int) { api.markAllRead(groupId).orThrow() }
    suspend fun markRead(messageId: Int) { api.markRead(messageId).orThrow() }

    suspend fun initiateCall(groupId: Int?, callType: String) =
        api.initiateCall(InitiateCallRequest(groupId, callType)).orThrow()

    suspend fun answerCall(callId: Int) = api.answerCall(callId).orThrow()
    suspend fun endCall(callId: Int) = api.endCall(callId).orThrow()
    suspend fun rejectCall(callId: Int) = api.rejectCall(callId).orThrow()

    suspend fun iceServers(): IceServersResponse = api.iceServers().orThrow()
    suspend fun health() = api.health().orThrow()
    suspend fun testNotification() = api.testNotification().orThrow()
    suspend fun logout() { runCatching { api.logout() } }
}
