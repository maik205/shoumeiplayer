package com.maik205.shoumeiplayer.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.data.api.dto.UserConfigurationDto
import com.maik205.shoumeiplayer.data.repo.TrackSelection
import com.maik205.shoumeiplayer.ui.components.NavRailDestination
import com.maik205.shoumeiplayer.ui.components.NavRailScaffold
import com.maik205.shoumeiplayer.ui.components.ScreenScaffold
import com.maik205.shoumeiplayer.ui.components.SlabButton
import com.maik205.shoumeiplayer.ui.navigation.containerViewModel
import com.maik205.shoumeiplayer.ui.theme.Alpha
import com.maik205.shoumeiplayer.ui.theme.Ash600
import com.maik205.shoumeiplayer.ui.theme.Dimens
import com.maik205.shoumeiplayer.ui.theme.Ink050
import com.maik205.shoumeiplayer.ui.theme.Ink150
import com.maik205.shoumeiplayer.ui.theme.Paper
import com.maik205.shoumeiplayer.ui.theme.Signal

/** §5.7 — a ledger row is 72dp tall, spanning the full safe width (x=48 → 912). */
private val LedgerRowHeight = 72.dp

/** §5.7 — the focused actionable row's leading edge-light bar: `3.dp × rowHeight` (§4.2). */
private val EdgeLightWidth = 3.dp

/** §4.3 full-width-row treatment applied to the group list (M-B13): fixed 240dp column. */
private val GroupPaneWidth = 240.dp

/** Gap between the group list and the field pane. */
private val GroupFieldGap = 32.dp

/** Value column width cap so a long URL wraps into the ledger instead of eating the label. */
private val ValueMaxWidth = 560.dp

/** §4.2 — dialog copy caps at 560dp (§3); the panel itself is a plane-2 slab. */
private val DialogWidth = 560.dp

/** §4.3 — the one hairline that separates a group from a group, and the one above Sign out. */
private val GroupRuleGap = 20.dp

/**
 * §5.7 — placeholder for a value the server has not answered for yet: never a spinner, and never a
 * long dash. A dash in a value column reads as "this row is broken"; the plain word is the truth.
 */
@Composable
private fun pendingLabel(): String = stringResource(R.string.settings_not_available)

/** docs/browse-v2-plan.md M-B13 §3 pin — the human label for each [SettingsGroup]. */
@Composable
private fun SettingsGroup.label(): String = when (this) {
    SettingsGroup.Playback -> stringResource(R.string.settings_playback)
    SettingsGroup.Appearance -> stringResource(R.string.settings_appearance)
    SettingsGroup.Server -> stringResource(R.string.settings_server)
    SettingsGroup.About -> stringResource(R.string.settings_about)
}

/** `SubtitlePlaybackMode` cycle order, reusing `TrackSelection`'s server-verified spellings. */
private val SubtitleModeOrder = listOf(
    TrackSelection.SubtitleMode.DEFAULT,
    TrackSelection.SubtitleMode.ALWAYS,
    TrackSelection.SubtitleMode.ONLY_FORCED,
    TrackSelection.SubtitleMode.NONE,
    TrackSelection.SubtitleMode.SMART,
)

@Composable
private fun subtitleModeLabel(value: String?): String = when (value) {
    TrackSelection.SubtitleMode.ALWAYS -> stringResource(R.string.settings_subtitle_mode_always)
    TrackSelection.SubtitleMode.ONLY_FORCED -> stringResource(R.string.settings_subtitle_mode_only_forced)
    TrackSelection.SubtitleMode.NONE -> stringResource(R.string.off)
    TrackSelection.SubtitleMode.SMART -> stringResource(R.string.settings_subtitle_mode_smart)
    else -> stringResource(R.string.settings_subtitle_mode_default)
}

private fun nextSubtitleMode(current: String?): String {
    val index = SubtitleModeOrder.indexOf(current).let { if (it < 0) 0 else it }
    return SubtitleModeOrder[(index + 1) % SubtitleModeOrder.size]
}

/**
 * A small curated language cycle rather than free text: TV has no comfortable text entry (the
 * on-screen keyboard is a D-pad grid, brief guardrail 4), and `SubtitleLanguagePreference` /
 * `AudioLanguagePreference` are ISO 639-2 codes a viewer cannot be expected to spell correctly one
 * character at a time. Not pinned by the plan; noted as a deviation in the task's completion note.
 */
private val LanguageOptions: List<Pair<String?, Int>> = listOf(
    null to R.string.settings_language_system_default,
    "eng" to R.string.settings_language_english,
    "jpn" to R.string.settings_language_japanese,
    "spa" to R.string.settings_language_spanish,
    "fre" to R.string.settings_language_french,
    "ger" to R.string.settings_language_german,
)

@Composable
private fun languageLabel(code: String?): String {
    val labelRes = LanguageOptions.firstOrNull { it.first == code }?.second
    // A code outside the curated cycle (e.g. one the server set before this list existed) has no
    // resource to localize; it is shown as the raw code rather than silently falling to "System
    // default", which would misrepresent what is actually configured.
    return if (labelRes != null) stringResource(labelRes) else code ?: stringResource(R.string.settings_language_system_default)
}

private fun nextLanguage(current: String?): String? {
    val index = LanguageOptions.indexOfFirst { it.first == current }.let { if (it < 0) 0 else it }
    return LanguageOptions[(index + 1) % LanguageOptions.size].first
}

/** `ClientSettings.preferredQuality` label cycle — matches that field's doc comment verbatim. */
private val QualityOptions = listOf("Auto", "4K", "1080p", "720p", "480p")

private fun nextQuality(current: String): String {
    val index = QualityOptions.indexOf(current).let { if (it < 0) 0 else it }
    return QualityOptions[(index + 1) % QualityOptions.size]
}

@Composable
private fun onOff(value: Boolean): String = stringResource(if (value) R.string.on else R.string.off)

/**
 * §5.7 v2 — Settings, "the ledger", now a two-pane group view (browse-v2-plan M-B13).
 *
 * Left pane: the four [SettingsGroup]s as a 240dp full-width-row list (§4.3 focus treatment, no
 * scale) — the selected group's label stays [Paper], the rest [Ash600]. Right pane: that group's
 * rows in the original §5.7 ledger rhythm (72dp, label `titleMedium` flush left, value `bodyMedium
 * @ Ash600` right-aligned). Pure-info rows (server URL, version, engine name) stay non-focusable;
 * every other row is a full-width actionable row that cycles or toggles its value on click.
 *
 * Server-backed rows (Playback group's subtitle/audio prefs, `PlayDefaultAudioTrack`,
 * `EnableNextEpisodeAutoPlay`, `RememberAudioSelections`, `RememberSubtitleSelections`) never show
 * a value the write hasn't confirmed: [SettingsViewModel] holds the previous value until the POST
 * resolves and, on failure, surfaces the one inline error line at the foot of the field pane
 * instead of a toast (§6).
 *
 * Sign out stays the Server group's last row, under the one permitted hairline, with its
 * confirmation dialog unchanged from v1.
 *
 * Rail: wrapped in `NavRailScaffold`. M-B14 wired [onNavigate] through, so the cross-screen rail
 * jump (Home / Search / Libraries) is one `NavGraph`-owned hop; the default no-op keeps previews
 * and tests constructing this screen without a navigator.
 */
@Composable
fun SettingsScreen(
    onSignedOut: () -> Unit,
    onBack: () -> Unit,
    onNavigate: (NavRailDestination) -> Unit = {},
) {
    val viewModel = containerViewModel { container ->
        SettingsViewModel(
            sessionStore = container.sessionStore,
            settingsStore = container.settingsStore,
            authRepository = container.authRepository,
            appVersion = container.appVersion,
            // §5.7 — the debug truth is part of the "proof" concept, not an easter egg.
            engineName = container.playerEngine::class.simpleName.orEmpty(),
        )
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val config = uiState.configuration ?: UserConfigurationDto()

    var confirming by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    LaunchedEffect(viewModel) {
        viewModel.signedOut.collect { onSignedOut() }
    }

    val signOutFocus = remember { FocusRequester() }

    NavRailScaffold(
        selected = NavRailDestination.Settings,
        onSelect = onNavigate,
    ) { contentFocus, railFocus ->
        LaunchedEffect(Unit) { runCatching { contentFocus.requestFocus() } }
        ScreenScaffold {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .width(GroupPaneWidth)
                        .fillMaxHeight(),
                ) {
                    SettingsGroup.entries.forEachIndexed { index, group ->
                        GroupRow(
                            label = group.label(),
                            selected = group == uiState.group,
                            onClick = { viewModel.selectGroup(group) },
                            focusRequester = if (index == 0) contentFocus else null,
                            leftFocusRequester = railFocus,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(GroupFieldGap))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    when (uiState.group) {
                        SettingsGroup.Playback -> PlaybackFields(
                            config = config,
                            preferredQuality = uiState.settings.preferredQuality,
                            saving = uiState.saving,
                            viewModel = viewModel,
                        )
                        SettingsGroup.Appearance -> AppearanceFields(
                            focusScaleEnabled = uiState.settings.focusScaleEnabled,
                            clockInOsd = uiState.settings.clockInOsd,
                            viewModel = viewModel,
                        )
                        SettingsGroup.Server -> ServerFields(
                            uiState = uiState,
                            signOutFocus = signOutFocus,
                            onSignOutClick = { confirming = true },
                        )
                        SettingsGroup.About -> AboutFields(uiState = uiState)
                    }
                    if (uiState.errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        // §6 — error is inline and plain: one sentence, no dialog, no toast.
                        Text(
                            text = uiState.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Signal,
                        )
                    }
                }
            }
        }
    }

    if (confirming) {
        // §5.7 — "Confirmation dialog required"; §1 allows centring *only* inside a dialog.
        SignOutDialog(
            userName = uiState.userName,
            onConfirm = {
                confirming = false
                viewModel.signOut()
            },
            onDismiss = { confirming = false },
        )
    }
}

@Composable
private fun PlaybackFields(
    config: UserConfigurationDto,
    preferredQuality: String,
    saving: Boolean,
    viewModel: SettingsViewModel,
) {
    ActionableRow(
        label = stringResource(R.string.settings_preferred_quality),
        value = preferredQuality,
        onClick = { viewModel.setPreferredQuality(nextQuality(preferredQuality)) },
    )
    ActionableRow(
        label = stringResource(R.string.settings_subtitle_mode),
        value = subtitleModeLabel(config.subtitleMode),
        enabled = !saving,
        onClick = { viewModel.setSubtitleMode(nextSubtitleMode(config.subtitleMode)) },
    )
    ActionableRow(
        label = stringResource(R.string.settings_subtitle_language),
        value = languageLabel(config.subtitleLanguagePreference),
        enabled = !saving,
        onClick = { viewModel.setSubtitleLanguage(nextLanguage(config.subtitleLanguagePreference)) },
    )
    ActionableRow(
        label = stringResource(R.string.settings_audio_language),
        value = languageLabel(config.audioLanguagePreference),
        enabled = !saving,
        onClick = { viewModel.setAudioLanguage(nextLanguage(config.audioLanguagePreference)) },
    )
    ActionableRow(
        label = stringResource(R.string.settings_play_default_audio),
        value = onOff(config.playDefaultAudioTrack),
        enabled = !saving,
        onClick = { viewModel.setPlayDefaultAudioTrack(!config.playDefaultAudioTrack) },
    )
    ActionableRow(
        label = stringResource(R.string.settings_remember_audio_selections),
        value = onOff(config.rememberAudioSelections),
        enabled = !saving,
        onClick = { viewModel.setRememberAudioSelections(!config.rememberAudioSelections) },
    )
    ActionableRow(
        label = stringResource(R.string.settings_remember_subtitle_selections),
        value = onOff(config.rememberSubtitleSelections),
        enabled = !saving,
        onClick = { viewModel.setRememberSubtitleSelections(!config.rememberSubtitleSelections) },
    )
    ActionableRow(
        label = stringResource(R.string.settings_autoplay_next),
        value = onOff(config.enableNextEpisodeAutoPlay),
        enabled = !saving,
        onClick = { viewModel.setNextEpisodeAutoPlay(!config.enableNextEpisodeAutoPlay) },
    )
}

@Composable
private fun AppearanceFields(
    focusScaleEnabled: Boolean,
    clockInOsd: Boolean,
    viewModel: SettingsViewModel,
) {
    ActionableRow(
        label = stringResource(R.string.settings_focus_scale),
        value = onOff(focusScaleEnabled),
        onClick = { viewModel.setFocusScaleEnabled(!focusScaleEnabled) },
    )
    ActionableRow(
        label = stringResource(R.string.settings_clock_in_osd),
        value = onOff(clockInOsd),
        onClick = { viewModel.setClockInOsd(!clockInOsd) },
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ServerFields(
    uiState: SettingsUiState,
    signOutFocus: FocusRequester,
    onSignOutClick: () -> Unit,
) {
    val pending = pendingLabel()
    LedgerRow(label = stringResource(R.string.settings_address_label), value = uiState.serverUrl.ifEmpty { pending })
    LedgerRow(
        label = stringResource(R.string.settings_server),
        value = uiState.serverName ?: pending,
        secondary = uiState.serverVersion?.let { stringResource(R.string.settings_version_format, it) },
    )
    LedgerRow(label = stringResource(R.string.settings_signed_in_as), value = uiState.userName.ifEmpty { pending })

    // §5.7 — the second and last permitted rule: the destructive row sits under it.
    Spacer(modifier = Modifier.height(GroupRuleGap))
    Hairline()
    Spacer(modifier = Modifier.height(GroupRuleGap))
    ActionableRow(
        label = stringResource(R.string.sign_out),
        value = "",
        enabled = !uiState.signingOut,
        labelColor = Signal,
        border = Border(
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.errorContainer),
            shape = MaterialTheme.shapes.small,
        ),
        focusRequester = signOutFocus,
        onClick = onSignOutClick,
    )
}

@Composable
private fun AboutFields(uiState: SettingsUiState) {
    val pending = pendingLabel()
    LedgerRow(label = stringResource(R.string.settings_application_label), value = uiState.appVersion.ifEmpty { pending })
    LedgerRow(label = stringResource(R.string.settings_playback_engine_label), value = uiState.engineName.ifEmpty { pending })
}

/**
 * §5.7 — a pure-info row: no `focusable()`, no click target. The value is right-aligned to the safe
 * edge, and its optional second line carries the quiet detail (`Version 10.9.11`) at `@0.38`.
 *
 * **Rows are grouped, not ruled** (§4.3): there is no divider under a row. A hairline under every
 * row is a line whose only job is to look designed, and it made a five-row screen read as a form.
 */
@Composable
private fun LedgerRow(
    label: String,
    value: String,
    secondary: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(LedgerRowHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = Paper,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(modifier = Modifier.width(Dimens.ItemSpacing))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = ValueMaxWidth),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (secondary != null) {
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodySmall,
                        color = Paper.copy(alpha = Alpha.TextDisabled),
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * §5.7 / §4.3 — a focusable full-width ledger row: focus takes background [Ink050] plus a
 * `3.dp × 72dp` white edge-light bar at the leading edge, **no scale**. [labelColor] defaults to
 * [Paper]; the one exception is Sign out, which keeps its destructive [Signal] label and resting
 * `errorContainer` [border] (§5.7's "the destructive colour stays in the text").
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ActionableRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    labelColor: Color = Paper,
    border: Border? = null,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.small
    val rowBorder = border ?: Border.None

    var rowModifier = modifier
        .fillMaxWidth()
        .height(LedgerRowHeight)
    if (focusRequester != null) {
        rowModifier = rowModifier.focusRequester(focusRequester)
    }
    rowModifier = rowModifier.onFocusChanged { focused = it.isFocused || it.hasFocus }

    Surface(
        onClick = onClick,
        modifier = rowModifier,
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Ink050,
            pressedContainerColor = Ink050,
            contentColor = labelColor,
            focusedContentColor = labelColor,
            pressedContentColor = labelColor,
        ),
        border = ClickableSurfaceDefaults.border(
            border = rowBorder,
            focusedBorder = rowBorder,
            pressedBorder = rowBorder,
        ),
        // §4.2 — a scaled full-width row reads as broken.
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 1f),
        // §7 flag 2 — the glow is decoration only; the edge-light bar is the signal here.
        glow = ClickableSurfaceDefaults.glow(glow = Glow.None),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (focused) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(EdgeLightWidth)
                        .height(LedgerRowHeight)
                        .background(Color.White),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = Dimens.ItemSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = labelColor.copy(alpha = if (enabled) 1f else Alpha.TextTertiary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (value.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(Dimens.ItemSpacing))
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * §4.3 — the group list's own full-width row: same [Ink050] + edge-light focus treatment as
 * [ActionableRow], but the label colour also encodes *selection* (not just focus) — [Paper] for
 * the active group, [Ash600] for the rest, so the group pane keeps its own state readable while
 * the field pane holds focus.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun GroupRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    leftFocusRequester: FocusRequester,
) {
    var focused by remember { mutableStateOf(false) }
    val labelColor = when {
        focused -> Paper
        selected -> Paper
        else -> Ash600
    }

    var rowModifier: Modifier = Modifier
        .fillMaxWidth()
        .height(LedgerRowHeight)
        .focusProperties { left = leftFocusRequester }
    if (focusRequester != null) {
        rowModifier = rowModifier.focusRequester(focusRequester)
    }
    rowModifier = rowModifier.onFocusChanged { focused = it.isFocused || it.hasFocus }

    Surface(
        onClick = onClick,
        modifier = rowModifier,
        shape = ClickableSurfaceDefaults.shape(MaterialTheme.shapes.small),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Ink050,
            pressedContainerColor = Ink050,
            contentColor = labelColor,
            focusedContentColor = Paper,
            pressedContentColor = Paper,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border.None,
            focusedBorder = Border.None,
            pressedBorder = Border.None,
        ),
        scale = ClickableSurfaceDefaults.scale(scale = 1f, focusedScale = 1f, pressedScale = 1f),
        glow = ClickableSurfaceDefaults.glow(glow = Glow.None),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (focused) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(EdgeLightWidth)
                        .height(LedgerRowHeight)
                        .background(Color.White),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = Dimens.ItemSpacing),
            )
        }
    }
}

/**
 * §4.2 — dialogs are plane 2: [Ink150] fill, a `1.dp #FFFFFF @8%` hairline, 8dp radius. Cancel owns
 * focus, so a stray ENTER on arrival never signs anyone out.
 */
@Composable
private fun SignOutDialog(
    userName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(DialogWidth)
                .background(Ink150, MaterialTheme.shapes.extraLarge)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = Alpha.Hairline),
                    shape = MaterialTheme.shapes.extraLarge,
                )
                .padding(32.dp),
        ) {
            Text(
                text = stringResource(R.string.sign_out),
                style = MaterialTheme.typography.headlineSmall,
                color = Paper,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (userName.isEmpty()) {
                    stringResource(R.string.settings_sign_out_confirm_generic)
                } else {
                    stringResource(R.string.settings_sign_out_confirm_named, userName)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing)) {
                SlabButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    primary = false,
                    modifier = Modifier.focusRequester(cancelFocus),
                )
                SlabButton(
                    text = stringResource(R.string.sign_out),
                    onClick = onConfirm,
                )
            }
        }
    }
}

/** §4.3 — a group separator: `1.dp` `#FFFFFF @8%`, insets 0. Used exactly twice on this screen. */
@Composable
private fun Hairline() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color.White.copy(alpha = Alpha.Hairline)),
    )
}
