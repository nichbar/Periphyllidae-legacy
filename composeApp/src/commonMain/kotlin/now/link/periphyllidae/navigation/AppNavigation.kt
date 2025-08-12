package now.link.periphyllidae.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import now.link.periphyllidae.ui.screens.*
import org.koin.compose.viewmodel.koinViewModel

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
    object Search : Screen("search")
    object Settings : Screen("settings")
    object NowPlaying : Screen("now_playing")
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Login.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = koinViewModel(),
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = koinViewModel(),
                onNavigateToSearch = {
                    navController.navigate(Screen.Search.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToNowPlaying = {
                    navController.navigate(Screen.NowPlaying.route)
                }
            )
        }
        
        composable(Screen.Search.route) {
            SearchScreen(
                viewModel = koinViewModel(),
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToNowPlaying = {
                    navController.navigate(Screen.NowPlaying.route)
                }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = koinViewModel(),
                onNavigateBack = {
                    navController.popBackStack()
                },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.NowPlaying.route) {
            NowPlayingScreen(
                viewModel = koinViewModel(),
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
