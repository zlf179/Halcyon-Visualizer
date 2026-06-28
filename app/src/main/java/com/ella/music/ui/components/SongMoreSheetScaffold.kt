package com.ella.music.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
internal fun SongSheetColumn(content: @Composable ColumnScope.() -> Unit) {
    EllaMiuixSheetColumn(
        verticalPadding = 8.dp,
        spacing = 2.dp,
        showHandle = false,
        content = content
    )
}

@Composable
internal fun SongMenuItem(
    title: String,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    EllaMiuixMenuItem(text = title, onClick = onClick, danger = danger)
}
