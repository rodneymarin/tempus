package com.rodneymarin.tempus.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rodneymarin.tempus.ui.dashboard.DashboardScreen
import com.rodneymarin.tempus.ui.detail.TrackerDetailScreen
import com.rodneymarin.tempus.ui.editor.TrackerEditorScreen

@Composable
fun TempusNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.DASHBOARD) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onOpenTracker = { id -> navController.navigate(Routes.detail(id)) },
                onCreateTracker = { navController.navigate(Routes.editor()) },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("trackerId") { type = NavType.LongType }),
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
