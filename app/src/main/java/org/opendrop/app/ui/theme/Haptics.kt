package org.opendrop.app.ui.theme

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf

/** Subtle haptics from DESIGN.md. Uses system constants so it follows the user's haptic settings. */
@Stable
class Haptics(private val view: View?, private val enabled: Boolean) {
    /** A segmented choice, e.g. an EQ preset. */
    fun segment() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.SEGMENT_TICK
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        },
    )

    /** One slider step. */
    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)

    fun confirm() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        },
    )

    fun reject() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        },
    )

    private fun perform(constant: Int) {
        if (enabled) view?.performHapticFeedback(constant)
    }
}

val LocalHaptics = staticCompositionLocalOf { Haptics(view = null, enabled = false) }
