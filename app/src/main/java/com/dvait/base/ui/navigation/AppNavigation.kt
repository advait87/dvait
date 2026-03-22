package com.dvait.base.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.dvait.base.engine.QueryEngine
import com.dvait.base.engine.EmbeddingEngine
import com.dvait.base.data.settings.SettingsDataStore
import com.dvait.base.data.repository.CapturedTextRepository
import com.dvait.base.ui.chat.ChatScreen
import com.dvait.base.ui.debug.DataViewerScreen
import com.dvait.base.ui.settings.AppFilterScreen
import com.dvait.base.ui.settings.SettingsScreen
import com.dvait.base.ui.settings.ThemeSettings
import com.dvait.base.ui.onboarding.OnboardingScreen

@Composable
fun AppNavigation(
    navController: NavHostController,
    onboardingCompleted: Boolean,
    queryEngine: QueryEngine,
    embeddingEngine: EmbeddingEngine,
    settingsDataStore: SettingsDataStore,
    repository: CapturedTextRepository,
    conversationRepository: com.dvait.base.data.repository.ConversationRepository
) {

    NavHost(
        navController = navController,
        startDestination = if (onboardingCompleted) "chat" else "onboarding",
        enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
    ) {
        composable(
            route = "chat?query={query}",
            arguments = listOf(navArgument("query") { nullable = true })
        ) { backStackEntry ->
            val query = backStackEntry.arguments?.getString("query")
            ChatScreen(
                queryEngine = queryEngine,
                conversationRepository = conversationRepository,
                onNavigateToSettings = { navController.navigate("settings") },
                settingsDataStore = settingsDataStore,
                initialQuery = query
            )
        }
        composable("settings") {
            SettingsScreen(
                settingsDataStore = settingsDataStore,
                repository = repository,
                embeddingEngine = embeddingEngine,
                onBack = { navController.popBackStack() },
                onViewData = { navController.navigate("data_viewer") },
                onViewAppFilter = { navController.navigate("app_filter") },
                onNavigateToThemeSettings = { navController.navigate("theme_settings") }
            )
        }
        composable("data_viewer") {
            DataViewerScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
        composable("app_filter") {
            AppFilterScreen(
                settingsDataStore = settingsDataStore,
                onBack = { navController.popBackStack() }
            )
        }
        composable("theme_settings") {
            ThemeSettings(
              settingsDataStore = settingsDataStore,
              onBack = { navController.popBackStack() }
            )
        }
        composable("onboarding") {
            OnboardingScreen(
                settingsDataStore = settingsDataStore,
                onFinish = {
                    navController.navigate("chat") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }
    }
}
