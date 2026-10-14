package com.pupil.hanzi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.graphics.Color

/**
 * The polyphone reading list, shown over the character.
 *
 * Deliberately a plain popup rather than a menu: the options need the pinyin
 * font and two-tone colouring, which a Material menu item cannot do.
 */
@Composable
fun ReadingPicker(
    cell: CharCell,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnClickOutside = true,
        ),
    ) {
        Box(modifier = Modifier.clip(RoundedCornerShape(8.dp))) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 4.dp),
            ) {
                cell.readings.forEachIndexed { index, reading ->
                    Box(
                        modifier = Modifier
                            .clickable { onSelect(index) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        PinyinText(
                            reading = reading,
                            fontSize = 18.sp,
                            fontWeight = if (index == cell.selectedReading) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                        )
                    }
                }
            }
        }
    }
}