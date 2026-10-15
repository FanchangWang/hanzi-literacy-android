package com.pupil.hanzi.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pupil.hanzi.data.Settings

/**
 * Single screen with two mutually exclusive states: the character grid and the
 * text field. The switches stay put in both.
 */
@Composable
fun MainScreen(viewModel: HanziViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Holding the screen awake is a window flag, so it follows the setting
    // rather than being pushed on and popped by hand.
    LaunchedEffect(state.settings.keepScreenOn) {
        if (state.settings.keepScreenOn) {
            context.keepScreenAwake()
        } else {
            context.allowScreenToSleep()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SettingsBar(
                settings = state.settings,
                onShowPinyin = viewModel::setShowPinyin,
                onAnimate = viewModel::setAnimate,
                onSpeak = viewModel::setSpeak,
                onKeepScreenOn = viewModel::setKeepScreenOn,
            )
            HorizontalDivider()

            when (state.mode) {
                Mode.VIEWING -> ViewingPane(
                    state = state,
                    onSpeak = viewModel::speak,
                    onPreviewReading = viewModel::speakReading,
                    onSpeakAll = viewModel::speakAll,
                    onStopPlaying = viewModel::stopPlaying,
                    onEdit = viewModel::startEditing,
                    onOpenDetail = viewModel::openDetail,
                    onSelectReading = viewModel::selectReading,
                    onCloseDetail = viewModel::closeDetail,
                )

                Mode.EDITING -> EditingPane(
                    draft = state.draft,
                    onDraftChange = viewModel::onDraftChange,
                    onClear = viewModel::clearDraft,
                    onConfirm = viewModel::confirm,
                )
            }
        }
    }
}

/** The switches. Always visible. */
@Composable
private fun SettingsBar(
    settings: Settings,
    onShowPinyin: (Boolean) -> Unit,
    onAnimate: (Boolean) -> Unit,
    onSpeak: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "汉字识字",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 8.dp),
        )
        FilterChip(
            selected = settings.showPinyin,
            onClick = { onShowPinyin(!settings.showPinyin) },
            label = { Text("展示拼音") },
        )
        FilterChip(
            selected = settings.animateStrokes,
            onClick = { onAnimate(!settings.animateStrokes) },
            label = { Text("笔顺动画") },
        )
        FilterChip(
            selected = settings.speakCharacters,
            onClick = { onSpeak(!settings.speakCharacters) },
            label = { Text("语音") },
        )
        FilterChip(
            selected = settings.keepScreenOn,
            onClick = { onKeepScreenOn(!settings.keepScreenOn) },
            label = { Text("屏幕常亮") },
        )
    }
}

@Composable
private fun EditingPane(
    draft: String,
    onDraftChange: (String) -> Unit,
    onClear: () -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("文字输入", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            placeholder = { Text("请输入汉字或标点符号...") },
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onClear) { Text("清空") }
            Button(onClick = onConfirm) { Text("确认") }
        }
    }
}

@Composable
private fun ViewingPane(
    state: HanziUiState,
    onSpeak: (Int) -> Unit,
    onPreviewReading: (Int, Int) -> Unit,
    onSpeakAll: () -> Unit,
    onStopPlaying: () -> Unit,
    onCloseDetail: () -> Unit,
    onEdit: () -> Unit,
    onOpenDetail: (Int) -> Unit,
    onSelectReading: (Int, Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (state.isEmpty) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "还没有内容，点下面的「编辑」开始",
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val config = LocalConfiguration.current
            // The original tool is a fixed 120px cell, which on a 2560x1600
            // tablet yields 20 columns by 7 rows. A hard-coded 6 columns made
            // each cell enormous and showed barely two rows, so the count
            // follows the screen: aim for a cell around 96dp regardless of
            // how wide the display is.
            val targetCell = 96
            val columns = (config.screenWidthDp / targetCell).coerceIn(4, 14)
            CharGrid(
                cells = state.cells,
                showPinyin = state.settings.showPinyin,
                animate = state.settings.animateStrokes,
                speakEnabled = state.settings.speakCharacters,
                onSpeak = onSpeak,
                onOpenDetail = onOpenDetail,
                onSelectReading = onSelectReading,
                columns = columns,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("编辑")
            }
            if (state.settings.speakCharacters && !state.isEmpty) {
                // One button, two jobs: it starts the phrase and, while one is
                // running, stops it. Pressing it again mid-phrase used to queue
                // a second playback on top of the first.
                if (state.playing) {
                    Button(onClick = onStopPlaying) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("停止播放")
                    }
                } else {
                    Button(onClick = onSpeakAll) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("播放全部文本")
                    }
                }
            }
        }
    }

    state.detail?.let { detail ->
        DetailSheet(
            detail = detail,
            showPinyin = state.settings.showPinyin,
            speakEnabled = state.settings.speakCharacters,
            onSelectReading = { index ->
                val cellIndex = state.cells.indexOfFirst { it.char == detail.cell.char }
                if (cellIndex >= 0) onSelectReading(cellIndex, index)
            },
            onPreviewReading = { readingIndex ->
                val cellIndex = state.cells.indexOfFirst { it.char == detail.cell.char }
                if (cellIndex >= 0) onPreviewReading(cellIndex, readingIndex)
            },
            onDismiss = onCloseDetail,
        )
    }
}

private fun android.content.Context.keepScreenAwake() {
    val window = (this as? android.app.Activity)?.window ?: return
    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}

private fun android.content.Context.allowScreenToSleep() {
    val window = (this as? android.app.Activity)?.window ?: return
    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}