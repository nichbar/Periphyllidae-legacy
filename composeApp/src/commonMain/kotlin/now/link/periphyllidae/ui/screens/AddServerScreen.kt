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
import now.link.periphyllidae.viewmodels.AddServerViewModel

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add New Server") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
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
            description = "Enter your Subsonic server details to add it to your server list",
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        )
    }
}
