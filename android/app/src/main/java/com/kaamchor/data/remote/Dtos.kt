package com.kaamchor.data.remote

import com.google.gson.annotations.SerializedName

data class UserDto(
    val id: Int,
    val username: String,
    @SerializedName("display_name") val displayName: String?,
    val bio: String? = null,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    val email: String? = null,
) {
    val name: String get() = displayName?.takeIf { it.isNotBlank() } ?: username
}

data class AuthResponse(
    val token: String,
    @SerializedName("refresh_token") val refreshToken: String,
    val user: UserDto,
)

data class LoginRequest(val identifier: String, val password: String)

data class SignupRequest(
    val username: String,
    val email: String,
    val password: String,
    @SerializedName("display_name") val displayName: String,
)

data class PostDto(
    val id: Int,
    @SerializedName("user_id") val userId: Int,
    val caption: String = "",
    @SerializedName("photo_url") val photoUrl: String,
    @SerializedName("created_at") val createdAt: String? = null,
    val author: UserDto? = null,
    @SerializedName("like_count") val likeCount: Int = 0,
    @SerializedName("comment_count") val commentCount: Int = 0,
    @SerializedName("liked_by_me") val likedByMe: Boolean = false,
)

data class FeedResponse(
    val posts: List<PostDto> = emptyList(),
    @SerializedName("next_cursor") val nextCursor: Int? = null,
    @SerializedName("has_more") val hasMore: Boolean = false,
)

data class CommentDto(
    val id: Int,
    @SerializedName("post_id") val postId: Int,
    @SerializedName("user_id") val userId: Int,
    val text: String,
    @SerializedName("created_at") val createdAt: String? = null,
    val author: UserDto? = null,
)

data class LikeResponse(val liked: Boolean, @SerializedName("like_count") val likeCount: Int)

data class GroupDto(
    val id: Int,
    val name: String = "",
    @SerializedName("is_group") val isGroup: Boolean = false,
    val members: List<UserDto> = emptyList(),
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("last_message") val lastMessage: MessageDto? = null,
    @SerializedName("unread_count") val unreadCount: Int = 0,
)

data class MessageDto(
    val id: Int,
    @SerializedName("group_id") val groupId: Int,
    @SerializedName("sender_id") val senderId: Int,
    val text: String = "",
    @SerializedName("media_url") val mediaUrl: String? = null,
    @SerializedName("media_type") val mediaType: String = "text",
    val duration: Double? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    val sender: UserDto? = null,
)

data class MessagesResponse(
    val messages: List<MessageDto> = emptyList(),
    @SerializedName("next_cursor") val nextCursor: Int? = null,
    @SerializedName("has_more") val hasMore: Boolean = false,
)

data class CreateGroupRequest(
    val name: String? = null,
    @SerializedName("member_ids") val memberIds: List<Int>,
)

data class TextMessageRequest(val text: String)

data class IceServerDto(val urls: Any? = null, val username: String? = null, val credential: String? = null)

data class IceServersResponse(@SerializedName("iceServers") val iceServers: List<IceServerDto> = emptyList())

data class UploadResponse(val url: String)

data class CallDto(
    val id: Int,
    @SerializedName("group_id") val groupId: Int? = null,
    @SerializedName("caller_id") val callerId: Int,
    @SerializedName("call_type") val callType: String = "audio",
    @SerializedName("is_group") val isGroup: Boolean = false,
    val status: String = "initiated",
)

data class InitiateCallRequest(
    @SerializedName("group_id") val groupId: Int?,
    @SerializedName("call_type") val callType: String,
)

data class CommentRequest(val text: String)
