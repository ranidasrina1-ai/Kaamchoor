package com.kaamchor.ui.nav

object Routes {
    const val AUTH = "auth"
    const val HOME = "home"
    const val CREATE_POST = "create_post"
    const val POST_DETAIL = "post/{postId}"
    const val PROFILE = "profile/{userId}"
    const val EDIT_PROFILE = "edit_profile"
    const val CHAT_ROOM = "chat/{groupId}"
    const val NEW_CHAT = "new_chat"
    const val SETTINGS = "settings"

    fun post(postId: Int) = "post/$postId"
    fun profile(userId: Int) = "profile/$userId"
    fun chatRoom(groupId: Int) = "chat/$groupId"
}
