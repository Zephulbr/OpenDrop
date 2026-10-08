package org.opendrop.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.opendrop.app.automation.Automation
import org.opendrop.app.settings.AppSettings
import org.opendrop.app.settings.Behavior
import org.opendrop.app.ui.components.NavRow
import org.opendrop.app.ui.components.ScreenTopBar
import org.opendrop.app.ui.components.SectionTitle
import org.opendrop.app.ui.components.SwitchRow
import org.opendrop.app.ui.theme.Dimens

@Composable
fun SettingsScreen(
    behavior: Behavior,
    onChange: ((Behavior) -> Behavior) -> Unit,
    onOpenAppearance: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar("Settings", onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            NavRow(
                title = "Appearance",
                subtitle = "Theme, accent color and haptics",
                onClick = onOpenAppearance,
            )

            Spacer(Modifier.height(Dimens.SectionGap))
            SectionTitle("Notifications")
            SwitchRow(
                title = "Low battery",
                subtitle = "Notify once when the earbuds drop to ${AppSettings.LOW_BATTERY_PERCENT} %",
                checked = behavior.lowBatteryAlert,
                onCheckedChange = { on -> onChange { it.copy(lowBatteryAlert = on) } },
            )

            Spacer(Modifier.height(Dimens.SectionGap))
            SectionTitle("Automation")
            SwitchRow(
                title = "Allow other apps",
                subtitle = "Tasker, MacroDroid and others can switch EQ, connect and disconnect, " +
                    "and receive state changes",
                checked = behavior.automation,
                onCheckedChange = { on -> onChange { it.copy(automation = on) } },
            )
            AnimatedVisibility(behavior.automation) {
                Text(
                    automationHelp(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Dimens.Gutter, vertical = 8.dp),
                )
            }

            Spacer(Modifier.height(Dimens.SectionGap))
            SectionTitle("About")
            Text(
                "OpenDrop is free software under the GPL-3.0. Not affiliated with Moondrop. " +
                    "Fonts: Inter and JetBrains Mono, SIL Open Font License 1.1.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.Gutter),
            )
        }
    }
}

private fun automationHelp(): String = """
    Send a broadcast to package ${Automation.PACKAGE} with one of these actions:

    ${Automation.ACTION_SET_EQ} (extra "${Automation.EXTRA_PRESET}": reference, basshead or monitor)
    ${Automation.ACTION_NEXT_EQ}
    ${Automation.ACTION_CONNECT}
    ${Automation.ACTION_DISCONNECT}

    OpenDrop broadcasts ${Automation.EVENT_STATE} when the connection, battery or EQ preset changes.
""".trimIndent()
