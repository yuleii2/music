package com.k2.music.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.k2.music.ui.components.ErrorState
import com.k2.music.ui.components.LoadingSkeleton
import com.k2.music.ui.components.MusicAppScaffold
import com.k2.music.ui.components.MiniPlayer
import com.k2.music.ui.gateway.PlaybackSessionType
import com.k2.music.ui.gateway.PlaybackUiState
import com.k2.music.ui.navigation.AppDestination
import com.k2.music.ui.navigation.MusicNavHost
import com.k2.music.ui.navigation.navigateToRoot
import com.k2.music.ui.navigation.chordDetailRoute
import com.k2.music.ui.navigation.progressionEditorByIdRoute
import com.k2.music.ui.theme.MusicTheme
import com.k2.music.ui.learning.OnboardingRoute

@Composable
fun MusicApp(appContainer: AppContainer) {
    val settings by appContainer.appPreferences.settings.collectAsStateWithLifecycle()
    MusicTheme(settings) {
        val repositoryState by appContainer.repositoryState.collectAsStateWithLifecycle()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .semantics { testTagsAsResourceId = true },
        ) {
            when (val state = repositoryState) {
                RepositoryLoadState.Loading -> LoadingSkeleton()
                is RepositoryLoadState.Error -> ErrorState(
                    message = state.message,
                    onRetry = appContainer::retryRepositoryLoad,
                )
                is RepositoryLoadState.Ready -> ReadyApp(appContainer, state.services)
            }
        }
    }
}

@Composable
private fun ReadyApp(appContainer: AppContainer, services: CoreServices) {
    val learningProfile by appContainer.learningProfileStore.profile.collectAsStateWithLifecycle()
    if (!learningProfile.onboardingCompleted) {
        OnboardingRoute(
            appContainer.learningProfileStore,
            appContainer.appPreferences,
            services.practicePreferencesStore,
            learningProfile.updatedAt,
        )
        return
    }
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val progressionPlayback by services.progressionTransport.state.collectAsStateWithLifecycle()
    val chordPlayback by services.playbackController.state.collectAsStateWithLifecycle()
    val selected = AppDestination.roots.firstOrNull {
        currentRoute == it.route
    } ?: AppDestination.Home
    val isRootDestination = AppDestination.roots.any { currentRoute == it.route }

    MusicAppScaffold(
        selected = selected,
        onDestinationSelected = navController::navigateToRoot,
        showNavigation = isRootDestination,
        snackbarHostState = snackbarHostState,
        miniPlayer = if (!isRootDestination) {
            null
        } else if (progressionPlayback.isVisible) {
            {
                MiniPlayer(
                    title = progressionPlayback.title,
                    subtitle = when (progressionPlayback.sessionType) {
                        PlaybackSessionType.PROGRESSION ->
                            "${progressionPlayback.currentSymbol.ifBlank { "准备播放" }} · ${progressionPlayback.bpm} BPM"
                        PlaybackSessionType.METRONOME ->
                            "${progressionPlayback.timeSignature} · ${progressionPlayback.bpm} BPM"
                        PlaybackSessionType.NONE -> ""
                    },
                    isPlaying = progressionPlayback.isPlaying,
                    onOpen = {
                        progressionPlayback.progressionId?.let {
                            navController.navigate(progressionEditorByIdRoute(it)) { launchSingleTop = true }
                        } ?: navController.navigate("metronome") { launchSingleTop = true }
                    },
                    onPlayPause = services.progressionTransport::toggle,
                    onStop = services.progressionTransport::stop,
                )
            }
        } else {
            when (val chord = chordPlayback) {
                is PlaybackUiState.Playing -> {
                    {
                        MiniPlayer(
                            title = chord.symbol,
                            subtitle = chord.voicingName,
                            isPlaying = true,
                            onOpen = { navController.navigate(chordDetailRoute(chord.symbol)) { launchSingleTop = true } },
                            onPlayPause = null,
                            onStop = services.playbackController::stop,
                        )
                    }
                }
                is PlaybackUiState.Preparing -> {
                    {
                        MiniPlayer(
                            title = chord.symbol,
                            subtitle = "正在准备试听",
                            isPlaying = false,
                            onOpen = { navController.navigate(chordDetailRoute(chord.symbol)) { launchSingleTop = true } },
                            onPlayPause = null,
                            onStop = services.playbackController::stop,
                        )
                    }
                }
                else -> null
            }
        },
    ) {
        MusicNavHost(
            navController = navController,
            services = services,
            appPreferences = appContainer.appPreferences,
            snackbarHostState = snackbarHostState,
        )
    }
}
