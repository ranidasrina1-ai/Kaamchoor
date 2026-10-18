package com.kaamchor.ui.screens.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.kaamchor.Graph
import com.kaamchor.data.remote.UserDto
import com.kaamchor.ui.components.Avatar
import com.kaamchor.util.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditProfileState(
    val user: UserDto? = null,
    val displayName: String = "",
    val bio: String = "",
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

class EditProfileViewModel : ViewModel() {
    private val repo = Graph.repository
    private val _state = MutableStateFlow(EditProfileState())
    val state: StateFlow<EditProfileState> = _state

    init {
        viewModelScope.launch {
            runCatching { repo.myProfile() }
                .onSuccess { u ->
                    _state.update { it.copy(user = u, displayName = u.name, bio = u.bio ?: "") }
                }
        }
    }

    fun onDisplayName(v: String) = _state.update { it.copy(displayName = v, error = null) }
    fun onBio(v: String) = _state.update { it.copy(bio = v, error = null) }

    fun save(avatar: android.net.Uri?) {
        val s = _state.value
        if (s.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching { repo.editProfile(s.displayName.trim(), s.bio, avatar) }
                .onSuccess { u ->
                    Session.saveUser(u)
                    _state.update { it.copy(saving = false, user = u, saved = true) }
                }
                .onFailure { _state.update { st -> st.copy(saving = false, error = it.message) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(onBack: () -> Unit) {
    val vm: EditProfileViewModel = viewModel()
    val state by vm.state.collectAsState()
    var avatar by remember { mutableStateOf<android.net.Uri?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { if (it != null) avatar = it }

    androidx.compose.runtime.LaunchedEffect(state.saved) { if (state.saved) onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Edit profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(112.dp).clickable {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                contentAlignment = Alignment.BottomEnd,
            ) {
                val current = avatar
                if (current != null) {
                    AsyncImage(
                        model = current,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(112.dp).clip(androidx.compose.foundation.shape.CircleShape),
                    )
                } else {
                    Avatar(user = state.user, size = 112.dp)
                }
                Text(
                    "Change",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(4.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = state.displayName,
                onValueChange = vm::onDisplayName,
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.bio,
                onValueChange = vm::onBio,
                label = { Text("Bio") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                maxLines = 5,
            )

            if (state.error != null) {
                Spacer(Modifier.height(10.dp))
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { vm.save(avatar) },
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Save changes")
                }
            }
        }
    }
}
