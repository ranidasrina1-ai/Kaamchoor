package com.kaamchor.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaamchor.Graph
import com.kaamchor.data.remote.UserDto
import com.kaamchor.ui.components.GlassSurface
import com.kaamchor.ui.components.asHazeSource
import com.kaamchor.ui.components.rememberGlassState
import com.kaamchor.ui.theme.WordmarkStyle
import com.kaamchor.util.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isSignup: Boolean = false,
    val username: String = "",
    val email: String = "",
    val displayName: String = "",
    val password: String = "",
    val serverUrl: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val authed: UserDto? = null,
)

class AuthViewModel : ViewModel() {
    private val repo = Graph.repository
    private val session = Graph.session

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state

    init {
        viewModelScope.launch {
            _state.update { it.copy(serverUrl = session.serverUrlBlocking()) }
        }
    }

    fun onUsername(v: String) = _state.update { it.copy(username = v, error = null) }
    fun onEmail(v: String) = _state.update { it.copy(email = v, error = null) }
    fun onDisplayName(v: String) = _state.update { it.copy(displayName = v, error = null) }
    fun onPassword(v: String) = _state.update { it.copy(password = v, error = null) }
    fun onServerUrl(v: String) = _state.update { it.copy(serverUrl = v, error = null) }
    fun toggleMode() = _state.update { it.copy(isSignup = !it.isSignup, error = null) }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                session.setServerUrl(com.kaamchor.data.remote.normalizeBaseUrl(s.serverUrl))
                val auth = if (s.isSignup) {
                    if (s.username.isBlank() || s.email.isBlank() || s.password.length < 6) {
                        throw com.kaamchor.data.AppException("Fill all fields. Password needs 6+ characters.")
                    }
                    repo.signup(
                        username = s.username.trim(),
                        email = s.email.trim(),
                        password = s.password,
                        displayName = s.displayName.trim().ifBlank { s.username.trim() },
                    )
                } else {
                    if (s.username.isBlank() || s.password.isBlank()) {
                        throw com.kaamchor.data.AppException("Enter your username/email and password.")
                    }
                    repo.login(s.username.trim(), s.password)
                }
                Session.saveAuth(auth)
                _state.update { it.copy(loading = false, authed = auth.user) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Something went wrong") }
            }
        }
    }
}

@Composable
fun AuthScreen(onAuthed: () -> Unit) {
    val vm: AuthViewModel = viewModel()
    val state by vm.state.collectAsState()
    val haze = rememberGlassState()

    LaunchedEffect(state.authed) {
        if (state.authed != null) onAuthed()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surface,
                    )
                )
            )
            .asHazeSourceIfNeeded(haze),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(24.dp),
        ) {
            Spacer(Modifier.height(48.dp))
            Text("Kaam Chor", style = WordmarkStyle, color = MaterialTheme.colorScheme.primary)
            Text(
                "Share moments. Talk freely.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(32.dp))

            GlassSurface(
                hazeState = haze,
                modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
                containerAlpha = 0.72f,
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        if (state.isSignup) "Create account" else "Welcome back",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = state.username,
                        onValueChange = vm::onUsername,
                        label = { Text(if (state.isSignup) "Username" else "Username or email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.isSignup) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.email,
                            onValueChange = vm::onEmail,
                            label = { Text("Email") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.displayName,
                            onValueChange = vm::onDisplayName,
                            label = { Text("Display name (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    var visible by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = vm::onPassword,
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        trailingIcon = {
                            IconButton(onClick = { visible = !visible }) {
                                Icon(
                                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (state.error != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            state.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = vm::submit,
                        enabled = !state.loading,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        if (state.loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(if (state.isSignup) "Sign up" else "Log in")
                        }
                    }

                    TextButton(
                        onClick = vm::toggleMode,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (state.isSignup) "Already have an account? Log in"
                            else "New here? Create an account",
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            var showServer by remember { mutableStateOf(false) }
            if (!showServer) {
                TextButton(onClick = { showServer = true }) {
                    Text("Server: ${state.serverUrl}", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                OutlinedTextField(
                    value = state.serverUrl,
                    onValueChange = vm::onServerUrl,
                    label = { Text("Server URL") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
                )
            }
        }
    }
}

@Composable
private fun Modifier.asHazeSourceIfNeeded(haze: dev.chrisbanes.haze.HazeState): Modifier =
    this.asHazeSource(haze)
