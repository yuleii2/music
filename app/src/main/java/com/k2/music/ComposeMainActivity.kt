package com.k2.music

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.k2.music.ui.MusicApp
import com.k2.music.ui.RepositoryLoadState

class ComposeMainActivity : ComponentActivity() {
    private val appContainer by lazy(LazyThreadSafetyMode.NONE) {
        (application as MusicApplication).appContainer
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition {
            appContainer.repositoryState.value is RepositoryLoadState.Loading
        }
        enableEdgeToEdge()
        setContent {
            MusicApp(appContainer)
        }
    }

    override fun onStop() {
        appContainer.pauseForLifecycle()
        super.onStop()
    }
}
