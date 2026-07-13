package com.k2.music.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.k2.music.ui.navigation.AppDestination

@Composable
fun MusicAppScaffold(
    selected: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
    showNavigation: Boolean = true,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    miniPlayer: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp
        if (useRail && showNavigation) {
            Row(Modifier.fillMaxSize()) {
                MusicNavigationRail(selected, onDestinationSelected)
                Scaffold(
                    modifier = Modifier.weight(1f),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = { miniPlayer?.invoke() },
                ) { padding ->
                    Box(Modifier.padding(padding)) { content() }
                }
            }
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    Column {
                        if (miniPlayer != null) miniPlayer()
                        if (showNavigation) MusicNavigationBar(selected, onDestinationSelected)
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding)) { content() }
            }
        }
    }
}

@Composable
fun MusicNavigationBar(
    selected: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
) {
    NavigationBar {
        AppDestination.roots.forEach { destination ->
            NavigationBarItem(
                modifier = Modifier.testTag("nav_${destination.route}"),
                selected = destination == selected,
                onClick = { onDestinationSelected(destination) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(destination.label) },
            )
        }
    }
}

@Composable
fun MusicNavigationRail(
    selected: AppDestination,
    onDestinationSelected: (AppDestination) -> Unit,
) {
    NavigationRail {
        AppDestination.roots.forEach { destination ->
            NavigationRailItem(
                modifier = Modifier.testTag("nav_${destination.route}"),
                selected = destination == selected,
                onClick = { onDestinationSelected(destination) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(destination.label) },
            )
        }
    }
}
