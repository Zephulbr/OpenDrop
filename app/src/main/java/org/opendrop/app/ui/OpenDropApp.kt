package org.opendrop.app.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.coroutines.cancellation.CancellationException
import org.opendrop.app.device.DeviceViewModel
import org.opendrop.app.settings.Appearance
import org.opendrop.app.ui.theme.LocalMotion
import org.opendrop.app.ui.theme.MotionTokens

private enum class Destination { Home, DeviceInfo, Appearance }

@Composable
fun OpenDropApp(
    viewModel: DeviceViewModel,
    appearance: Appearance,
    onAppearanceChange: (debounce: Boolean, transform: (Appearance) -> Appearance) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val motion = LocalMotion.current
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    // 0..1 while the user drags the system back gesture (Android 14+).
    var backProgress by remember { mutableFloatStateOf(0f) }

    fun open(target: Destination) {
        backProgress = 0f
        destination = target
    }

    PredictiveBackHandler(enabled = destination != Destination.Home) { events ->
        try {
            events.collect { backProgress = it.progress }
            destination = Destination.Home
        } catch (e: CancellationException) {
            backProgress = 0f
            throw e
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AnimatedContent(
            targetState = destination,
            transitionSpec = { sharedAxis(motion, forward = targetState != Destination.Home) },
            label = "screens",
        ) { screen ->
            val back = { destination = Destination.Home }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Predictive back preview: the sub-screen shrinks and shifts with the gesture.
                        if (screen != Destination.Home) {
                            val scale = 1f - 0.1f * backProgress
                            scaleX = scale
                            scaleY = scale
                            translationX = size.width * 0.05f * backProgress
                        }
                    }
                    .background(MaterialTheme.colorScheme.background),
            ) {
                when (screen) {
                    Destination.Home -> HomeScreen(
                        state = state,
                        onPermissionResult = viewModel::refresh,
                        onConnect = viewModel::connect,
                        onDisconnect = viewModel::disconnect,
                        onEq = viewModel::setEq,
                        onVolume = viewModel::setVolume,
                        onOpenDeviceInfo = { open(Destination.DeviceInfo) },
                        onOpenAppearance = { open(Destination.Appearance) },
                    )
                    Destination.DeviceInfo -> DeviceInfoScreen(state, onBack = back)
                    Destination.Appearance -> AppearanceScreen(appearance, onAppearanceChange, onBack = back)
                }
            }
        }
    }
}

/** Shared-axis horizontal slide + fade; a plain crossfade with reduced motion. */
private fun sharedAxis(motion: MotionTokens, forward: Boolean): ContentTransform {
    if (motion.reduced) {
        return fadeIn(motion.effectsFast()) togetherWith fadeOut(motion.effectsFast())
    }
    val direction = if (forward) 1 else -1
    return (slideInHorizontally(motion.spatialSlow()) { w -> direction * w / 4 } + fadeIn(motion.effectsDefault()))
        .togetherWith(
            slideOutHorizontally(motion.spatialSlow()) { w -> -direction * w / 4 } + fadeOut(motion.effectsFast()),
        )
}
