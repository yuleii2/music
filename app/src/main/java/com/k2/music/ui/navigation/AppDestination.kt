package com.k2.music.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Home("home", "首页", Icons.Rounded.Home),
    Library("library", "和弦", Icons.Rounded.LibraryMusic),
    Workbench("workbench", "工具", Icons.Rounded.Build),
    Practice("practice", "练习", Icons.Rounded.School),
    Profile("profile", "我的", Icons.Rounded.Person),
    ;

    companion object {
        val roots = entries.toList()
    }
}
