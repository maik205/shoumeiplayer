package com.maik205.shoumeiplayer.ui.screens.login

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

/** §5.5 — the same left-anchored column as ServerEntry: gutter x=96, width 480. */
private val ColumnWidth = 480.dp

private val AppNameTop = 48.dp

/**
 * §5.5 — Login runs the heading 36dp higher than ServerEntry so two field blocks, the helper line,
 * the slab and the Quick Connect note all clear the 486dp safe height without crowding.
 */
private val HeadingTop = 96.dp
private val HeadingToFields = 20.dp

/** §5.5 — "two field blocks 88dp apart". Eyebrow (18) + gap (10) + field (~56) is one block. */
private val FieldBlockPitch = 88.dp
private val FieldBlockHeight = 84.dp

private val EyebrowToField = 10.dp
private val FieldToHelper = 12.dp
private val HelperBlockHeight = 32.dp
private val HelperToAction = 32.dp

private val RuleHeight = 2.dp

private const val SHAKE_MS = 160
private const val SHAKE_DP = 8f

/**
 * §5.5 — Login: the ServerEntry column with two field blocks and a quiet note at its foot.
 *
 * Same deletions as ServerEntry: no ghost numeral, no step counter, no kanji mark, no wordmark, and
 * no hairline above the Quick Connect note — a rule there separates a sentence from nothing, which
 * is exactly the kind of line §4.3 bans.
 *
 * Both fields are underlines; while signing in the rule under the password becomes a 2dp
 * indeterminate bar in its own place. A failed sign-in shakes the fields 160ms / ±8dp / 2 cycles,
 * never a dialog and never a toast.
 */
@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val viewModel = containerViewModel { container: AppContainer -> LoginViewModel(container.authRepository) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.navigateToHome.collect { onLoggedIn() }
    }

    // §7 flag 5 — explicit FocusRequester; material3 `TextField` focus is far too quiet at 10 feet.
    val usernameFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { usernameFocusRequester.requestFocus() }

    val shake = remember { Animatable(0f) }
    LaunchedEffect(uiState.error) {
        if (uiState.error != null) shake.shakeOnce()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink000),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleSmall,
            color = Paper.copy(alpha = Alpha.TextDisabled),
            maxLines = 1,
            modifier = Modifier.offset(x = Dimens.Gutter, y = AppNameTop),
        )

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = Dimens.Gutter, top = HeadingTop, bottom = Dimens.OverscanVertical)
                .width(ColumnWidth),
        ) {
            Text(
                text = stringResource(R.string.sign_in),
                style = MaterialTheme.typography.displayLarge,
                color = Paper,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(HeadingToFields))
            FieldBlock(
                eyebrow = stringResource(R.string.username),
                value = uiState.username,
                onValueChange = viewModel::onUsernameChange,
                loading = uiState.loading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                showProgress = false,
                modifier = Modifier
                    .offset(x = shake.value.dp)
                    .focusRequester(usernameFocusRequester),
            )

            Spacer(modifier = Modifier.height(FieldBlockPitch - FieldBlockHeight))
            FieldBlock(
                eyebrow = stringResource(R.string.password),
                value = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                loading = uiState.loading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { viewModel.login() }),
                visualTransformation = PasswordVisualTransformation(),
                showProgress = true,
                modifier = Modifier.offset(x = shake.value.dp),
            )

            Spacer(modifier = Modifier.height(FieldToHelper))
            Box(modifier = Modifier.height(HelperBlockHeight)) {
                // §6 — the error is inline, in plain language, in [Signal]; no square, no icon.
                uiState.error?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = Signal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(HelperToAction))
            SlabButton(
                text = stringResource(R.string.sign_in),
                // `LoginViewModel.login()` already no-ops while loading; the slab has no disabled
                // state (§4.2), so the guard stays at the call site too.
                onClick = { if (!uiState.loading) viewModel.login() },
            )

            // §5.5 — a quiet note at the foot of the column, no rule above it, non-focusable.
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.quick_connect_coming_soon),
                style = MaterialTheme.typography.bodySmall,
                color = Paper.copy(alpha = Alpha.TextDisabled),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * §5.5 / §3.1 — one field block: the rationed uppercase eyebrow (`labelLarge @0.55`) over an
 * underline field. The eyebrow exists only because a floating `TextField` label shrinks below the
 * 12sp legibility floor; these two labels and ServerEntry's are the app's whole eyebrow budget.
 */
@Composable
private fun FieldBlock(
    eyebrow: String,
    value: String,
    onValueChange: (String) -> Unit,
    loading: Boolean,
    showProgress: Boolean,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = eyebrow.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = Paper.copy(alpha = Alpha.TextTertiary),
        )
        Spacer(modifier = Modifier.height(EyebrowToField))
        Box(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                enabled = !loading,
                // No label — the eyebrow replaces it (a floating label shrinks below 12sp).
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
            if (loading && showProgress) {
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
}

/** §5.5 — 160ms, ±8dp, 2 cycles. */
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
