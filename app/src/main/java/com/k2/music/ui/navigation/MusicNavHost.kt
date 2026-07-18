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
import com.k2.music.ui.profile.PracticeProgressRoute
import com.k2.music.ui.backup.DataBackupRoute
import com.k2.music.ui.song.SongDetailRoute
import com.k2.music.ui.song.SongEditorRoute
import com.k2.music.ui.song.SongImportPreviewRoute
import com.k2.music.ui.song.SongImportRoute
import com.k2.music.ui.song.SongLibraryRoute
import com.k2.music.ui.song.SongPracticeRoute
import com.k2.music.song.SongPracticeMode
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
                    onNavigateToTools = { navController.navigateToRoot(AppDestination.Workbench) },
                    onStartPractice = { navController.navigate(practiceSessionRoute(it)) },
                    onAdjustPractice = { navController.navigate(practiceSetupRoute(it)) },
                    onSongTask = { task ->
                        when (task.mode) {
                            SongPracticeMode.PERFORMANCE -> navController.navigate(
                                songPracticeRoute(
                                    task.songId,
                                    task.sectionId,
                                    task.bpm,
                                    task.transposeSemitones,
                                    task.capoFret,
                                    task.loopEnabled,
                                    task.showFretboard,
                                ),
                            )
                            SongPracticeMode.GUIDED_TRANSITION -> navController.navigate(
                                practiceSessionRoute(
                                    PracticeConfigUi(
                                        mode = if (task.transition == null) PracticeModeUi.MULTI_CHORD else PracticeModeUi.TWO_CHORD,
                                        symbols = task.transition?.let { "${it.fromChord} ${it.toChord}" }.orEmpty(),
                                        durationSeconds = 120,
                                        bpm = task.bpm,
                                        timeSignature = task.timeSignature,
                                        songId = task.songId,
                                        songSectionId = task.sectionId.orEmpty(),
                                        songTransitionFrom = task.transition?.fromChord.orEmpty(),
                                        songTransitionTo = task.transition?.toChord.orEmpty(),
                                        useProgressionRhythm = true,
                                    ),
                                ),
                            )
                            null -> navController.navigate(songDetailRoute(task.songId))
                        }
                    },
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
                onStartDirect = { navController.navigate(practiceSessionRoute(it)) },
                onAiPlan = { navController.navigate(aiAssistantRoute("practice", it)) },
                onSongLibrary = { navController.navigate(ROUTE_SONG_LIBRARY) },
            )
        }
        composable(AppDestination.Profile.route) {
            ProfileRoute(
                services = services,
                appPreferences = appPreferences,
                onAiAssistant = { navController.navigate(aiAssistantRoute()) },
                onAiSettings = { navController.navigate(ROUTE_AI_SETTINGS) },
                onExportFavorites = { navController.navigate(exportRoute(ExportScopeUi.FAVORITES)) },
                onPracticeProgress = { navController.navigate(ROUTE_PRACTICE_PROGRESS) },
                onDataBackup = { navController.navigate(ROUTE_DATA_BACKUP) },
            )
        }
        composable(ROUTE_PRACTICE_PROGRESS) {
            PracticeProgressRoute(services, navController::navigateUp)
        }
        composable(ROUTE_DATA_BACKUP) {
            DataBackupRoute(services, navController::navigateUp)
        }
        composable(ROUTE_SONG_LIBRARY) {
            SongLibraryRoute(
                services = services,
                onBack = navController::navigateUp,
                onImport = { navController.navigate(ROUTE_SONG_IMPORT) },
                onManualCreate = { navController.navigate(songEditorRoute("new")) },
                onOpenSong = { navController.navigate(songDetailRoute(it)) },
            )
        }
        composable(ROUTE_SONG_IMPORT) {
            SongImportRoute(
                services = services,
                onBack = navController::navigateUp,
                onPreview = { navController.navigate(ROUTE_SONG_IMPORT_PREVIEW) },
            )
        }
        composable(ROUTE_SONG_IMPORT_PREVIEW) {
            SongImportPreviewRoute(
                services = services,
                onBack = navController::navigateUp,
                onSaved = { songId ->
                    navController.navigate(songDetailRoute(songId)) {
                        popUpTo(ROUTE_SONG_IMPORT) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = SONG_DETAIL_PATTERN,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) {
            SongDetailRoute(
                services = services,
                onBack = navController::navigateUp,
                onEdit = { navController.navigate(songEditorRoute(it)) },
                onGuidedPractice = { config -> navController.navigate(practiceSessionRoute(config)) },
                onPerformance = { songId, sectionId -> navController.navigate(songPracticeRoute(songId, sectionId)) },
            )
        }
        composable(
            route = SONG_EDITOR_PATTERN,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) {
            SongEditorRoute(
                services = services,
                onBack = navController::navigateUp,
                onSaved = { songId ->
                    navController.navigate(songDetailRoute(songId)) {
                        popUpTo(ROUTE_SONG_LIBRARY)
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(
            route = SONG_PRACTICE_PATTERN,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("sectionId") { type = NavType.StringType; defaultValue = "" },
                navArgument("restoreBpm") { type = NavType.IntType; defaultValue = 0 },
                navArgument("restoreTranspose") { type = NavType.IntType; defaultValue = 99 },
                navArgument("restoreCapo") { type = NavType.IntType; defaultValue = -1 },
                navArgument("restoreLoop") { type = NavType.IntType; defaultValue = -1 },
                navArgument("restoreFretboard") { type = NavType.IntType; defaultValue = -1 },
            ),
        ) {
            SongPracticeRoute(services, navController::navigateUp)
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
                    onStartPractice = { symbol ->
                        val partner = if (symbol == "C") "G" else "C"
                        navController.navigate(practiceSetupRoute(PracticeConfigUi(symbols = "$symbol $partner", bpm = 50, allowBarre = false, maxFret = 5)))
                    },
                    onAddProgression = { symbol -> navController.navigate(progressionEditorRoute(symbol)) },
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
                    onPractice = { symbol ->
                        val partner = if (symbol == "C") "G" else "C"
                        navController.navigate(practiceSetupRoute(PracticeConfigUi(symbols = "$symbol $partner")))
                    },
                    onAddProgression = { navController.navigate(progressionEditorRoute(it)) },
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
                onPractice = { navController.navigate(practiceSessionRoute(it)) },
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
                sourceProgressionId = args.getString("progressionId").orEmpty(),
                useProgressionRhythm = args.getBoolean("progressionRhythm"),
                songId = args.getString("songId").orEmpty(),
                songSectionId = args.getString("songSectionId").orEmpty(),
                songTransitionFrom = args.getString("songFrom").orEmpty(),
                songTransitionTo = args.getString("songTo").orEmpty(),
            )
            PracticeResultScreen(
                seconds = args.getInt("seconds"),
                attempts = args.getInt("attempts"),
                successes = args.getInt("successes"),
                failures = args.getInt("failures"),
                streak = args.getInt("streak"),
                symbols = args.getString("symbols").orEmpty(),
                previousRate = args.getInt("previousRate").takeIf { it >= 0 }?.div(10_000.0),
                hardestTransition = args.getString("hardest").orEmpty().ifBlank { null },
                suggestedBpm = args.getInt("suggestedBpm"),
                suggestionReason = args.getString("suggestion").orEmpty(),
                onAgain = { navController.navigate(practiceSessionRoute(config)) },
                onSuggestedAgain = {
                    navController.navigate(practiceSessionRoute(config.copy(bpm = args.getInt("suggestedBpm"))))
                },
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
const val ROUTE_PRACTICE_PROGRESS = "practice-progress"
const val ROUTE_DATA_BACKUP = "data-backup"
const val ROUTE_SONG_LIBRARY = "song-library"
const val ROUTE_SONG_IMPORT = "song-import"
const val ROUTE_SONG_IMPORT_PREVIEW = "song-import-preview"

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
    navArgument("progressionId") { type = NavType.StringType; defaultValue = "" },
    navArgument("progressionRhythm") { type = NavType.BoolType; defaultValue = false },
    navArgument("songId") { type = NavType.StringType; defaultValue = "" },
    navArgument("songSectionId") { type = NavType.StringType; defaultValue = "" },
    navArgument("songFrom") { type = NavType.StringType; defaultValue = "" },
    navArgument("songTo") { type = NavType.StringType; defaultValue = "" },
)

private fun practiceResultArguments() = listOf(
    navArgument("seconds") { type = NavType.IntType },
    navArgument("attempts") { type = NavType.IntType },
    navArgument("successes") { type = NavType.IntType },
    navArgument("failures") { type = NavType.IntType },
    navArgument("streak") { type = NavType.IntType },
    navArgument("symbols") { type = NavType.StringType },
    navArgument("previousRate") { type = NavType.IntType },
    navArgument("hardest") { type = NavType.StringType },
    navArgument("suggestedBpm") { type = NavType.IntType },
    navArgument("suggestion") { type = NavType.StringType },
    navArgument("mode") { type = NavType.StringType },
    navArgument("goal") { type = NavType.IntType },
    navArgument("bpm") { type = NavType.IntType },
    navArgument("signature") { type = NavType.StringType },
    navArgument("switch") { type = NavType.StringType },
    navArgument("accent") { type = NavType.BoolType },
    navArgument("barre") { type = NavType.BoolType },
    navArgument("maxFret") { type = NavType.IntType },
    navArgument("progressionId") { type = NavType.StringType; defaultValue = "" },
    navArgument("progressionRhythm") { type = NavType.BoolType; defaultValue = false },
    navArgument("songId") { type = NavType.StringType; defaultValue = "" },
    navArgument("songSectionId") { type = NavType.StringType; defaultValue = "" },
    navArgument("songFrom") { type = NavType.StringType; defaultValue = "" },
    navArgument("songTo") { type = NavType.StringType; defaultValue = "" },
)

private inline fun <reified T : Enum<T>> enumValue(raw: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: fallback
