package now.link.periphyllidae.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import now.link.periphyllidae.ui.components.ServerConfigurationForm
import now.link.periphyllidae.ui.layout.ScreenContainer
import now.link.periphyllidae.ui.layout.TopBarConfig
import now.link.periphyllidae.viewmodels.AddServerViewModel
@Composable
fun AddServerScreen(
    viewModel: AddServerViewModel,
    onServerAdded: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Navigate on successful server addition
    LaunchedEffect(state.isServerAdded) {
        if (state.isServerAdded) {
            onServerAdded()
        }
    }

    // Clear state when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearState()
        }
    }

    val topBarConfig = TopBarConfig(
        title = "Add New Server",
        navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
        onNavigationClick = onNavigateBack
    )

    ScreenContainer(topBarConfig = topBarConfig) { paddingValues ->
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
            onSubmit = { viewModel.addServer() },
            submitButtonText = "Add Server",
            title = "Server Configuration",
            subtitle = "Add Subsonic Server",
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        )
    }
}
