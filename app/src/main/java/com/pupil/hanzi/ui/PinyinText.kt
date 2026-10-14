package com.pupil.hanzi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.pupil.hanzi.R
import com.pupil.hanzi.data.PinyinReading

/**
 * The pinyin typeface. Without it the system font draws a and g the way a
 * Latin text font does, which looks nothing like a textbook.
 */
val PinyinFontFamily = FontFamily(Font(R.font.pinyin_regular))

/** Initial red, final blue, matching the original tool's convention. */
val InitialColor = Color(0xFFE74C3C)
val FinalColor = Color(0xFF3498DB)

/**
 * One syllable in the pinyin font, initial in red and final in blue.
 *
 * The split comes from [PinyinReading], which derived it from the flat
 * dictionary key rather than the display string, so a tone mark can never be
 * mistaken for part of the initial.
 */
@Composable
fun PinyinText(
    reading: PinyinReading,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    PinyinTextColored(
        display = reading.display,
        initialLength = reading.initial.length,
        fontSize = fontSize,
        fontWeight = fontWeight,
        modifier = modifier,
    )
}

/**
 * Two-tone pinyin. The colour boundary is placed by [initialLength] rather
 * than by scanning for tone marks, which keeps it correct for every syllable.
 */
@Composable
fun PinyinTextColored(
    display: String,
    initialLength: Int,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    val annotated = remember(display, initialLength) {
        buildAnnotatedString {
            append(display)
            if (initialLength in 1..display.length) {
                addStyle(SpanStyle(color = InitialColor), 0, initialLength)
                addStyle(SpanStyle(color = FinalColor), initialLength, display.length)
            }
        }
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = TextStyle(
            fontFamily = PinyinFontFamily,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = TextAlign.Center,
        ),
    )
}

/**
 * A polyphone's pinyin, tinted so the extra readings are discoverable.
 * Tapping opens the picker.
 */
@Composable
fun PolyphonePinyin(
    reading: PinyinReading,
    fontSize: TextUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PinyinTextColored(
        display = reading.display,
        initialLength = reading.initial.length,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}