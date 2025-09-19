package now.link.periphyllidae.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import now.link.periphyllidae.ui.components.ServerConfigurationForm
import now.link.periphyllidae.viewmodels.LoginViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoginSuccess: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    // Navigate on successful authentication
    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated) {
            onLoginSuccess()
        }
    }

    Scaffold { paddingValues ->
        ServerConfigurationForm(
            serverUrl = state.serverUrl,
            username = state.username,
            password = state.password,
            alias = state.alias,
            isLoading = state.isLoading,
            error = state.error,
            onServerUrlChange = { viewModel.updateServerUrl(it) },
            onUsernameChange = { viewModel.updateUsername(it) },
            onPasswordChange = { viewModel.updatePassword(it) },
            onAliasChange = { viewModel.updateAlias(it) },
            onSubmit = { viewModel.login() },
            submitButtonText = "Connect to Server",
            title = "Connect to Server",
            subtitle = "Subsonic Music Player",
            description = "Enter your Subsonic server details to get started",
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        )
    }
}
