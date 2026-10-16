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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pupil.hanzi.data.HistoryEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The texts that were confirmed before, newest first.
 *
 * This is the only part of the input page that scrolls, so it takes whatever
 * height is left over and the field above it stays put.
 */
@Composable
fun HistoryList(
    history: List<HistoryEntry>,
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
        // Read once per composition: a row only needs to know which day it was
        // stored on, not the exact instant it is drawn.
        val now = System.currentTimeMillis()
        // The text is the entry's identity: recording the same text twice
        // moves it instead of adding a second copy, so keys stay unique.
        items(history, key = { it.text }) { entry ->
            HistoryRow(
                entry = entry,
                now = now,
                onStudy = { onStudy(entry.text) },
                onEdit = { onEdit(entry.text) },
                onDelete = { onDelete(entry.text) },
            )
        }
    }
}

/**
 * One stored text with when it was confirmed and what can be done to it.
 *
 * Two lines of text at most: a stored paragraph is a reminder of what it was,
 * not something to read here -- 「学习」 opens it in the grid, 「编辑」 puts it
 * back in the field. Deleting sits on its own at the left, away from the two
 * actions that open the text.
 */
@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    now: Long,
    onStudy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        // An entry stored before times were kept has none to show.
        if (entry.timeMillis > 0L) {
            Text(
                text = formatHistoryTime(entry.timeMillis, now),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = entry.text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text("删除")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit) { Text("编辑") }
                TextButton(onClick = onStudy) { Text("学习") }
            }
        }
        HorizontalDivider()
    }
}

private val HISTORY_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
private val HISTORY_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")

/**
 * When a stored entry was confirmed, in the device's zone and idiom.
 *
 * A weekday is the part that ages well: it says at a glance how long ago this
 * was. Today and yesterday are named because their date is the one thing the
 * reader already knows; anything older gets its full date, since "上周三" only
 * gets more ambiguous the further back it goes. Reads as 周五 今天 10:04:40.
 */
internal fun formatHistoryTime(
    millis: Long,
    now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val at = Instant.ofEpochMilli(millis).atZone(zone)
    val date = at.toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val day = when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> date.format(HISTORY_DATE_FORMAT)
    }
    // DayOfWeek.value is 1 for Monday through 7 for Sunday.
    return "周${WEEKDAYS[date.dayOfWeek.value - 1]} $day ${at.format(HISTORY_TIME_FORMAT)}"
}
