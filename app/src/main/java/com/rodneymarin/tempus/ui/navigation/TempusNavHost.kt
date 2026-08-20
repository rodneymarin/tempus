package com.rodneymarin.tempus.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rodneymarin.tempus.TempusApp
import com.rodneymarin.tempus.ui.dashboard.DashboardScreen
import com.rodneymarin.tempus.ui.detail.TrackerDetailScreen
import com.rodneymarin.tempus.ui.editor.TrackerEditorScreen

@Composable
fun TempusNavHost(
    navController: NavHostController = rememberNavController(),
) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as TempusApp
    val themePrefs = app.container.themePrefs
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(
            Routes.DASHBOARD,
            enterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) },
        ) {
            DashboardScreen(
                onOpenTracker = { id -> navController.navigate(Routes.detail(id)) },
                onCreateTracker = { navController.navigate(Routes.editor()) },
                themePrefs = themePrefs,
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("trackerId") { type = NavType.LongType }),
            enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
        ) { entry ->
            val trackerId = entry.arguments?.getLong("trackerId") ?: return@composable
            TrackerDetailScreen(
                trackerId = trackerId,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.editor(trackerId)) },
            )
        }
        composable(
            route = Routes.EDITOR,
            arguments = listOf(navArgument("trackerId") {
                type = NavType.LongType
                defaultValue = -1L
            }),
            enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) },
        ) { entry ->
            val trackerId = entry.arguments?.getLong("trackerId")?.takeIf { it > 0 }
            TrackerEditorScreen(
                trackerId = trackerId,
                onDone = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
    }
}
