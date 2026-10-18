package com.kaamchor.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kaamchor.Graph
import com.kaamchor.data.remote.normalizeBaseUrl
import com.kaamchor.util.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val serverUrl: String = "",
    val status: String? = null,
    val saved: Boolean = false,
)

class SettingsViewModel : ViewModel() {
    private val repo = Graph.repository
    private val session = Graph.session
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state

    init {
        viewModelScope.launch {
            _state.update { it.copy(serverUrl = session.serverUrlBlocking()) }
        }
    }

    fun onUrl(v: String) = _state.update { it.copy(serverUrl = v, saved = false, status = null) }

    fun saveUrl() {
        viewModelScope.launch {
            session.setServerUrl(normalizeBaseUrl(_state.value.serverUrl))
            _state.update { it.copy(saved = true, status = "Saved. Reconnecting…") }
            Graph.socket.disconnect()
            Graph.socket.connect()
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _state.update { it.copy(status = "Checking…") }
            runCatching { repo.health() }
                .onSuccess { _state.update { s -> s.copy(status = "Server reachable ✓") } }
                .onFailure { e -> _state.update { s -> s.copy(status = "Failed: ${e.message}") } }
        }
    }

    fun testNotification() {
        viewModelScope.launch {
            runCatching { repo.testNotification() }
                .onSuccess { _state.update { s -> s.copy(status = "Notification sent") } }
                .onFailure { e -> _state.update { s -> s.copy(status = "Failed: ${e.message}") } }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.logout()
            Session.logout()
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onLoggedOut: () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    val state by vm.state.collectAsState()
    var confirmingLogout by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text("Server", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Point the app at your Kaam Chor backend (same Wi-Fi for local testing).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.serverUrl,
                onValueChange = vm::onUrl,
                label = { Text("Server URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = vm::saveUrl, modifier = Modifier.fillMaxWidth()) { Text("Save server") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = vm::testConnection, modifier = Modifier.fillMaxWidth()) {
                Text("Test connection")
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))

            Text("Notifications", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = vm::testNotification, modifier = Modifier.fillMaxWidth()) {
                Text("Send test notification")
            }

            if (state.status != null) {
                Spacer(Modifier.height(12.dp))
                Text(state.status!!, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))

            if (!confirmingLogout) {
                OutlinedButton(
                    onClick = { confirmingLogout = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Log out") }
            } else {
                Button(
                    onClick = { vm.logout(onLoggedOut) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Confirm log out") }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "Kaam Chor v2.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
