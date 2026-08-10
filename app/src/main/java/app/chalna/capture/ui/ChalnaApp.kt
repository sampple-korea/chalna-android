package app.chalna.capture.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

internal enum class ChalnaRoute { HOME, GALLERY, SETTINGS, HELP, PRIVACY, PLAYER }

@Composable
fun ChalnaApp(dependencies: UiDependencies) {
    val state by dependencies.state.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val systemDark = (configuration.uiMode and 0x30) == 0x20
    val colors =
        when (state.appearance) {
            AppearanceMode.NIGHT -> NightColors
            AppearanceMode.MIST -> MistColors
            AppearanceMode.SYSTEM -> if (systemDark) NightColors else MistColors
        }
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycleOwner, dependencies) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    resumed = true
                    dependencies.refreshSetup()
                } else if (event == Lifecycle.Event.ON_PAUSE) {
                    resumed = false
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val activity = LocalActivity.current
    SideEffect {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars =
                colors.background.luminance() > .5f
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars =
                colors.background.luminance() > .5f
        }
    }
    CompositionLocalProvider(LocalChalnaColors provides colors) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            if (state.player == null) ChalnaBackdrop(state.reducedMotion || state.powerSaver || !resumed)
            if (state.setupComplete) MainShell(state, dependencies) else SetupScreen(state, dependencies)
        }
    }
}

@Composable
private fun MainShell(
    state: ChalnaUiState,
    dependencies: UiDependencies,
) {
    var route by rememberSaveable { mutableStateOf(if (state.player == null) ChalnaRoute.HOME else ChalnaRoute.PLAYER) }
    var playerOrigin by rememberSaveable { mutableStateOf(ChalnaRoute.HOME) }
    LaunchedEffect(state.player?.item?.id) {
        if (state.player != null) {
            if (route != ChalnaRoute.PLAYER) playerOrigin = route
            route = ChalnaRoute.PLAYER
        } else if (route == ChalnaRoute.PLAYER) {
            route = playerOrigin
        }
    }
    BackHandler(enabled = route != ChalnaRoute.HOME) {
        if (route == ChalnaRoute.PLAYER) dependencies.closePlayer()
        route = if (route == ChalnaRoute.PLAYER) playerOrigin else ChalnaRoute.HOME
    }
    AnimatedContent(
        targetState = route,
        transitionSpec = {
            val enteringForward = targetState.depth >= initialState.depth
            if (state.reducedMotion) {
                fadeIn(tween(140)) togetherWith fadeOut(tween(110))
            } else {
                val direction = if (enteringForward) 1 else -1
                (fadeIn(tween(300)) + slideInVertically(tween(320)) { it * direction / 28 }) togetherWith
                    (fadeOut(tween(220)) + slideOutVertically(tween(260)) { -it * direction / 36 })
            }
        },
        label = "chalnaRoute",
    ) { current ->
        when (current) {
            ChalnaRoute.HOME -> HomeScreen(state, dependencies) { route = it }
            ChalnaRoute.GALLERY ->
                GalleryScreen(state, dependencies, { route = ChalnaRoute.HOME }) { id ->
                    playerOrigin = ChalnaRoute.GALLERY
                    dependencies.openPlayer(id)
                    route = ChalnaRoute.PLAYER
                }
            ChalnaRoute.SETTINGS -> SettingsScreen(state, dependencies, { route = ChalnaRoute.HOME }) { route = it }
            ChalnaRoute.HELP -> HelpScreen(dependencies) { route = ChalnaRoute.SETTINGS }
            ChalnaRoute.PRIVACY -> PrivacyScreen { route = ChalnaRoute.SETTINGS }
            ChalnaRoute.PLAYER ->
                state.player?.let {
                    PlayerScreen(it, dependencies) {
                        dependencies.closePlayer()
                        route = playerOrigin
                    }
                } ?: GalleryScreen(state, dependencies, { route = ChalnaRoute.HOME }) { id -> dependencies.openPlayer(id) }
        }
    }
}

private val ChalnaRoute.depth: Int
    get() =
        when (this) {
            ChalnaRoute.HOME -> 0
            ChalnaRoute.GALLERY, ChalnaRoute.SETTINGS -> 1
            ChalnaRoute.HELP, ChalnaRoute.PRIVACY, ChalnaRoute.PLAYER -> 2
        }
