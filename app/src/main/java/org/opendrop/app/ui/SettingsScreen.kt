package org.opendrop.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.opendrop.app.R
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
        ScreenTopBar(stringResource(R.string.settings), onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            NavRow(
                title = stringResource(R.string.appearance),
                subtitle = stringResource(R.string.settings_appearance_subtitle),
                onClick = onOpenAppearance,
            )

            Spacer(Modifier.height(Dimens.SectionGap))
            SectionTitle(stringResource(R.string.settings_audio))
            val context = LocalContext.current
            var codecNote by remember { mutableStateOf<String?>(null) }
            NavRow(
                title = stringResource(R.string.settings_codec),
                subtitle = stringResource(R.string.settings_codec_subtitle),
                onClick = { codecNote = openDeveloperOptions(context) },
            )
            codecNote?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Dimens.Gutter, vertical = 8.dp),
                )
            }

            Spacer(Modifier.height(Dimens.SectionGap))
            SectionTitle(stringResource(R.string.settings_notifications))
            SwitchRow(
                title = stringResource(R.string.settings_low_battery),
                subtitle = stringResource(R.string.settings_low_battery_subtitle, AppSettings.LOW_BATTERY_PERCENT),
                checked = behavior.lowBatteryAlert,
                onCheckedChange = { on -> onChange { it.copy(lowBatteryAlert = on) } },
            )

            Spacer(Modifier.height(Dimens.SectionGap))
            SectionTitle(stringResource(R.string.settings_automation))
            SwitchRow(
                title = stringResource(R.string.settings_allow_other_apps),
                subtitle = stringResource(R.string.settings_allow_other_apps_subtitle),
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
            SectionTitle(stringResource(R.string.settings_about))
            Text(
                stringResource(R.string.settings_about_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Dimens.Gutter),
            )
        }
    }
}

/**
 * Opens Developer options, where Android lets the user pick the Bluetooth
 * codec. Returns a hint when that screen isn't available (not unlocked yet).
 */
private fun openDeveloperOptions(context: Context): String? {
    val developerOn = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
    if (developerOn) {
        try {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            return null
        } catch (e: ActivityNotFoundException) {
            // Fall through to the hint.
        }
    }
    return context.getString(R.string.settings_developer_off)
}

@Composable
private fun automationHelp(): String = listOf(
    stringResource(R.string.automation_help_send, Automation.PACKAGE),
    "",
    "${Automation.ACTION_SET_EQ} (${Automation.EXTRA_PRESET}: reference / basshead / monitor)",
    Automation.ACTION_NEXT_EQ,
    Automation.ACTION_CONNECT,
    Automation.ACTION_DISCONNECT,
    "",
    stringResource(R.string.automation_help_state, Automation.EVENT_STATE),
).joinToString("\n")
