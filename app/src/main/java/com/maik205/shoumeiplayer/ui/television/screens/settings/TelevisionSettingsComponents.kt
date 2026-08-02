package com.maik205.shoumeiplayer.ui.television.screens.settings

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusSurface
import com.maik205.shoumeiplayer.ui.television.components.televisionBringIntoViewOnFocus
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionTheme

@Composable
internal fun SettingsSectionContent(
    section: SettingsSection,
    rows: List<SettingRowModel>,
    selectedSectionFocus: FocusRequester,
    onOpenChoice: (SettingRowModel, FocusRequester) -> Unit,
    onMoveSection: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focusedKey by remember(section) { mutableStateOf<String?>(null) }
    val rowFocus = remember(section) {
        rows
            .filter { row -> row.onClick != null || row.choices.isNotEmpty() }
            .associate { row -> row.key to FocusRequester() }
    }

    LazyColumn(
        modifier = modifier
            .focusGroup()
            .focusRestorer()
            .focusProperties { up = selectedSectionFocus },
        contentPadding = PaddingValues(bottom = 48.dp),
    ) {
        items(rows, key = SettingRowModel::key) { row ->
            val restingAlpha = if (focusedKey == null || focusedKey == row.key) 0.74f else 0.38f
            val focusRequester = rowFocus[row.key]
            if (focusRequester == null) {
                SettingsValueRow(
                    row = row,
                    rowAlpha = restingAlpha,
                )
            } else {
                SettingsInteractiveRow(
                    row = row,
                    restingAlpha = restingAlpha,
                    focusRequester = focusRequester,
                    onClick = if (row.choices.isNotEmpty()) {
                        { onOpenChoice(row, focusRequester) }
                    } else {
                        row.onClick ?: {}
                    },
                    onMoveSection = onMoveSection,
                    onFocusChanged = { focused ->
                        if (focused) {
                            focusedKey = row.key
                        } else if (focusedKey == row.key) {
                            focusedKey = null
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SettingsValueRow(
    row: SettingRowModel,
    rowAlpha: Float,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(37.dp)
            .padding(horizontal = 6.dp)
            .graphicsLayer { alpha = rowAlpha },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(row.labelRes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = row.value,
            style = MaterialTheme.typography.bodyMedium,
            color = TelevisionTheme.colors.PaperMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsInteractiveRow(
    row: SettingRowModel,
    restingAlpha: Float,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onMoveSection: (Int) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
) {
    TelevisionFocusSurface(
        onClick = onClick,
        restingAlpha = restingAlpha,
        focusedAlpha = 1f,
        scaleTo = 1f,
        focusRequester = focusRequester,
        onFocusChanged = onFocusChanged,
        modifier = Modifier
            .fillMaxWidth()
            .height(37.dp)
            .televisionBringIntoViewOnFocus()
            .onPreviewKeyEvent { event ->
                val nativeEvent = event.nativeKeyEvent
                if (nativeEvent.action != KeyEvent.ACTION_DOWN) {
                    false
                } else {
                    when (nativeEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            onMoveSection(-1)
                            true
                        }

                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            onMoveSection(1)
                            true
                        }

                        else -> false
                    }
                }
            },
    ) { focused ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(row.labelRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            when (row.control) {
                SettingControl.Toggle -> SettingsToggle(
                    checked = row.checked,
                    focused = focused,
                )

                SettingControl.Choice -> {
                    Text(
                        text = row.value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (focused) TelevisionTheme.colors.Paper else TelevisionTheme.colors.PaperMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                    )
                }

                SettingControl.Value -> Unit
            }
        }
    }
}

@Composable
internal fun SettingsChoiceDrawer(
    row: SettingRowModel,
    onSelect: (SettingChoiceOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val backFocus = remember(row.key) { FocusRequester() }
    val selectedFocus = remember(row.key) { FocusRequester() }
    val label = stringResource(row.labelRes)
    val initialIndex = row.choices.indexOfFirst(SettingChoiceOption::selected)
        .takeIf { it >= 0 }
        ?: 0

    LaunchedEffect(row.key) {
        runCatching { selectedFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TelevisionTheme.colors.Black.copy(alpha = 0.54f)),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Column(
            modifier = Modifier
                .width(390.dp)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        0f to androidx.compose.ui.graphics.Color.Transparent,
                        0.18f to TelevisionTheme.colors.Black.copy(alpha = 0.18f),
                        0.5f to TelevisionTheme.colors.Black.copy(alpha = 0.68f),
                        0.78f to TelevisionTheme.colors.Black.copy(alpha = 0.94f),
                        1f to TelevisionTheme.colors.Black.copy(alpha = 0.99f),
                    ),
                )
                .padding(
                    start = 40.dp,
                    end = TelevisionDimensions.SafeHorizontal,
                    top = TelevisionDimensions.SafeTop,
                    bottom = TelevisionDimensions.SafeBottom,
                )
                .focusGroup()
                .focusRestorer()
                .onPreviewKeyEvent { event ->
                    val nativeEvent = event.nativeKeyEvent
                    if (
                        nativeEvent.action == KeyEvent.ACTION_DOWN &&
                        nativeEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                    ) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                },
        ) {
            TelevisionFocusSurface(
                onClick = onDismiss,
                focusRequester = backFocus,
                scaleTo = 1.12f,
                restingAlpha = 0.46f,
                modifier = Modifier
                    .size(48.dp)
                    .focusProperties {
                        up = FocusRequester.Cancel
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                        down = selectedFocus
                    },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.tv_close_label, label),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(22.dp),
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(20.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                items(
                    items = row.choices,
                    key = SettingChoiceOption::key,
                ) { option ->
                    val optionIndex = row.choices.indexOf(option)
                    TelevisionFocusSurface(
                        onClick = { onSelect(option) },
                        focusRequester = if (optionIndex == initialIndex) selectedFocus else null,
                        scaleTo = 1f,
                        restingAlpha = if (option.selected) 0.78f else 0.46f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .focusProperties {
                                left = FocusRequester.Cancel
                                right = FocusRequester.Cancel
                                if (optionIndex == 0) {
                                    up = backFocus
                                }
                                if (optionIndex == row.choices.lastIndex) {
                                    down = FocusRequester.Cancel
                                }
                            },
                    ) { focused ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    translationX = if (focused) 8.dp.toPx() else 0f
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (option.selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(R.string.tv_selected),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsToggle(
    checked: Boolean,
    focused: Boolean,
) {
    Box(
        modifier = Modifier
            .width(26.dp)
            .height(15.dp)
            .clip(CircleShape)
            .background(
                if (checked) {
                    TelevisionTheme.colors.Paper
                } else {
                    TelevisionTheme.colors.Paper.copy(alpha = if (focused) 0.28f else 0.18f)
                },
            )
            .padding(2.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .size(11.dp)
                .clip(CircleShape)
                .background(if (checked) TelevisionTheme.colors.Black else TelevisionTheme.colors.Paper),
        )
    }
}
