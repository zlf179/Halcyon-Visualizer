package com.ella.music.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.ui.components.EllaMiuixDialog
import com.ella.music.ui.components.EllaMiuixListItem

internal enum class PlaybackExportFormat {
    Ella,
    Sollin
}

@Composable
internal fun BackupFormatDialog(
    onDismissRequest: () -> Unit,
    onFormatSelected: (PlaybackExportFormat) -> Unit
) {
    EllaMiuixDialog(
        show = true,
        title = stringResource(R.string.settings_backup_export_format_title),
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BackupExportFormatRow(
                title = stringResource(R.string.settings_backup_export_format_ella),
                summary = stringResource(R.string.settings_backup_export_format_ella_summary),
                onClick = { onFormatSelected(PlaybackExportFormat.Ella) }
            )
            BackupExportFormatRow(
                title = stringResource(R.string.settings_backup_export_format_sollin),
                summary = stringResource(R.string.settings_backup_export_format_sollin_summary),
                onClick = { onFormatSelected(PlaybackExportFormat.Sollin) }
            )
        }
    }
}

@Composable
private fun BackupExportFormatRow(
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    EllaMiuixListItem(
        title = title,
        summary = summary,
        onClick = onClick
    )
}
