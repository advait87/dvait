package com.dvait.base

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.*
import com.dvait.base.ui.navigation.AppNavigation
import com.dvait.base.ui.theme.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var navController: NavHostController

    private var pendingAccentColor: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as DvaitApp
        
        // Cache initial values so Compose never renders with stale defaults
        var cachedTheme by mutableStateOf("system")
        var cachedAccent by mutableStateOf("orange")
        var cachedOnboarding by mutableStateOf(false)
        var isReady by mutableStateOf(false)

        // Load critical settings BEFORE Compose renders
        lifecycleScope.launch {
            val (onboarding, accent, theme) = combine(
                app.settingsDataStore.onboardingCompleted,
                app.settingsDataStore.accentColor,
                app.settingsDataStore.appTheme
            ) { o, a, t -> Triple(o, a, t) }.first()
            cachedOnboarding = onboarding
            cachedAccent = accent
            cachedTheme = theme
            isReady = true
        }

        // Hold system splash until values are loaded
        splashScreen.setKeepOnScreenCondition { !isReady }

        // Observe accent color for delayed icon switching
        lifecycleScope.launch {
            app.settingsDataStore.accentColor.collect { color ->
                pendingAccentColor = color
            }
        }

        setContent {
            // Don't render anything until data is ready — splash covers us
            if (!isReady) return@setContent

            val appTheme by app.settingsDataStore.appTheme.collectAsState(initial = cachedTheme)
            val accentColor by app.settingsDataStore.accentColor.collectAsState(initial = cachedAccent)
            val onboardingCompleted by app.settingsDataStore.onboardingCompleted.collectAsState(initial = cachedOnboarding)

            DvaitTheme(
                appTheme = appTheme,
                accentColor = accentColor
            ) {
                val navController = rememberNavController()
                this.navController = navController
                AppNavigation(
                    navController = navController,
                    onboardingCompleted = onboardingCompleted,
                    queryEngine = app.queryEngine,
                    embeddingEngine = app.embeddingEngine,
                    settingsDataStore = app.settingsDataStore,
                    repository = app.repository,
                    conversationRepository = app.conversationRepository
                )
            }
        }

    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
    }


    override fun onStop() {
        super.onStop()
        pendingAccentColor?.let { color ->
            com.dvait.base.util.DynamicIconManager(this).setIcon(color)
        }
    }
}
