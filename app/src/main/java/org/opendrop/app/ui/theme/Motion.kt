package org.opendrop.app.ui.theme

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Motion tokens from DESIGN.md. Springs for anything spatial, short tweens for
 * fades and color. With reduced motion, spatial motion becomes a short tween.
 */
@Immutable
class MotionTokens(val reduced: Boolean) {
    /** Chips, toggles, thumb, press scale. */
    fun <T> spatialFast(): FiniteAnimationSpec<T> =
        if (reduced) tween(REDUCED_MS) else spring(dampingRatio = 0.9f, stiffness = 1400f)

    /** Section expand, row reveal, hero changes. */
    fun <T> spatialDefault(): FiniteAnimationSpec<T> =
        if (reduced) tween(REDUCED_MS) else spring(dampingRatio = 0.85f, stiffness = 700f)

    /** Screen transitions, battery ring. */
    fun <T> spatialSlow(): FiniteAnimationSpec<T> =
        if (reduced) tween(REDUCED_MS) else spring(dampingRatio = 0.9f, stiffness = 380f)

    /** Color and opacity on small things. */
    fun <T> effectsFast(): FiniteAnimationSpec<T> = tween(120, easing = FastOutSlowInEasing)

    /** Crossfades and content swaps. */
    fun <T> effectsDefault(): FiniteAnimationSpec<T> = tween(200, easing = FastOutSlowInEasing)

    companion object {
        const val REDUCED_MS = 150
    }
}

val LocalMotion = staticCompositionLocalOf { MotionTokens(reduced = false) }

/** True when the system animator scale is 0 ("Remove animations"). Updates live. */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = read()
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}
