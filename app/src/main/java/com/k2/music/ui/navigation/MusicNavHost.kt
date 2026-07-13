package com.k2.music.ui.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.k2.music.ui.CoreServices
import com.k2.music.ui.detail.ChordDetailRoute
import com.k2.music.ui.home.HomeRoute
import com.k2.music.ui.library.LibraryRoute
import com.k2.music.ui.recognition.RecognitionRoute
import com.k2.music.ui.transpose.TransposeRoute
import com.k2.music.ui.workbench.WorkbenchScreen
import com.k2.music.ui.progression.MetronomeRoute
import com.k2.music.ui.progression.ProgressionEditorRoute
import com.k2.music.ui.progression.ProgressionListRoute
import com.k2.music.ui.practice.PracticeHomeRoute
import com.k2.music.ui.practice.PracticeSetupRoute
import com.k2.music.ui.practice.PracticeSessionRoute
import com.k2.music.ui.practice.PracticeResultScreen
import com.k2.music.ui.ai.AiAssistantRoute
import com.k2.music.ui.ai.AiSettingsRoute
import com.k2.music.ui.export.ExportRoute
import com.k2.music.ui.profile.ProfileRoute
import com.k2.music.ui.gateway.ExportScopeUi
import com.k2.music.ui.gateway.PracticeConfigUi
import com.k2.music.ui.gateway.PracticeModeUi
import com.k2.music.ui.gateway.PracticeSwitchUi
import com.k2.music.ui.preferences.AppPreferences
import com.k2.music.ui.theme.LocalMusicMotion
import kotlinx.coroutines.launch

@Composable
fun MusicNavHost(
    navController: NavHostController,
    services: CoreServices,
    appPreferences: AppPreferences,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    SharedTransitionNavHost(
        navController = navController,
        services = services,
        appPreferences = appPreferences,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        scope = scope,
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionNavHost(
    navController: NavHostController,
    services: CoreServices,
    appPreferences: AppPreferences,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    SharedTransitionLayout {
        val sharedTransitionScope = this
        val motion = LocalMusicMotion.current
        NavHost(
            navController = navController,
            startDestination = AppDestination.Home.route,
            modifier = modifier,
            enterTransition = { fadeIn(tween(motion.standard)) },
            exitTransition = { fadeOut(tween(motion.quick)) },
            popEnterTransition = { fadeIn(tween(motion.standard)) },
            popExitTransition = { fadeOut(tween(motion.quick)) },
        ) {
        composable(AppDestination.Home.route) {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides sharedTransitionScope,
                LocalNavAnimatedVisibilityScope provides this@composable,
            ) {
                HomeRoute(
                    services = services,
                    snackbarHostState = snackbarHostState,
                    onNavigateToChord = { navController.navigate(chordDetailRoute(it)) { launchSingleTop = true } },
                    onNavigateToRecognition = { navController.navigate(ROUTE_RECOGNITION) },
                    onNavigateToTranspose = { navController.navigate(ROUTE_TRANSPOSE) },
                    onNavigateToProgressions = { navController.navigate(ROUTE_PROGRESSIONS) },
                    onStartPractice = { navController.navigateToRoot(AppDestination.Practice) },
                )
            }
        }
        composable(AppDestination.Library.route) {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides sharedTransitionScope,
                LocalNavAnimatedVisibilityScope provides this@composable,
            ) {
                LibraryRoute(
                    services = services,
                    snackbarHostState = snackbarHostState,
                    onNavigateToChord = { navController.navigate(chordDetailRoute(it)) { launchSingleTop = true } },
                    onNavigateToRecognition = { navController.navigate(ROUTE_RECOGNITION) },
                    onExportSelection = { symbols ->
                        navController.navigate(exportRoute(ExportScopeUi.SELECTION, symbols))
                    },
                )
            }
        }
        composable(AppDestination.Workbench.route) {
            val settings by appPreferences.settings.collectAsStateWithLifecycle()
            WorkbenchScreen(
                recentToolId = settings.recentToolId,
                onToolUsed = appPreferences::setRecentTool,
                onRecognition = { navController.navigate(ROUTE_RECOGNITION) },
                onTranspose = { navController.navigate(ROUTE_TRANSPOSE) },
                onProgressions = { navController.navigate(ROUTE_PROGRESSIONS) },
                onMetronome = { navController.navigate(ROUTE_METRONOME) },
                onAiAssistant = { navController.navigate(aiAssistantRoute()) },
            )
        }
        composable(AppDestination.Practice.route) {
            PracticeHomeRoute(
                services = services,
                onSetup = { navController.navigate(practiceSetupRoute(it)) },
                onAiPlan = { navController.navigate(aiAssistantRoute("practice", it)) },
            )
        }
        composable(AppDestination.Profile.route) {
            ProfileRoute(
                services = services,
                appPreferences = appPreferences,
                onAiAssistant = { navController.navigate(aiAssistantRoute()) },
                onAiSettings = { navController.navigate(ROUTE_AI_SETTINGS) },
                onExportFavorites = { navController.navigate(exportRoute(ExportScopeUi.FAVORITES)) },
            )
        }
        composable(
            route = CHORD_DETAIL_PATTERN,
            arguments = listOf(navArgument("symbol") { type = NavType.StringType }),
        ) {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides sharedTransitionScope,
                LocalNavAnimatedVisibilityScope provides this@composable,
            ) {
                ChordDetailRoute(
                    services = services,
                    snackbarHostState = snackbarHostState,
                    onNavigateBack = navController::navigateUp,
                    onExportCurrent = { symbol, index ->
                        navController.navigate(exportRoute(ExportScopeUi.CURRENT_VOICING, listOf(symbol), index))
                    },
                    onExportAll = { symbol ->
                        navController.navigate(exportRoute(ExportScopeUi.CHORD_ALL, listOf(symbol)))
                    },
                    onExplainWithAi = { symbol ->
                        navController.navigate(aiAssistantRoute("explain", symbol))
                    },
                )
            }
        }
        composable(ROUTE_RECOGNITION) {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides sharedTransitionScope,
                LocalNavAnimatedVisibilityScope provides this@composable,
            ) {
                RecognitionRoute(
                    services = services,
                    snackbarHostState = snackbarHostState,
                    onBack = navController::navigateUp,
                    onOpenChord = { navController.navigate(chordDetailRoute(it)) },
                )
            }
        }
        composable(ROUTE_TRANSPOSE) {
            TransposeRoute(
                services = services,
                snackbarHostState = snackbarHostState,
                onBack = navController::navigateUp,
                onOpenChord = { navController.navigate(chordDetailRoute(it)) },
                onAddProgression = {
                    navController.navigate(progressionEditorRoute(it))
                },
            )
        }
        composable(ROUTE_PROGRESSIONS) {
            ProgressionListRoute(
                services = services,
                snackbarHostState = snackbarHostState,
                onBack = navController::navigateUp,
                onOpenEditor = {
                    navController.navigate(progressionEditorByIdRoute(it)) { launchSingleTop = true }
                },
            )
        }
        composable(ROUTE_METRONOME) {
            MetronomeRoute(services = services, onBack = navController::navigateUp)
        }
        composable(
            route = AI_ASSISTANT_PATTERN,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType; defaultValue = "" },
                navArgument("symbol") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            AiAssistantRoute(
                services = services,
                snackbarHostState = snackbarHostState,
                onBack = navController::navigateUp,
                onSettings = { navController.navigate(ROUTE_AI_SETTINGS) },
                onOpenChord = { navController.navigate(chordDetailRoute(it)) },
                onOpenProgression = { navController.navigate(progressionEditorRoute(it)) },
                onOpenPractice = { navController.navigateToRoot(AppDestination.Practice) },
            )
        }
        composable(ROUTE_AI_SETTINGS) {
            AiSettingsRoute(services, snackbarHostState, navController::navigateUp)
        }
        composable(
            route = PROGRESSION_EDITOR_PATTERN,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType; defaultValue = "" },
                navArgument("seed") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            ProgressionEditorRoute(
                services = services,
                snackbarHostState = snackbarHostState,
                onBack = navController::navigateUp,
                onAiOptimize = { navController.navigate(aiAssistantRoute("optimize", it)) },
            )
        }
        composable(
            route = PRACTICE_SETUP_PATTERN,
            arguments = practiceArguments(),
        ) {
            PracticeSetupRoute(
                services,
                snackbarHostState,
                navController::navigateUp,
                onStart = { navController.navigate(practiceSessionRoute(it)) },
            )
        }
        composable(
            route = PRACTICE_SESSION_PATTERN,
            arguments = practiceArguments(),
        ) {
            PracticeSessionRoute(
                services,
                snackbarHostState,
                onFinished = { result, config ->
                    navController.navigate(practiceResultRoute(result, config))
                },
                onAbandon = navController::navigateUp,
            )
        }
        composable(
            route = PRACTICE_RESULT_PATTERN,
            arguments = practiceResultArguments(),
        ) { entry ->
            val args = requireNotNull(entry.arguments)
            val config = PracticeConfigUi(
                mode = enumValue(args.getString("mode"), PracticeModeUi.TWO_CHORD),
                symbols = args.getString("symbols").orEmpty(),
                durationSeconds = args.getInt("goal"),
                bpm = args.getInt("bpm"),
                timeSignature = args.getString("signature").orEmpty(),
                switchMode = enumValue(args.getString("switch"), PracticeSwitchUi.EACH_MEASURE),
                accentFirstBeat = args.getBoolean("accent"),
                allowBarre = args.getBoolean("barre"),
                maxFret = args.getInt("maxFret"),
            )
            PracticeResultScreen(
                seconds = args.getInt("seconds"),
                count = args.getInt("count"),
                streak = args.getInt("streak"),
                symbols = args.getString("symbols").orEmpty(),
                previous = args.getInt("previous").takeIf { it >= 0 },
                onAgain = { navController.navigate(practiceSessionRoute(config)) },
                onAdjust = { navController.navigate(practiceSetupRoute(config)) },
                onDone = { navController.navigateToRoot(AppDestination.Practice) },
            )
        }
        composable(
            route = EXPORT_PATTERN,
            arguments = listOf(
                navArgument("scope") { type = NavType.StringType },
                navArgument("symbols") { type = NavType.StringType; defaultValue = "" },
                navArgument("index") { type = NavType.IntType; defaultValue = 0 },
            ),
        ) {
            ExportRoute(services, snackbarHostState, navController::navigateUp)
        }
        }
    }
}

const val ROUTE_RECOGNITION = "recognition"
const val ROUTE_TRANSPOSE = "transpose-capo"
const val ROUTE_PROGRESSIONS = "progressions"
const val ROUTE_METRONOME = "metronome"
const val ROUTE_AI_SETTINGS = "ai-settings"

private fun practiceArguments() = listOf(
    navArgument("mode") { type = NavType.StringType },
    navArgument("symbols") { type = NavType.StringType },
    navArgument("duration") { type = NavType.IntType },
    navArgument("bpm") { type = NavType.IntType },
    navArgument("signature") { type = NavType.StringType },
    navArgument("switch") { type = NavType.StringType },
    navArgument("accent") { type = NavType.BoolType },
    navArgument("barre") { type = NavType.BoolType },
    navArgument("maxFret") { type = NavType.IntType },
)

private fun practiceResultArguments() = listOf(
    navArgument("seconds") { type = NavType.IntType },
    navArgument("count") { type = NavType.IntType },
    navArgument("streak") { type = NavType.IntType },
    navArgument("symbols") { type = NavType.StringType },
    navArgument("previous") { type = NavType.IntType },
    navArgument("mode") { type = NavType.StringType },
    navArgument("goal") { type = NavType.IntType },
    navArgument("bpm") { type = NavType.IntType },
    navArgument("signature") { type = NavType.StringType },
    navArgument("switch") { type = NavType.StringType },
    navArgument("accent") { type = NavType.BoolType },
    navArgument("barre") { type = NavType.BoolType },
    navArgument("maxFret") { type = NavType.IntType },
)

private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: fallback
