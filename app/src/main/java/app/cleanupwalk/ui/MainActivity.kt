package app.cleanupwalk.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.cleanupwalk.CleanupWalkApp
import app.cleanupwalk.ui.home.HomeScreen
import app.cleanupwalk.ui.home.HomeViewModel
import app.cleanupwalk.ui.theme.CleanupWalkTheme
import app.cleanupwalk.ui.walk.WalkScreen
import app.cleanupwalk.ui.walk.WalkViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as CleanupWalkApp

        setContent {
            CleanupWalkTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = HOME) {
                    composable(HOME) {
                        val homeViewModel: HomeViewModel = viewModel(
                            factory = viewModelFactory { initializer { HomeViewModel(app) } }
                        )
                        HomeScreen(
                            viewModel = homeViewModel,
                            scanner = app.scanner,
                            onOpenWalk = {
                                navController.navigate(WALK) { launchSingleTop = true }
                            }
                        )
                    }
                    composable(WALK) {
                        val walkViewModel: WalkViewModel = viewModel(
                            factory = viewModelFactory { initializer { WalkViewModel(app) } }
                        )
                        WalkScreen(
                            viewModel = walkViewModel,
                            scanner = app.scanner,
                            onClose = { navController.popBackStack(HOME, inclusive = false) }
                        )
                    }
                }
            }
        }
    }

    private companion object {
        const val HOME = "home"
        const val WALK = "walk"
    }
}
