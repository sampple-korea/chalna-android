package app.chalna.capture.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle

internal enum class ChalnaRoute { HOME, GALLERY, SETTINGS, HELP, PRIVACY, PLAYER }

@Composable
fun ChalnaApp(dependencies: UiDependencies) {
    val state by dependencies.state.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val systemDark = (configuration.uiMode and 0x30) == 0x20
    val colors = when (state.appearance) {
        AppearanceMode.NIGHT -> NightColors
        AppearanceMode.MIST -> MistColors
        AppearanceMode.SYSTEM -> if (systemDark) NightColors else MistColors
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, dependencies) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) dependencies.refreshSetup()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    CompositionLocalProvider(LocalChalnaColors provides colors) {
        Box(Modifier.fillMaxSize().background(colors.background).systemBarsPadding()) {
            ChalnaBackdrop(state.reducedMotion || state.powerSaver)
            if (state.setupComplete) MainShell(state, dependencies) else SetupScreen(state, dependencies)
        }
    }
}

@Composable
private fun MainShell(state: ChalnaUiState, dependencies: UiDependencies) {
    var route by rememberSaveable { mutableStateOf(if (state.player == null) ChalnaRoute.HOME else ChalnaRoute.PLAYER) }
    LaunchedEffect(state.player?.item?.id) {
        if (state.player != null) route = ChalnaRoute.PLAYER
        else if (route == ChalnaRoute.PLAYER) route = ChalnaRoute.GALLERY
    }
    BackHandler(enabled = route != ChalnaRoute.HOME) {
        if (route == ChalnaRoute.PLAYER) dependencies.closePlayer()
        route = if (route == ChalnaRoute.PLAYER) ChalnaRoute.GALLERY else ChalnaRoute.HOME
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
            ChalnaRoute.GALLERY -> GalleryScreen(state, dependencies, { route = ChalnaRoute.HOME }) { id ->
                dependencies.openPlayer(id)
                route = ChalnaRoute.PLAYER
            }
            ChalnaRoute.SETTINGS -> SettingsScreen(state, dependencies, { route = ChalnaRoute.HOME }) { route = it }
            ChalnaRoute.HELP -> HelpScreen(dependencies) { route = ChalnaRoute.SETTINGS }
            ChalnaRoute.PRIVACY -> PrivacyScreen { route = ChalnaRoute.SETTINGS }
            ChalnaRoute.PLAYER -> state.player?.let {
                PlayerScreen(it, dependencies) {
                    dependencies.closePlayer()
                    route = ChalnaRoute.GALLERY
                }
            } ?: GalleryScreen(state, dependencies, { route = ChalnaRoute.HOME }) { id -> dependencies.openPlayer(id) }
        }
    }
}

private val ChalnaRoute.depth: Int
    get() = when (this) {
        ChalnaRoute.HOME -> 0
        ChalnaRoute.GALLERY, ChalnaRoute.SETTINGS -> 1
        ChalnaRoute.HELP, ChalnaRoute.PRIVACY, ChalnaRoute.PLAYER -> 2
    }
