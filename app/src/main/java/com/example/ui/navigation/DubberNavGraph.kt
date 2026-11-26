package com.example.ui.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.DubberViewModel
import com.example.ui.screens.ApiKeyManagementScreen
import com.example.ui.screens.AudioSyncScreen
import com.example.ui.screens.CharacterVoiceScreen
import com.example.ui.screens.ExportVideoScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.ImportVideoScreen
import com.example.ui.screens.KhmerTranslationScreen
import com.example.ui.screens.LanguageDetectionScreen
import com.example.ui.screens.SettingsHistoryScreen
import com.example.ui.screens.VideoAnalysisScreen
import com.example.ui.screens.VideoPreviewScreen
import com.example.ui.screens.VoicePreviewScreen
import kotlinx.serialization.Serializable

sealed interface AppRoute {
    @Serializable data object Dashboard : AppRoute
    @Serializable data object ImportVideo : AppRoute
    @Serializable data object ApiKeys : AppRoute
    @Serializable data object SettingsHistory : AppRoute

    @Serializable data class VideoAnalysis(val projectId: Long) : AppRoute
    @Serializable data class LanguageDetection(val projectId: Long) : AppRoute
    @Serializable data class CharacterVoices(val projectId: Long) : AppRoute
    @Serializable data class KhmerTranslation(val projectId: Long) : AppRoute
    @Serializable data class VoicePreview(val projectId: Long) : AppRoute
    @Serializable data class AudioSync(val projectId: Long) : AppRoute
    @Serializable data class VideoPreview(val projectId: Long) : AppRoute
    @Serializable data class ExportVideo(val projectId: Long) : AppRoute
}

enum class PipelineStepDestination(val shortTitle: String) {
    ANALYSIS("Analysis"),
    LANGUAGES("Languages"),
    CHARACTERS("Voices"),
    TRANSLATION("Khmer Script"),
    AUDITION("Voice Edit"),
    SYNC("Audio Sync"),
    PREVIEW("Preview"),
    EXPORT("Export")
}

private data class TopTab(
    val route: AppRoute,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topTabs = listOf(
    TopTab(AppRoute.Dashboard, "Studio", Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "nav_tab_dashboard"),
    TopTab(AppRoute.ImportVideo, "Import", Icons.Filled.AddCircle, Icons.Outlined.AddCircleOutline, "nav_tab_import"),
    TopTab(AppRoute.ApiKeys, "API Keys", Icons.Filled.Key, Icons.Outlined.Key, "nav_tab_apikeys"),
    TopTab(AppRoute.SettingsHistory, "History", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_tab_settings")
)

fun navigateToPipelineStep(
    navController: NavHostController,
    projectId: Long,
    step: PipelineStepDestination
) {
    val dest: AppRoute = when (step) {
        PipelineStepDestination.ANALYSIS -> AppRoute.VideoAnalysis(projectId)
        PipelineStepDestination.LANGUAGES -> AppRoute.LanguageDetection(projectId)
        PipelineStepDestination.CHARACTERS -> AppRoute.CharacterVoices(projectId)
        PipelineStepDestination.TRANSLATION -> AppRoute.KhmerTranslation(projectId)
        PipelineStepDestination.AUDITION -> AppRoute.VoicePreview(projectId)
        PipelineStepDestination.SYNC -> AppRoute.AudioSync(projectId)
        PipelineStepDestination.PREVIEW -> AppRoute.VideoPreview(projectId)
        PipelineStepDestination.EXPORT -> AppRoute.ExportVideo(projectId)
    }
    navController.navigate(dest) {
        launchSingleTop = true
    }
}

@Composable
fun DubberAppShell(viewModel: DubberViewModel) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination

    val isTopLevel = currentDest == null ||
        currentDest.hasRoute<AppRoute.Dashboard>() ||
        currentDest.hasRoute<AppRoute.ImportVideo>() ||
        currentDest.hasRoute<AppRoute.ApiKeys>() ||
        currentDest.hasRoute<AppRoute.SettingsHistory>()

    LaunchedEffect(Unit) {
        viewModel.snackbar.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (isTopLevel && !useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        topTabs.forEach { tab ->
                            val selected = currentDest?.hasRoute(tab.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo<AppRoute.Dashboard> { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isTopLevel && useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        topTabs.forEach { tab ->
                            val selected = currentDest?.hasRoute(tab.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo<AppRoute.Dashboard> { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("${tab.testTag}_rail")
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = AppRoute.Dashboard,
                    modifier = Modifier.weight(1f)
                ) {
                    composable<AppRoute.Dashboard> {
                        HomeDashboardScreen(
                            viewModel = viewModel,
                            onImportClick = { navController.navigate(AppRoute.ImportVideo) },
                            onManageKeysClick = { navController.navigate(AppRoute.ApiKeys) },
                            onOpenProject = { projectId, step ->
                                viewModel.selectProject(projectId)
                                navigateToPipelineStep(navController, projectId, step)
                            }
                        )
                    }
                    composable<AppRoute.ImportVideo> {
                        ImportVideoScreen(
                            viewModel = viewModel,
                            onNavigateToKeys = { navController.navigate(AppRoute.ApiKeys) },
                            onProjectCreated = { projectId ->
                                navController.navigate(AppRoute.VideoAnalysis(projectId))
                            }
                        )
                    }
                    composable<AppRoute.ApiKeys> {
                        ApiKeyManagementScreen(viewModel = viewModel)
                    }
                    composable<AppRoute.SettingsHistory> {
                        SettingsHistoryScreen(
                            viewModel = viewModel,
                            onOpenProject = { projectId ->
                                viewModel.selectProject(projectId)
                                navController.navigate(AppRoute.VideoPreview(projectId))
                            }
                        )
                    }

                    composable<AppRoute.VideoAnalysis> { entry ->
                        val route = entry.toRoute<AppRoute.VideoAnalysis>()
                        LaunchedEffect(route.projectId) { viewModel.selectProject(route.projectId) }
                        VideoAnalysisScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack(AppRoute.Dashboard, inclusive = false) },
                            onSelectStep = { step -> navigateToPipelineStep(navController, route.projectId, step) },
                            onGoToApiKeys = { navController.navigate(AppRoute.ApiKeys) }
                        )
                    }
                    composable<AppRoute.LanguageDetection> { entry ->
                        val route = entry.toRoute<AppRoute.LanguageDetection>()
                        LaunchedEffect(route.projectId) { viewModel.selectProject(route.projectId) }
                        LanguageDetectionScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onSelectStep = { step -> navigateToPipelineStep(navController, route.projectId, step) }
                        )
                    }
                    composable<AppRoute.CharacterVoices> { entry ->
                        val route = entry.toRoute<AppRoute.CharacterVoices>()
                        LaunchedEffect(route.projectId) { viewModel.selectProject(route.projectId) }
                        CharacterVoiceScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onSelectStep = { step -> navigateToPipelineStep(navController, route.projectId, step) }
                        )
                    }
                    composable<AppRoute.KhmerTranslation> { entry ->
                        val route = entry.toRoute<AppRoute.KhmerTranslation>()
                        LaunchedEffect(route.projectId) { viewModel.selectProject(route.projectId) }
                        KhmerTranslationScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onSelectStep = { step -> navigateToPipelineStep(navController, route.projectId, step) }
                        )
                    }
                    composable<AppRoute.VoicePreview> { entry ->
                        val route = entry.toRoute<AppRoute.VoicePreview>()
                        LaunchedEffect(route.projectId) { viewModel.selectProject(route.projectId) }
                        VoicePreviewScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onSelectStep = { step -> navigateToPipelineStep(navController, route.projectId, step) }
                        )
                    }
                    composable<AppRoute.AudioSync> { entry ->
                        val route = entry.toRoute<AppRoute.AudioSync>()
                        LaunchedEffect(route.projectId) { viewModel.selectProject(route.projectId) }
                        AudioSyncScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onSelectStep = { step -> navigateToPipelineStep(navController, route.projectId, step) }
                        )
                    }
                    composable<AppRoute.VideoPreview> { entry ->
                        val route = entry.toRoute<AppRoute.VideoPreview>()
                        LaunchedEffect(route.projectId) { viewModel.selectProject(route.projectId) }
                        VideoPreviewScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onSelectStep = { step -> navigateToPipelineStep(navController, route.projectId, step) }
                        )
                    }
                    composable<AppRoute.ExportVideo> { entry ->
                        val route = entry.toRoute<AppRoute.ExportVideo>()
                        LaunchedEffect(route.projectId) { viewModel.selectProject(route.projectId) }
                        ExportVideoScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onSelectStep = { step -> navigateToPipelineStep(navController, route.projectId, step) }
                        )
                    }
                }
            }
        }
    }
}
