package com.pupil.hanzi.ui

import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pupil.hanzi.data.Settings

/**
 * Width at which the switches stop being a phone's problem.
 *
 * Below this the title, four chips and the edit button cannot share one line,
 * so the chips collapse behind a toggle. This is Material's compact/medium
 * breakpoint, and it is used instead of measuring the chips because the
 * decision then needs no second layout pass.
 */
private const val WIDE_WIDTH_DP = 600

/**
 * Single screen with two mutually exclusive states: the character grid and the
 * text field. The switches stay put in both.
 */
@Composable
fun MainScreen(viewModel: HanziViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val wide = LocalConfiguration.current.screenWidthDp >= WIDE_WIDTH_DP

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
                mode = state.mode,
                wide = wide,
                onShowPinyin = viewModel::setShowPinyin,
                onAnimate = viewModel::setAnimate,
                onSpeak = viewModel::setSpeak,
                onKeepScreenOn = viewModel::setKeepScreenOn,
                onEdit = if (state.mode == Mode.VIEWING) viewModel::startEditing else null,
            )
            HorizontalDivider()

            when (state.mode) {
                Mode.VIEWING -> ViewingPane(
                    state = state,
                    onSpeak = viewModel::speak,
                    onPreviewReading = viewModel::speakReading,
                    onOpenDetail = viewModel::openDetail,
                    onSelectReading = viewModel::selectReading,
                    onCloseDetail = viewModel::closeDetail,
                )

                Mode.EDITING -> EditingPane(
                    draft = state.draft,
                    history = state.history,
                    wide = wide,
                    onDraftChange = viewModel::onDraftChange,
                    onClear = viewModel::clearDraft,
                    onPaste = viewModel::pasteFromClipboard,
                    onConfirm = viewModel::confirm,
                    onStudyHistory = viewModel::studyHistory,
                    onEditHistory = viewModel::editHistory,
                    onDeleteHistory = viewModel::deleteHistory,
                )
            }
        }
    }
}

/**
 * The switches.
 *
 * A phone cannot hold the title, four chips and the edit button on one line, so
 * there the chips collapse behind one toggle and unfold onto a second line when
 * it is tapped. A tablet, where they do fit, keeps the single row: chips next to
 * the title, edit button in the top-right corner.
 *
 * They start unfolded on the input page -- that is where they are set -- and
 * folded on the grid page, which wants the height. Tapping the toggle overrides
 * that for as long as the page stays put.
 *
 * The edit button stays outside the collapsing part, so it never moves.
 */
@Composable
private fun SettingsBar(
    settings: Settings,
    mode: Mode,
    wide: Boolean,
    onShowPinyin: (Boolean) -> Unit,
    onAnimate: (Boolean) -> Unit,
    onSpeak: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onEdit: (() -> Unit)?,
) {
    // Keyed on the mode: switching pages resets the toggle to that page's
    // default, and a rotation within a page keeps whatever was chosen.
    var expanded by rememberSaveable(mode) { mutableStateOf(mode == Mode.EDITING) }

    val chips: @Composable () -> Unit = {
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
            label = { Text("语音朗读") },
        )
        FilterChip(
            selected = settings.keepScreenOn,
            onClick = { onKeepScreenOn(!settings.keepScreenOn) },
            label = { Text("屏幕常亮") },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "汉字识字",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 8.dp),
            )
            if (wide) {
                FlowRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    chips()
                }
            } else {
                FilterChip(
                    selected = expanded,
                    onClick = { expanded = !expanded },
                    label = { Text(if (expanded) "收起设置" else "显示设置") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (expanded) {
                                Icons.Filled.ExpandLess
                            } else {
                                Icons.Filled.ExpandMore
                            },
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                )
                Spacer(Modifier.weight(1f))
            }
            if (onEdit != null) {
                TextButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("编辑")
                }
            }
        }
        if (!wide && expanded) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                chips()
            }
        }
    }
}

/**
 * The input page: the field, its buttons and the stored texts.
 *
 * A phone stacks them, with only the history scrolling. A wider window -- a
 * tablet, or a phone on its side -- puts the history beside the field instead,
 * which is the same split without the vertical squeeze.
 */
@Composable
private fun EditingPane(
    draft: String,
    history: List<String>,
    wide: Boolean,
    onDraftChange: (String) -> Unit,
    onClear: () -> Unit,
    onPaste: () -> Unit,
    onConfirm: () -> Unit,
    onStudyHistory: (String) -> Unit,
    onEditHistory: (String) -> Unit,
    onDeleteHistory: (String) -> Unit,
) {
    if (wide) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(16.dp),
            ) {
                Text("文字输入", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                // Weighted but not filled: the field takes the height its text
                // needs, up to what the buttons leave on the screen. A long
                // text scrolls inside the field instead of pushing them off.
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp)
                        .weight(1f, fill = false),
                    placeholder = { Text("请输入汉字或标点符号...") },
                )
                Spacer(Modifier.height(12.dp))
                DraftActions(onClear = onClear, onPaste = onPaste, onConfirm = onConfirm)
            }

            VerticalDivider()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(16.dp),
            ) {
                Text("历史记录", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                HistoryList(
                    history = history,
                    onStudy = onStudyHistory,
                    onEdit = onEditHistory,
                    onDelete = onDeleteHistory,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        return
    }

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
        DraftActions(onClear = onClear, onPaste = onPaste, onConfirm = onConfirm)

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Text(
            text = "历史记录",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        // The only scrolling part of this page: the field and its buttons above
        // keep their place while the history moves under them.
        HistoryList(
            history = history,
            onStudy = onStudyHistory,
            onEdit = onEditHistory,
            onDelete = onDeleteHistory,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 清空 on the left, the actions that keep the text on the right: the right edge
 * is where the thumb rests and where the confirming action belongs.
 */
@Composable
private fun DraftActions(
    onClear: () -> Unit,
    onPaste: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onClear) { Text("清空") }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onPaste) { Text("粘贴") }
            Button(onClick = onConfirm) { Text("确认") }
        }
    }
}

@Composable
private fun ViewingPane(
    state: HanziUiState,
    onSpeak: (Int) -> Unit,
    onPreviewReading: (Int, Int) -> Unit,
    onCloseDetail: () -> Unit,
    onOpenDetail: (Int) -> Unit,
    onSelectReading: (Int, Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (state.isEmpty) {
            if (state.loading) {
                LoadingIndicator()
            } else {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "还没有内容，点右上角的「编辑」开始",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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

/** Centred spinner with a caption, shown while a sentence is being parsed. */
@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(strokeWidth = 3.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                text = "正在加载中…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
