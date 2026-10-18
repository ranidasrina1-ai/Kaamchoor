package com.kaamchor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kaamchor.data.remote.UserDto

@Composable
fun Avatar(
    user: UserDto?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val name = user?.name ?: "?"
    val initial = name.trim().firstOrNull()?.uppercase() ?: "?"
    val avatarUrl = user?.avatarUrl

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colorFor(user?.id ?: 0)),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        } else {
            Text(
                text = initial,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

private val AVATAR_COLORS = listOf(
    Color(0xFF005DB7), Color(0xFF6F5575), Color(0xFF3E4758),
    Color(0xFF00696E), Color(0xFF8B5000), Color(0xFF5B5891),
)

fun colorFor(id: Int): Color = AVATAR_COLORS[kotlin.math.abs(id) % AVATAR_COLORS.size]
