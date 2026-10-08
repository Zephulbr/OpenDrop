package org.opendrop.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.opendrop.app.ui.theme.Dimens

private val steps = listOf(
    "Pair first" to "Pair your Moondrop earbuds in Android's Bluetooth settings. OpenDrop finds them there.",
    "Close MOONDROP Link" to "Only one app can talk to the earbuds at a time. Swipe Link away from recent apps.",
    "Honest controls" to "OpenDrop shows only what your earbuds' firmware offers. Models it doesn't know yet " +
        "are read-only, and a device report from Device info helps add them.",
    "Also without earbuds" to "The phone EQ works with any headphones, and USB DACs show up when plugged in.",
)

/** First run: what to do before connecting, and what to expect. Shown once. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.Gutter, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Welcome to OpenDrop", style = MaterialTheme.typography.headlineMedium)
        Text(
            "An open-source app for Moondrop audio devices. Not affiliated with Moondrop.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        steps.forEachIndexed { i, (title, body) ->
            Row {
                Text(
                    "${i + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(28.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Get started") }
    }
}
