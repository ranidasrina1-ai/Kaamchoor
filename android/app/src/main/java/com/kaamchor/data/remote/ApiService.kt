package com.kaamchor.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    // ── Auth ────────────────────────────────────────────────────────────
    @POST("api/auth/signup")
    suspend fun signup(@Body body: SignupRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<AuthResponse>

    @GET("api/auth/me")
    suspend fun me(): Response<UserDto>

    @GET("api/auth/users")
    suspend fun users(): Response<List<UserDto>>

    @POST("api/auth/logout")
    suspend fun logout(): Response<Unit>

    // ── Posts ───────────────────────────────────────────────────────────
    @Multipart
    @POST("api/posts")
    suspend fun createPost(
        @Part photo: MultipartBody.Part,
        @Part("caption") caption: RequestBody,
    ): Response<PostDto>

    @GET("api/posts")
    suspend fun feed(
        @Query("limit") limit: Int = 20,
        @Query("cursor") cursor: Int? = null,
    ): Response<FeedResponse>

    @GET("api/posts/{id}")
    suspend fun post(@Path("id") id: Int): Response<PostDto>

    @GET("api/posts/user/{userId}")
    suspend fun userPosts(
        @Path("userId") userId: Int,
        @Query("limit") limit: Int = 30,
        @Query("cursor") cursor: Int? = null,
    ): Response<FeedResponse>

    @POST("api/posts/{id}/like")
    suspend fun toggleLike(@Path("id") id: Int): Response<LikeResponse>

    @GET("api/posts/{id}/comments")
    suspend fun comments(@Path("id") id: Int): Response<List<CommentDto>>

    @POST("api/posts/{id}/comments")
    suspend fun addComment(@Path("id") id: Int, @Body body: CommentRequest): Response<CommentDto>

    // ── Profile ─────────────────────────────────────────────────────────
    @GET("api/profile/{id}")
    suspend fun profile(@Path("id") id: Int): Response<UserDto>

    @GET("api/profile/me")
    suspend fun myProfile(): Response<UserDto>

    @Multipart
    @PUT("api/profile/edit")
    suspend fun editProfileMultipart(
        @Part("display_name") displayName: RequestBody,
        @Part("bio") bio: RequestBody,
        @Part avatar: MultipartBody.Part?,
    ): Response<UserDto>

    // ── Chat ────────────────────────────────────────────────────────────
    @GET("api/chat/groups")
    suspend fun groups(): Response<List<GroupDto>>

    @POST("api/chat/groups")
    suspend fun createGroup(@Body body: CreateGroupRequest): Response<GroupDto>

    @GET("api/chat/groups/{id}")
    suspend fun group(@Path("id") id: Int): Response<GroupDto>

    @GET("api/chat/groups/{id}/members")
    suspend fun members(@Path("id") id: Int): Response<List<UserDto>>

    @GET("api/chat/groups/{id}/messages")
    suspend fun messages(
        @Path("id") id: Int,
        @Query("limit") limit: Int = 30,
        @Query("cursor") cursor: Int? = null,
    ): Response<MessagesResponse>

    @POST("api/chat/groups/{id}/messages")
    suspend fun sendText(@Path("id") id: Int, @Body body: TextMessageRequest): Response<MessageDto>

    @Multipart
    @POST("api/chat/groups/{id}/messages")
    suspend fun sendMedia(
        @Path("id") id: Int,
        @Part file: MultipartBody.Part,
        @Part("media_type") mediaType: RequestBody,
        @Part("text") text: RequestBody,
        @Part("duration") duration: RequestBody?,
    ): Response<MessageDto>

    @POST("api/chat/groups/{id}/read-all")
    suspend fun markAllRead(@Path("id") id: Int): Response<Unit>

    @POST("api/chat/messages/{id}/read")
    suspend fun markRead(@Path("id") id: Int): Response<Unit>

    // ── Media ───────────────────────────────────────────────────────────
    @Multipart
    @POST("api/media/upload")
    suspend fun upload(@Part file: MultipartBody.Part): Response<UploadResponse>

    // ── Calls ───────────────────────────────────────────────────────────
    @POST("api/calls/initiate")
    suspend fun initiateCall(@Body body: InitiateCallRequest): Response<CallDto>

    @POST("api/calls/{id}/answer")
    suspend fun answerCall(@Path("id") id: Int): Response<CallDto>

    @POST("api/calls/{id}/end")
    suspend fun endCall(@Path("id") id: Int): Response<CallDto>

    @POST("api/calls/{id}/reject")
    suspend fun rejectCall(@Path("id") id: Int): Response<CallDto>

    @GET("api/calls/history")
    suspend fun callHistory(): Response<List<CallDto>>

    // ── Misc ────────────────────────────────────────────────────────────
    @GET("api/ice-servers")
    suspend fun iceServers(): Response<IceServersResponse>

    @GET("api/health")
    suspend fun health(): Response<Map<String, Any>>

    @POST("api/notifications/test")
    suspend fun testNotification(): Response<Map<String, Any>>
}
