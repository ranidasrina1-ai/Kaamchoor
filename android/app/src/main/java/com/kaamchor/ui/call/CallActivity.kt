package com.kaamchor.ui.call

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.json.JSONObject
import org.webrtc.SurfaceViewRenderer
import com.kaamchor.ui.theme.KaamChorTheme

class CallActivity : ComponentActivity() {

    private val vm: CallViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.all { it } || result[Manifest.permission.RECORD_AUDIO] == true) {
            beginFromIntent()
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val local = SurfaceViewRenderer(this).apply {
            init(vm.engine.eglBase.eglBaseContext, null)
        }
        val remote = SurfaceViewRenderer(this).apply {
            init(vm.engine.eglBase.eglBaseContext, null)
        }
        vm.bindRenderers(local, remote)

        setContent {
            KaamChorTheme {
                val state by vm.state.collectAsState()
                LaunchedEffect(state.phase) {
                    if (state.phase == CallPhase.ENDED) finish()
                }
                CallScreen(
                    state = state,
                    localRenderer = local,
                    remoteRenderer = remote,
                    onAccept = vm::accept,
                    onDecline = vm::decline,
                    onEnd = vm::end,
                    onToggleMic = vm::toggleMic,
                    onToggleCamera = vm::toggleCamera,
                    onSwitchCamera = vm::switchCamera,
                )
            }
        }

        val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        permissionLauncher.launch(permissions)
    }

    private fun beginFromIntent() {
        val incoming = intent.getStringExtra("incoming_call")
        if (incoming != null) {
            runCatching { vm.onIncoming(JSONObject(incoming)) }
        } else {
            val groupId = intent.getIntExtra("outgoing_group_id", -1)
            val callType = intent.getStringExtra("call_type") ?: "audio"
            if (groupId > 0) vm.startOutgoing(groupId, callType)
        }
    }
}
