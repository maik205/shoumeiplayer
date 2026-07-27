package com.maik205.shoumeiplayer.ui.screens.serverentry

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.di.AppContainer
import com.maik205.shoumeiplayer.ui.components.SlabButton
import com.maik205.shoumeiplayer.ui.navigation.containerViewModel
import com.maik205.shoumeiplayer.ui.theme.Alpha
import com.maik205.shoumeiplayer.ui.theme.Ash600
import com.maik205.shoumeiplayer.ui.theme.Dimens
import com.maik205.shoumeiplayer.ui.theme.Ink000
import com.maik205.shoumeiplayer.ui.theme.Ink300
import com.maik205.shoumeiplayer.ui.theme.Paper
import com.maik205.shoumeiplayer.ui.theme.Signal
import com.maik205.shoumeiplayer.ui.theme.Tungsten

/** §5.5 — one left-anchored column at the 96dp gutter, 480dp wide. */
private val ColumnWidth = 480.dp

/** §5.5 — the app name sits quietly at x=96, y=48; the heading carries the screen. */
private val AppNameTop = 48.dp

/** §5.5 — the heading's baseline block starts at y=132. */
private val HeadingTop = 132.dp

// §5.5 — the rest of the column is spaced off the y-grid: eyebrow 236, field 264, helper 332,
// action 396. The gaps below are those deltas minus each block's own height.
private val HeadingToEyebrow = 44.dp
private val EyebrowToField = 10.dp
private val FieldToHelper = 12.dp
private val HelperBlockHeight = 32.dp
private val HelperToAction = 32.dp

/** §5.5 — the underline-as-progress rule. */
private val RuleHeight = 2.dp

/** §5.5 — error shake: 160ms, ±8dp, 2 cycles, horizontal, on the field. */
private const val SHAKE_MS = 160
private const val SHAKE_DP = 8f

/**
 * §5.5 — ServerEntry.
 *
 * Pure black, one left-anchored column at the 96dp gutter, and a great deal of deliberate empty
 * space to its right. The focal element is the plain-language heading: v1's ghost numeral, step
 * counter, kanji mark and tracked-out wordmark are all gone, because a number that counts nothing
 * and a wordmark used once are chrome pretending to be design (§1).
 *
 * The field is an *underline*, not a box, and that same 2dp rule is the progress indicator: while
 * connecting it becomes an indeterminate bar exactly where it already was, so nothing on the screen
 * moves and no spinner ever appears.
 */
@Composable
fun ServerEntryScreen(onConnected: () -> Unit) {
    val viewModel = containerViewModel { container: AppContainer ->
        ServerEntryViewModel(container.authRepository)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.navigateToLogin.collect { onConnected() }
    }

    // §7 flag 5 — material3 `TextField` on TV needs an explicit FocusRequester; its default focus
    // is far too quiet at 10 feet.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val shake = remember { Animatable(0f) }
    LaunchedEffect(uiState.error) {
        if (uiState.error != null) shake.shakeOnce()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink000),
    ) {
        // §5.5 — the app name appears exactly once, and quietly.
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleSmall,
            color = Paper.copy(alpha = Alpha.TextDisabled),
            maxLines = 1,
            modifier = Modifier.offset(x = Dimens.Gutter, y = AppNameTop),
        )

        Column(
            modifier = Modifier
                .padding(start = Dimens.Gutter, top = HeadingTop)
                .width(ColumnWidth),
        ) {
            Text(
                text = stringResource(R.string.connect_to_your_server),
                style = MaterialTheme.typography.displayLarge,
                color = Paper,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(HeadingToEyebrow))
            // §3.1 — the app's single rationed eyebrow: it stands in for a floating TextField label,
            // which would otherwise shrink below the 12sp legibility floor.
            Text(
                text = stringResource(R.string.server_address).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = Paper.copy(alpha = Alpha.TextTertiary),
            )

            Spacer(modifier = Modifier.height(EyebrowToField))
            UnderlineField(
                value = uiState.url,
                onValueChange = viewModel::onUrlChange,
                loading = uiState.loading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { viewModel.connect() }),
                modifier = Modifier
                    .offset(x = shake.value.dp)
                    .focusRequester(focusRequester),
            )

            Spacer(modifier = Modifier.height(FieldToHelper))
            Box(modifier = Modifier.height(HelperBlockHeight)) {
                val error = uiState.error
                val serverName = uiState.serverName
                when {
                    // §6 — errors are inline and in plain language: no dialog, no toast, no icon.
                    error != null -> StatusLine(text = error, color = Signal)
                    serverName != null -> StatusLine(
                        text = connectedLine(serverName, uiState.serverVersion),
                        color = Tungsten,
                    )
                }
            }

            Spacer(modifier = Modifier.height(HelperToAction))
            SlabButton(
                text = stringResource(R.string.connect),
                // The slab has no disabled state (§4.2); guard the call instead so a second press
                // while connecting cannot re-issue the request.
                onClick = { if (!uiState.loading) viewModel.connect() },
            )
        }
    }
}

/**
 * §5.5 — the field is an underline, not a box. The rule under it *is* the field's indicator; while
 * [loading] the indicator is suppressed and a 2dp indeterminate bar takes its place, so nothing
 * shifts and no centred spinner is ever needed.
 */
@Composable
private fun UnderlineField(
    value: String,
    onValueChange: (String) -> Unit,
    loading: Boolean,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            enabled = !loading,
            // No label — the eyebrow above replaces it; a floating label shrinks below 12sp (§5.5).
            textStyle = MaterialTheme.typography.displaySmall,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.White,
                unfocusedIndicatorColor = Ink300,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = Tungsten,
                focusedTextColor = Paper,
                unfocusedTextColor = Ash600,
                disabledTextColor = Paper,
            ),
            modifier = modifier.fillMaxWidth(),
        )
        if (loading) {
            LinearProgressIndicator(
                color = Color.White,
                trackColor = Ink300,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(RuleHeight),
            )
        }
    }
}

/**
 * §5.5 — success and failure share one construction: a single line of `bodySmall`, no square, no
 * check icon, no green. The colour is the whole difference, and it is the only thing that differs.
 */
@Composable
private fun StatusLine(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * §5.5 — `Connected to "Basement NAS", version 10.9.11`. The version is dropped rather than faked
 * when the server did not report one; §1 has no room for a fake-precise number.
 */
internal fun connectedLine(serverName: String, version: String?): String =
    if (version.isNullOrBlank()) {
        "Connected to \"$serverName\""
    } else {
        "Connected to \"$serverName\", version $version"
    }

/** §5.5 — 160ms, ±8dp, 2 cycles. Never a dialog, never a toast. */
private suspend fun Animatable<Float, AnimationVector1D>.shakeOnce() {
    snapTo(0f)
    animateTo(
        targetValue = 0f,
        animationSpec = keyframes {
            durationMillis = SHAKE_MS
            0f at 0
            SHAKE_DP at SHAKE_MS / 8
            -SHAKE_DP at SHAKE_MS * 3 / 8
            SHAKE_DP at SHAKE_MS * 5 / 8
            -SHAKE_DP at SHAKE_MS * 7 / 8
            0f at SHAKE_MS
        },
    )
}
