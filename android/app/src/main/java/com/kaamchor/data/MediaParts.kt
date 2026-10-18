package com.kaamchor.data

import android.content.Context
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

object MediaParts {

    fun text(value: String): RequestBody =
        value.toRequestBody("text/plain".toMediaTypeOrNull())

    fun filePart(
        context: Context,
        uri: Uri,
        fieldName: String = "file",
        mimeOverride: String? = null,
        fileName: String? = null,
    ): MultipartBody.Part? {
        val resolver = context.contentResolver
        val mime = mimeOverride ?: resolver.getType(uri) ?: guessMime(uri) ?: "application/octet-stream"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        val extension = when {
            fileName != null -> fileName.substringAfterLast('.', "bin")
            mime.startsWith("image/") -> mime.substringAfter("/")
            mime == "video/mp4" -> "mp4"
            mime.startsWith("video/") -> "mp4"
            mime.startsWith("audio/") -> "m4a"
            else -> "bin"
        }
        val safeName = fileName ?: "upload.$extension"
        val body = bytes.toRequestBody(mime.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(fieldName, safeName, body)
    }

    private fun guessMime(uri: Uri): String? {
        val path = uri.path ?: return null
        return when (path.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "m4a", "aac" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "ogg" -> "audio/ogg"
            else -> null
        }
    }
}
