package com.k2.music.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Home("home", "概览", Icons.Rounded.SpaceDashboard),
    Library("library", "和弦", Icons.Rounded.LibraryMusic),
    Workbench("workbench", "工具", Icons.Rounded.Tune),
    Practice("practice", "练习", Icons.Rounded.Timer),
    Profile("profile", "设置", Icons.Rounded.Settings),
    ;

    companion object {
        val roots = entries.toList()
    }
}
