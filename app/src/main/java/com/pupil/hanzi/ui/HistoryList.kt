package com.pupil.hanzi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The texts that were confirmed before, newest first.
 *
 * This is the only part of the input page that scrolls, so it takes whatever
 * height is left over and the field above it stays put.
 */
@Composable
fun HistoryList(
    history: List<String>,
    onStudy: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (history.isEmpty()) {
        Text(
            text = "还没有历史记录。确认过的文字会自动存在这里。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(top = 4.dp),
        )
        return
    }

    LazyColumn(modifier = modifier) {
        // The text is the entry's identity: recording the same text twice
        // moves it instead of adding a second copy, so keys stay unique.
        items(history, key = { it }) { text ->
            HistoryEntry(
                text = text,
                onStudy = { onStudy(text) },
                onEdit = { onEdit(text) },
                onDelete = { onDelete(text) },
            )
        }
    }
}

/**
 * One stored text with what can be done to it.
 *
 * Two lines of text at most: a stored paragraph is a reminder of what it was,
 * not something to read here -- 「学习」 opens it in the grid, 「编辑」 puts it
 * back in the field.
 */
@Composable
private fun HistoryEntry(
    text: String,
    onStudy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = onStudy) { Text("学习") }
            TextButton(onClick = onEdit) { Text("编辑") }
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("删除")
            }
        }
        HorizontalDivider()
    }
}
