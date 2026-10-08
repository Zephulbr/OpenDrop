package org.opendrop.app.ui

import android.content.Context
import android.content.Intent
import android.os.Build
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.opendrop.app.R
import org.opendrop.app.device.UsbEntry
import org.opendrop.app.ui.components.InfoRow
import org.opendrop.app.ui.components.NavRow
import org.opendrop.app.ui.components.ScreenTopBar
import org.opendrop.app.ui.components.SectionTitle
import org.opendrop.app.ui.theme.Dimens
import org.opendrop.protocol.UsbReport

/** One USB device, read-only (roadmap M5): what it reports, and a report to share. */
@Composable
fun UsbDeviceScreen(entry: UsbEntry?, onAllow: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(entry?.title ?: stringResource(R.string.usb_device), onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
        ) {
            if (entry == null) {
                Note(stringResource(R.string.usb_unplugged))
                return@Column
            }
            val info = entry.info
            InfoRow(
                stringResource(R.string.info_model),
                info.model?.name ?: "–",
                note = info.model?.let { stringResource(R.string.usb_model_note, it.family.label, it.eqBands) }
                    ?: stringResource(R.string.usb_model_unknown),
            )
            InfoRow(stringResource(R.string.usb_id), info.id)
            InfoRow(stringResource(R.string.usb_manufacturer), info.manufacturer ?: "–")
            InfoRow(stringResource(R.string.usb_product), info.product ?: "–")
            InfoRow(stringResource(R.string.usb_version), info.version ?: "–")
            InfoRow(
                stringResource(R.string.usb_interfaces),
                info.interfaces.size.toString(),
                note = info.interfaces.joinToString(", ") { UsbReport.className(it.interfaceClass) }.ifEmpty { null },
            )

            Spacer(Modifier.height(Dimens.SectionGap))
            SectionTitle(stringResource(R.string.usb_support))
            Note(
                stringResource(R.string.usb_support_note),
            )
            if (!entry.hasPermission) {
                NavRow(
                    title = stringResource(R.string.usb_allow),
                    subtitle = stringResource(R.string.usb_allow_subtitle),
                    onClick = { onAllow(entry.key) },
                )
            }
            NavRow(
                title = stringResource(R.string.usb_share_report),
                subtitle = stringResource(if (entry.hasPermission) R.string.usb_share_full else R.string.usb_share_basic),
                onClick = { shareUsbReport(context, entry) },
            )
        }
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Dimens.Gutter, vertical = 8.dp),
    )
}

private fun shareUsbReport(context: Context, entry: UsbEntry) {
    val appVersion = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "?"
    val text = UsbReport.build(
        entry.info,
        listOf(
            "App" to appVersion,
            "Phone" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        ),
    )
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "OpenDrop USB report: ${entry.title}")
        .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, context.getString(R.string.usb_share_report)))
}
