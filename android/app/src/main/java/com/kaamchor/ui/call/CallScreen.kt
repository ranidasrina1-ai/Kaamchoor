package com.kaamchor.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.kaamchor.ui.components.Avatar
import com.kaamchor.ui.components.GlassSurface
import com.kaamchor.ui.components.asHazeSource
import com.kaamchor.ui.components.rememberGlassState
import com.kaamchor.ui.theme.CallRed
import com.kaamchor.ui.theme.OnlineGreen
import org.webrtc.SurfaceViewRenderer

@Composable
fun CallScreen(
    state: CallUiState,
    localRenderer: SurfaceViewRenderer?,
    remoteRenderer: SurfaceViewRenderer?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onEnd: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleCamera: () -> Unit,
    onSwitchCamera: () -> Unit,
) {
    val haze = rememberGlassState()
    val isVideo = state.callType == "video"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF10131A), Color(0xFF1B2130))
                )
            ),
    ) {
        Box(Modifier.fillMaxSize().asHazeSource(haze)) {
            if (isVideo && remoteRenderer != null) {
                AndroidView(
                    factory = { remoteRenderer },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    val primary = state.participants.firstOrNull()
                    Avatar(
                        user = primary?.let { com.kaamchor.data.remote.UserDto(it.userId, username = it.name.lowercase().replace(" ", ""), displayName = it.name) },
                        size = 120.dp,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        state.title,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when (state.phase) {
                            CallPhase.RINGING -> if (state.isCaller) "Ringing…" else "Incoming call"
                            CallPhase.CONNECTING -> "Connecting…"
                            CallPhase.CONNECTED -> "Connected"
                            CallPhase.ENDED -> "Call ended"
                        },
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            // Local preview (PiP)
            if (isVideo && localRenderer != null) {
                AndroidView(
                    factory = { localRenderer },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(16.dp)
                        .size(width = 110.dp, height = 150.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
            }

            // Controls
            GlassSurface(
                hazeState = haze,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                shape = RoundedCornerShape(30.dp),
                containerAlpha = 0.5f,
                padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.phase == CallPhase.RINGING && !state.isCaller) {
                        CallControl(
                            icon = Icons.Filled.CallEnd,
                            background = CallRed,
                            onClick = onDecline,
                        )
                        CallControl(
                            icon = Icons.Filled.Videocam,
                            background = OnlineGreen,
                            onClick = onAccept,
                        )
                    } else {
                        CallControl(
                            icon = if (state.micOn) Icons.Filled.Mic else Icons.Filled.MicOff,
                            onClick = onToggleMic,
                        )
                        if (isVideo) {
                            CallControl(
                                icon = if (state.cameraOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                                onClick = onToggleCamera,
                            )
                            CallControl(icon = Icons.Filled.Cameraswitch, onClick = onSwitchCamera)
                        }
                        CallControl(
                            icon = Icons.Filled.CallEnd,
                            background = CallRed,
                            onClick = onEnd,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    background: Color = Color.White.copy(alpha = 0.16f),
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(background),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
    }
}
