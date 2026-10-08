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
import androidx.compose.ui.unit.dp
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
        ScreenTopBar(entry?.title ?: "USB device", onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
        ) {
            if (entry == null) {
                Note("The device was unplugged.")
                return@Column
            }
            val info = entry.info
            InfoRow(
                "Model",
                info.model?.name ?: "–",
                note = info.model?.let { "${it.family.label} chip family, ${it.eqBands}-band EQ in MOONDROP Link" }
                    ?: "Not matched to a model in OpenDrop's list.",
            )
            InfoRow("USB id", info.id)
            InfoRow("Manufacturer", info.manufacturer ?: "–")
            InfoRow("Product", info.product ?: "–")
            InfoRow("Version", info.version ?: "–")
            InfoRow(
                "Interfaces",
                info.interfaces.size.toString(),
                note = info.interfaces.joinToString(", ") { UsbReport.className(it.interfaceClass) }.ifEmpty { null },
            )

            Spacer(Modifier.height(Dimens.SectionGap))
            SectionTitle("Support")
            Note(
                "OpenDrop can't change settings on USB devices yet. Each chip family speaks its own protocol, " +
                    "and OpenDrop only sends commands that were captured from a real device first. " +
                    "A USB report helps decode it.",
            )
            if (!entry.hasPermission) {
                NavRow(
                    title = "Allow access",
                    subtitle = "Lets OpenDrop read the device's descriptors for the report. Nothing on it changes.",
                    onClick = { onAllow(entry.key) },
                )
            }
            NavRow(
                title = "Share USB report",
                subtitle = if (entry.hasPermission) {
                    "Ids, names, interfaces and descriptors"
                } else {
                    "Ids, names and interfaces (allow access to include descriptors)"
                },
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
    context.startActivity(Intent.createChooser(send, "Share USB report"))
}
