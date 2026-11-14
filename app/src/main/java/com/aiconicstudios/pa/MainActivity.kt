package com.aiconicstudios.pa

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiconicstudios.pa.data.Repository
import com.aiconicstudios.pa.ui.PaTheme
import com.aiconicstudios.pa.ui.SettingsScreen
import com.aiconicstudios.pa.ui.TimetableScreen
import com.aiconicstudios.pa.ui.WorkspaceScreen

sealed interface Screen {
    data object Timetable : Screen
    data object Settings : Screen
    data class Workspace(val activityId: String) : Screen
}

class MainActivity : ComponentActivity() {

    private var screen by mutableStateOf<Screen>(Screen.Timetable)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            PaTheme {
                RequestNotificationPermission()
                App(screen) { screen = it }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(EXTRA_OPEN_WORKSPACE)?.let { screen = Screen.Workspace(it) }
    }

    companion object {
        const val EXTRA_OPEN_WORKSPACE = "open_workspace"
    }
}

@Composable
private fun App(screen: Screen, navigate: (Screen) -> Unit) {
    val state by Repository.state.collectAsStateWithLifecycle()
    BackHandler(enabled = screen != Screen.Timetable) { navigate(Screen.Timetable) }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            if (targetState == Screen.Timetable)
                (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
            else
                (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 } + fadeOut())
        },
        label = "nav",
    ) { s ->
        when (s) {
            Screen.Timetable -> TimetableScreen(
                state = state,
                onOpenWorkspace = { navigate(Screen.Workspace(it)) },
                onOpenSettings = { navigate(Screen.Settings) },
            )
            Screen.Settings -> SettingsScreen(state.settings, onBack = { navigate(Screen.Timetable) })
            is Screen.Workspace -> {
                val activity = state.activities.firstOrNull { it.id == s.activityId }
                if (activity == null) LaunchedEffect(Unit) { navigate(Screen.Timetable) }
                else WorkspaceScreen(
                    activity = activity,
                    workspace = state.workspace(activity.id),
                    settings = state.settings,
                    allActivities = state.activities,
                    onBack = { navigate(Screen.Timetable) },
                    onOpenSettings = { navigate(Screen.Settings) },
                )
            }
        }
    }
}

@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}
