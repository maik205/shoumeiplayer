package com.maik205.shoumeiplayer.ui.television.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.ui.i18n.resolve
import com.maik205.shoumeiplayer.ui.television.components.TelevisionBackground
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusRevealButton
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusScale
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusSurface
import com.maik205.shoumeiplayer.ui.television.components.TelevisionEmptyState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionErrorState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusHandoff
import com.maik205.shoumeiplayer.ui.television.components.TelevisionLoadingShape
import com.maik205.shoumeiplayer.ui.television.components.TelevisionLoadingState
import com.maik205.shoumeiplayer.ui.television.components.televisionHorizontalWrap
import com.maik205.shoumeiplayer.ui.television.components.televisionBringIntoViewOnFocus
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionTheme

@Composable
fun ConnectScreen(
    state: ConnectUiState,
    onAddressChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onServerClick: (ServerChoiceUi) -> Unit,
    onConnect: () -> Unit,
    onRetryConnection: () -> Unit,
    onAcceptInsecureConnection: () -> Unit,
    onCancelInsecureConnection: () -> Unit,
) {
    val addressFocus = remember { FocusRequester() }
    val firstServerFocus = remember { FocusRequester() }
    val insecureAllowFocus = remember { FocusRequester() }
    var initialFocusAssigned by remember { mutableStateOf(false) }
    var insecureAllowFocused by remember { mutableStateOf(false) }
    var insecureCancelFocused by remember { mutableStateOf(false) }
    var insecureOpener by remember { mutableStateOf<FocusRequester?>(null) }
    val refreshFocus = remember { FocusRequester() }
    val connectFocus = remember { FocusRequester() }

    // A failed connection used to leave focus wherever the attempt had taken it -- the initiating
    // control may itself have been removed on the way. Send the viewer back to whatever they
    // pressed (ONB-003).
    LaunchedEffect(state.connecting, state.error) {
        if (state.connecting || state.error == null) return@LaunchedEffect
        val origin = insecureOpener ?: addressFocus
        runCatching { origin.requestFocus() }
    }

    LaunchedEffect(state.insecureConnection) {
        if (state.insecureConnection != null) {
            runCatching { insecureAllowFocus.requestFocus() }
        }
    }

    // Whichever way the confirmation is dismissed -- Cancel, Back, or an Accept that fails --
    // focus belongs to the control that raised it, not wherever it happens to land.
    TelevisionFocusHandoff(
        present = state.insecureConnection != null,
        focused = insecureAllowFocused || insecureCancelFocused,
        insecureOpener,
        addressFocus,
    )

    BackHandler(enabled = state.insecureConnection != null) {
        onCancelInsecureConnection()
    }

    // Claiming the initial focus was a one-way latch, so a discovery error that removed the
    // original target left the flag set and no later server list or address field was ever
    // focused. The claim is only recorded once a requester actually accepts it.
    LaunchedEffect(state.servers, state.discovering, state.discoveryError) {
        if (state.discovering || initialFocusAssigned) return@LaunchedEffect
        val target = when {
            state.servers.isNotEmpty() -> firstServerFocus
            // A discovery error renders its own Retry, which owns focus; do not fight it.
            state.discoveryError == null -> addressFocus
            else -> null
        } ?: return@LaunchedEffect
        if (runCatching { target.requestFocus() }.isSuccess) initialFocusAssigned = true
    }

    TelevisionBackground(imageUrl = null) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = TelevisionDimensions.SafeHorizontal + 18.dp,
                    vertical = TelevisionDimensions.SafeTop,
                ),
        ) {
            OnboardingStatement(
                icon = Icons.Default.CastConnected,
                title = stringResource(R.string.tv_choose_media_server),
                modifier = Modifier
                    .weight(1.05f)
                    .fillMaxHeight(),
            )

            Column(
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxHeight()
                    .padding(top = 132.dp, end = 30.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.tv_available_servers),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TelevisionFocusRevealButton(
                        label = stringResource(R.string.tv_refresh),
                        icon = Icons.Default.Refresh,
                        onClick = onRefresh,
                        expandedWidth = 108.dp,
                        focusRequester = refreshFocus,
                        // Refresh sits above the server list; Down belongs to whichever of the
                        // list or the address field is actually on screen (ONB-008).
                        modifier = Modifier.focusProperties {
                            down = if (state.servers.isNotEmpty()) firstServerFocus else addressFocus
                        },
                    )
                }

                Spacer(Modifier.height(18.dp))
                when {
                    state.discovering && state.servers.isEmpty() -> TelevisionLoadingState(
                        label = stringResource(R.string.tv_looking_nearby),
                        shape = TelevisionLoadingShape.Rail,
                        modifier = Modifier.height(126.dp),
                    )

                    state.discoveryError != null && state.servers.isEmpty() -> TelevisionErrorState(
                        title = stringResource(R.string.tv_discovery_failed),
                        message = state.discoveryError.resolve(),
                        onRetry = onRefresh,
                        retryLabel = stringResource(R.string.retry),
                        modifier = Modifier.height(126.dp),
                    )

                    state.servers.isEmpty() -> TelevisionEmptyState(
                        title = stringResource(R.string.tv_no_servers_found),
                        actionLabel = stringResource(R.string.retry),
                        onAction = onRefresh,
                        requestInitialFocus = true,
                        modifier = Modifier.height(126.dp),
                    )

                    else -> LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(126.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        items(state.servers, key = ServerChoiceUi::id) { server ->
                            ServerRow(
                                server = server,
                                onClick = {
                                    insecureOpener = if (server == state.servers.firstOrNull()) {
                                        firstServerFocus
                                    } else {
                                        null
                                    }
                                    onServerClick(server)
                                },
                                focusRequester = if (server == state.servers.firstOrNull()) firstServerFocus else null,
                                upFocusRequester = refreshFocus.takeIf {
                                    server == state.servers.firstOrNull()
                                },
                                downFocusRequester = addressFocus.takeIf {
                                    server == state.servers.lastOrNull()
                                },
                            )
                        }
                    }
                }

                if (state.discoveryError != null && state.servers.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    TelevisionErrorState(
                        title = stringResource(R.string.tv_discovery_failed),
                        message = state.discoveryError.resolve(),
                        onRetry = onRefresh,
                        retryLabel = stringResource(R.string.retry),
                        requestInitialFocus = false,
                    )
                }

                Spacer(Modifier.height(26.dp))
                Text(
                    text = stringResource(R.string.server_address),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.tv_server_scheme_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TelevisionTheme.colors.PaperSoft,
                    )
                    NativeTvField(
                        value = state.address,
                        onValueChange = onAddressChange,
                        placeholder = stringResource(R.string.tv_server_address_hint),
                        onDone = {
                            insecureOpener = addressFocus
                            onConnect()
                        },
                        focusRequester = addressFocus,
                        // A text field eats Left and Right for a caret a D-pad cannot use, so the
                        // Connect button beside it was unreachable by arrow (ONB-007).
                        modifier = Modifier
                            .weight(1f)
                            .focusProperties {
                                up = if (state.servers.isNotEmpty()) firstServerFocus else refreshFocus
                            }
                            .onPreviewKeyEvent { event ->
                                if (
                                    event.type == KeyEventType.KeyDown &&
                                    event.key == Key.DirectionRight
                                ) {
                                    runCatching { connectFocus.requestFocus() }.isSuccess
                                } else {
                                    false
                                }
                            },
                    )
                    Spacer(Modifier.width(12.dp))
                    TelevisionFocusSurface(
                        onClick = {
                            if (state.connecting) return@TelevisionFocusSurface
                            insecureOpener = addressFocus
                            onConnect()
                        },
                        // Kept focusable while connecting so the spinner does not take the remote
                        // away from the control that started the attempt (ONB-002).
                        enabled = state.address.isNotBlank(),
                        focusRequester = connectFocus,
                        scaleTo = TelevisionFocusScale.Action,
                        restingAlpha = if (state.address.isBlank()) 0.24f else 0.62f,
                        modifier = Modifier.size(44.dp),
                    ) {
                        if (state.connecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = stringResource(R.string.connect),
                                modifier = Modifier.size(23.dp),
                            )
                        }
                    }
                }

                state.insecureConnection?.let { insecureConnection ->
                    Spacer(Modifier.height(18.dp))
                    // A confirmation that leaves everything behind it reachable is not a
                    // confirmation. Trapping focus inside it makes Accept and Cancel the only
                    // two answers, which is what the question implies.
                    Column(
                        modifier = Modifier
                            .focusGroup()
                            .focusProperties { onExit = { cancelFocusChange() } },
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = TelevisionTheme.colors.PaperMuted,
                                modifier = Modifier.size(28.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.tv_insecure_http_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.tv_insecure_http_message),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TelevisionTheme.colors.PaperSoft,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = insecureConnection.address,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TelevisionTheme.colors.PaperMuted,
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TelevisionFocusRevealButton(
                                label = stringResource(R.string.tv_use_http_anyway),
                                icon = Icons.AutoMirrored.Filled.ArrowForward,
                                onClick = onAcceptInsecureConnection,
                                loading = state.connecting,
                                focusRequester = insecureAllowFocus,
                                expandedWidth = 164.dp,
                                onFocusChanged = { insecureAllowFocused = it },
                            )
                            TelevisionFocusRevealButton(
                                label = stringResource(R.string.tv_cancel_insecure_connection),
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                onClick = onCancelInsecureConnection,
                                loading = state.connecting,
                                expandedWidth = 116.dp,
                                onFocusChanged = { insecureCancelFocused = it },
                            )
                        }
                    }
                }

                val connectionError = state.error
                if (connectionError != null) {
                    Spacer(Modifier.height(12.dp))
                    TelevisionErrorState(
                        title = stringResource(R.string.tv_connection_failed),
                        message = connectionError.resolve(),
                        onRetry = onRetryConnection,
                        retryLabel = stringResource(R.string.retry),
                        requestInitialFocus = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerRow(
    server: ServerChoiceUi,
    onClick: () -> Unit,
    focusRequester: FocusRequester?,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
) {
    TelevisionFocusSurface(
        onClick = onClick,
        focusRequester = focusRequester,
        scaleTo = TelevisionFocusScale.Navigation,
        restingAlpha = 0.38f,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .focusProperties {
                upFocusRequester?.let { up = it }
                downFocusRequester?.let { down = it }
            }
            .televisionBringIntoViewOnFocus(),
    ) { focused ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(26.dp), contentAlignment = Alignment.CenterStart) {
                if (focused) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column {
                Text(
                    text = if (server.name.isBlank()) {
                        stringResource(R.string.tv_server_default_name)
                    } else {
                        server.name
                    },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                Text(
                    text = when {
                        server.active && server.hasSession && !server.userName.isNullOrBlank() ->
                            stringResource(R.string.tv_current_signed_in_as, server.userName)
                        server.active -> stringResource(R.string.tv_current_server)
                        server.hasSession && !server.userName.isNullOrBlank() ->
                            stringResource(R.string.tv_signed_in_as, server.userName)
                        server.remembered -> stringResource(R.string.tv_remembered_server)
                        else -> server.address.removePrefix("http://").removePrefix("https://")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TelevisionTheme.colors.PaperSoft,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun ProfilesScreen(
    state: ProfilesUiState,
    backdropUrl: String?,
    onProfileClick: (ProfileUi) -> Unit,
    onRetry: () -> Unit,
    onAnotherAccount: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val firstFocus = remember { FocusRequester() }
    val profileFocusRequesters = remember(state.profiles.map(ProfileUi::id), firstFocus) {
        List(state.profiles.size) { index ->
            if (index == 0) firstFocus else FocusRequester()
        }
    }
    val profileRailState = rememberLazyListState()
    var initialProfileFocusAssigned by rememberSaveable { mutableStateOf(false) }
    var pendingProfileIndex by remember { mutableStateOf<Int?>(null) }
    val serverBackFocus = remember { FocusRequester() }
    val anotherAccountFocus = remember { FocusRequester() }
    val profilesRetryFocus = remember { FocusRequester() }
    LaunchedEffect(state.profiles) {
        if (state.profiles.isNotEmpty() && !initialProfileFocusAssigned) {
            firstFocus.requestFocus()
            initialProfileFocusAssigned = true
        }
    }

    // Choosing a passwordless profile disables the whole rail while it authenticates, which takes
    // focus off the very profile that was chosen. If it then fails, the viewer was left with no
    // selected profile and no obvious way back to the one they wanted.
    LaunchedEffect(state.loading, state.error) {
        if (state.loading) return@LaunchedEffect
        val index = pendingProfileIndex ?: return@LaunchedEffect
        pendingProfileIndex = null
        if (state.error == null) return@LaunchedEffect
        runCatching { profileFocusRequesters.getOrNull(index)?.requestFocus() }
    }

    TelevisionBackground(imageUrl = backdropUrl) {
        Box(Modifier.fillMaxSize()) {
            TelevisionFocusRevealButton(
                label = stringResource(R.string.tv_server),
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                expandedWidth = 104.dp,
                focusRequester = serverBackFocus,
                // Server Back, the profile rail, and Use Another Account are one vertical chain
                // rather than three things spatial navigation has to guess between (ONB-011).
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = TelevisionDimensions.SafeHorizontal,
                        top = TelevisionDimensions.SafeTop,
                    )
                    .focusProperties {
                        down = if (state.profiles.isNotEmpty()) firstFocus else anotherAccountFocus
                    },
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = TelevisionDimensions.SafeHorizontal + 18.dp),
            ) {
                OnboardingStatement(
                    icon = null,
                    title = stringResource(R.string.tv_whos_watching),
                    modifier = Modifier
                        .weight(0.86f)
                        .fillMaxHeight(),
                )
                Column(
                    modifier = Modifier
                        .weight(1.14f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    when {
                        state.loading && state.profiles.isEmpty() -> TelevisionLoadingState(
                            label = stringResource(R.string.tv_whos_watching),
                            shape = TelevisionLoadingShape.Rail,
                            modifier = Modifier.height(170.dp),
                        )

                        state.error != null && state.profiles.isEmpty() -> TelevisionErrorState(
                            title = stringResource(R.string.tv_profiles_load_failed),
                            message = state.error.resolve(),
                            onRetry = onRetry,
                            retryLabel = stringResource(R.string.retry),
                            modifier = Modifier.height(170.dp),
                        )

                        state.profiles.isEmpty() -> TelevisionEmptyState(
                            title = stringResource(R.string.tv_no_accounts),
                            message = stringResource(R.string.tv_no_accounts_detail),
                            actionLabel = stringResource(R.string.tv_use_another_account),
                            onAction = onAnotherAccount,
                            requestInitialFocus = true,
                            modifier = Modifier.height(170.dp),
                        )

                        else -> LazyRow(
                            state = profileRailState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(170.dp)
                                .focusGroup()
                                .focusRestorer(),
                            contentPadding = PaddingValues(
                                horizontal = 8.dp,
                                vertical = 8.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(28.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            itemsIndexed(
                                state.profiles,
                                key = { index, profile -> "${profile.id}:$index" },
                            ) { index, profile ->
                                ProfileTarget(
                                    profile = profile,
                                    onClick = {
                                        pendingProfileIndex = index
                                        onProfileClick(profile)
                                    },
                                    enabled = !state.loading,
                                    focusRequester = profileFocusRequesters.getOrNull(index),
                                    modifier = Modifier
                                        .televisionHorizontalWrap(
                                            index,
                                            profileFocusRequesters,
                                            profileRailState,
                                        )
                                        .focusProperties {
                                            up = serverBackFocus
                                            down = anotherAccountFocus
                                        },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                    if (state.profiles.isNotEmpty() || state.error != null) {
                        TelevisionFocusRevealButton(
                            label = stringResource(R.string.tv_use_another_account),
                            icon = Icons.Default.Keyboard,
                            onClick = onAnotherAccount,
                            enabled = !state.loading,
                            expandedWidth = 186.dp,
                            focusRequester = anotherAccountFocus,
                            modifier = Modifier.focusProperties {
                                up = if (state.profiles.isNotEmpty()) firstFocus else serverBackFocus
                                if (state.error != null && state.profiles.isNotEmpty()) {
                                    down = profilesRetryFocus
                                }
                            },
                        )
                    }
                    if (state.loading && state.profiles.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    val profilesError = state.error
                    if (profilesError != null && state.profiles.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        TelevisionErrorState(
                            title = stringResource(R.string.tv_profiles_load_failed),
                            message = profilesError.resolve(),
                            onRetry = onRetry,
                            retryLabel = stringResource(R.string.retry),
                            requestInitialFocus = false,
                            focusRequester = profilesRetryFocus,
                            modifier = Modifier.focusProperties { up = anotherAccountFocus },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileTarget(
    profile: ProfileUi,
    onClick: () -> Unit,
    enabled: Boolean,
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    TelevisionFocusSurface(
        onClick = onClick,
        enabled = enabled,
        focusRequester = focusRequester,
        scaleTo = 1.055f,
        restingAlpha = 0.48f,
        modifier = modifier
            .width(124.dp)
            .televisionBringIntoViewOnFocus(),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(TelevisionTheme.colors.ImagePlaceholder),
                contentAlignment = Alignment.Center,
            ) {
                if (profile.imageUrl != null) {
                    AsyncImage(
                        model = profile.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    val profileLabel = if (profile.name.isBlank()) {
                        stringResource(R.string.tv_unknown)
                    } else {
                        profile.name
                    }
                    Text(
                        text = profileLabel.take(1).uppercase(),
                        style = MaterialTheme.typography.displaySmall,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (profile.name.isBlank()) {
                    stringResource(R.string.tv_unknown)
                } else {
                    profile.name
                },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun LoginScreen(
    state: LoginUiState,
    backdropUrl: String?,
    onUserNameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSignIn: () -> Unit,
    onQuickConnect: () -> Unit,
    onForgotPassword: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val backFocus = remember { FocusRequester() }
    val usernameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val forgotPasswordFocus = remember { FocusRequester() }
    val quickConnectFocus = remember { FocusRequester() }
    val signInFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // A pending action stays in the focus order and shows a spinner. Dropping it out while it ran
    // was what left the screen with no selected control when the attempt failed (ONB-013, -014).
    val quickConnectFocusable = state.quickConnectAvailable && !state.quickConnectChecking
    val signInFocusable = state.userName.isNotBlank()

    LaunchedEffect(Unit) {
        if (state.userName.isBlank()) usernameFocus.requestFocus() else passwordFocus.requestFocus()
    }

    // Sign In and Quick Connect both make themselves unfocusable while they run, and submitting
    // the password from the IME clears focus outright. When the attempt then fails, nothing was
    // asking for focus back and the screen was left with no selected control at all. Put the
    // viewer on the button that failed, once it can accept focus again.
    LaunchedEffect(state.signingIn, state.quickConnectLoading, state.error) {
        if (state.error == null || state.signingIn || state.quickConnectLoading) {
            return@LaunchedEffect
        }
        runCatching { signInFocus.requestFocus() }
    }

    TelevisionBackground(imageUrl = backdropUrl) {
        Box(Modifier.fillMaxSize()) {
            TelevisionFocusRevealButton(
                label = stringResource(R.string.tv_profiles),
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                expandedWidth = 112.dp,
                focusRequester = backFocus,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = TelevisionDimensions.SafeHorizontal,
                        top = TelevisionDimensions.SafeTop,
                    )
                    .focusProperties {
                        left = FocusRequester.Cancel
                        up = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                        down = usernameFocus
                    },
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = TelevisionDimensions.SafeHorizontal + 18.dp),
            ) {
                OnboardingStatement(
                    icon = null,
                    title = if (state.userName.isBlank()) {
                        stringResource(R.string.sign_in)
                    } else {
                        stringResource(R.string.tv_welcome_back, state.userName)
                    },
                    modifier = Modifier
                        .weight(1.02f)
                        .fillMaxHeight(),
                )
                Column(
                    modifier = Modifier
                        .weight(0.98f)
                        .fillMaxHeight()
                        .padding(end = 64.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(R.string.username), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    NativeTvField(
                        value = state.userName,
                        onValueChange = onUserNameChange,
                        placeholder = stringResource(R.string.username),
                        onDone = { passwordFocus.requestFocus() },
                        focusRequester = usernameFocus,
                        imeAction = ImeAction.Next,
                        modifier = Modifier.focusProperties {
                            up = backFocus
                            down = passwordFocus
                        },
                        upFocusRequester = backFocus,
                        downFocusRequester = passwordFocus,
                    )
                    Spacer(Modifier.height(26.dp))
                    Text(stringResource(R.string.password), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    NativeTvField(
                        value = state.password,
                        onValueChange = onPasswordChange,
                        placeholder = stringResource(R.string.password),
                        onDone = {
                            focusManager.clearFocus()
                            onSignIn()
                        },
                        focusRequester = passwordFocus,
                        password = true,
                        modifier = Modifier.focusProperties {
                            up = usernameFocus
                            down = forgotPasswordFocus
                        },
                        upFocusRequester = usernameFocus,
                        downFocusRequester = forgotPasswordFocus,
                    )

                    Spacer(Modifier.height(16.dp))
                    TelevisionFocusRevealButton(
                        label = stringResource(R.string.tv_forgot_password),
                        icon = Icons.Default.LockReset,
                        onClick = onForgotPassword,
                        expandedWidth = 164.dp,
                        focusRequester = forgotPasswordFocus,
                        modifier = Modifier.focusProperties {
                            left = FocusRequester.Cancel
                            up = passwordFocus
                            right = FocusRequester.Cancel
                            down = when {
                                quickConnectFocusable -> quickConnectFocus
                                signInFocusable -> signInFocus
                                else -> FocusRequester.Cancel
                            }
                        },
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.quickConnectChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(16.dp))
                        } else if (state.quickConnectAvailable) {
                            TelevisionFocusRevealButton(
                                label = state.quickConnectCode
                                    ?: stringResource(R.string.tv_quick_connect),
                                icon = Icons.Default.Key,
                                onClick = onQuickConnect,
                                loading = state.quickConnectLoading,
                                selected = state.quickConnectCode != null,
                                expandedWidth = if (state.quickConnectCode == null) 148.dp else 118.dp,
                                focusRequester = quickConnectFocus,
                                // Quick Connect is the leading control: Left is the edge of the
                                // row, not a wrap back to Sign In (ONB-015).
                                modifier = Modifier.focusProperties {
                                    left = FocusRequester.Cancel
                                    up = forgotPasswordFocus
                                    right = if (signInFocusable) signInFocus else FocusRequester.Cancel
                                    down = FocusRequester.Cancel
                                },
                            )
                            if (state.quickConnectLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .padding(start = 10.dp)
                                        .size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                        }
                        TelevisionFocusRevealButton(
                            label = stringResource(R.string.sign_in),
                            icon = Icons.AutoMirrored.Filled.ArrowForward,
                            onClick = onSignIn,
                            enabled = signInFocusable,
                            loading = state.signingIn,
                            expandedWidth = 104.dp,
                            focusRequester = signInFocus,
                            // Sign In is the trailing control, so Right is the edge.
                            modifier = Modifier.focusProperties {
                                left = if (quickConnectFocusable) {
                                    quickConnectFocus
                                } else {
                                    FocusRequester.Cancel
                                }
                                up = forgotPasswordFocus
                                right = FocusRequester.Cancel
                                down = FocusRequester.Cancel
                            },
                        )
                        if (state.signingIn) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(start = 10.dp)
                                    .size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    }

                    val loginError = state.error
                    if (loginError != null) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = loginError.resolve(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TelevisionTheme.colors.PaperMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecoveryScreen(
    state: RecoveryUiState,
    backdropUrl: String?,
    onUserNameChange: (String) -> Unit,
    onRequestReset: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val usernameFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val requestFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { usernameFocus.requestFocus() }
    // The outcome used to be delivered by focusing the result text itself: a node with no focus
    // affordance, no action, and no directional contract, which is indistinguishable from a dead
    // remote. The request action is disabled while the request runs, so focus also had nowhere to
    // come back to. Land on that action instead, once it can accept focus again.
    LaunchedEffect(state.result, state.error, state.requesting) {
        if (!state.requesting && (state.result != null || state.error != null)) {
            runCatching { requestFocus.requestFocus() }
        }
    }

    TelevisionBackground(imageUrl = backdropUrl) {
        Box(Modifier.fillMaxSize()) {
            TelevisionFocusRevealButton(
                label = stringResource(R.string.sign_in),
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                expandedWidth = 106.dp,
                focusRequester = backFocus,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = TelevisionDimensions.SafeHorizontal,
                        top = TelevisionDimensions.SafeTop,
                    )
                    .focusProperties { down = usernameFocus },
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = TelevisionDimensions.SafeHorizontal + 18.dp),
            ) {
                OnboardingStatement(
                    icon = Icons.Default.LockReset,
                    title = stringResource(R.string.tv_reset_password),
                    modifier = Modifier
                        .weight(1.02f)
                        .fillMaxHeight(),
                )
                Column(
                    modifier = Modifier
                        .weight(0.98f)
                        .fillMaxHeight()
                        .padding(end = 64.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(R.string.username), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    NativeTvField(
                        value = state.userName,
                        onValueChange = onUserNameChange,
                        placeholder = stringResource(R.string.username),
                        onDone = onRequestReset,
                        focusRequester = usernameFocus,
                    )
                    Spacer(Modifier.height(24.dp))
                    TelevisionFocusRevealButton(
                        label = stringResource(
                            if (state.result == null) {
                                R.string.tv_request_reset
                            } else {
                                R.string.tv_request_again
                            },
                        ),
                        icon = if (state.result == null) {
                            Icons.AutoMirrored.Filled.ArrowForward
                        } else {
                            Icons.Default.Refresh
                        },
                        onClick = onRequestReset,
                        enabled = state.userName.isNotBlank() && !state.requesting,
                        expandedWidth = 154.dp,
                        focusRequester = requestFocus,
                        modifier = Modifier.focusProperties { up = usernameFocus },
                    )
                    if (state.requesting) {
                        Spacer(Modifier.height(16.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    val recoveryMessage = state.result ?: state.error
                    if (recoveryMessage != null) {
                        Spacer(Modifier.height(18.dp))
                        Text(
                            text = recoveryMessage.resolve(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (state.error == null) {
                                TelevisionTheme.colors.Paper
                            } else {
                                TelevisionTheme.colors.PaperMuted
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompactStateScreen(
    title: String,
    backdropUrl: String?,
    detail: String? = null,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    val primaryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { primaryFocus.requestFocus() }
    // These screens replace the whole back stack, so without a policy of their own Back left the
    // application entirely rather than offering another way to sign in.
    if (onBack != null) BackHandler(onBack = onBack)

    TelevisionBackground(imageUrl = backdropUrl) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .width(480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
            )
            detail?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TelevisionTheme.colors.PaperMuted,
                )
            }
            Spacer(Modifier.height(30.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TelevisionFocusRevealButton(
                    label = primaryLabel,
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    onClick = onPrimary,
                    selected = true,
                    focusRequester = primaryFocus,
                    expandedWidth = 130.dp,
                )
                if (secondaryLabel != null && onSecondary != null) {
                    TelevisionFocusRevealButton(
                        label = secondaryLabel,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = onSecondary,
                        expandedWidth = 130.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingStatement(
    icon: ImageVector?,
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        icon?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                tint = TelevisionTheme.colors.Paper,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.height(34.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun NativeTvField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onDone: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .height(44.dp)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.DirectionUp -> upFocusRequester?.let {
                        it.requestFocus()
                        true
                    } ?: false
                    Key.DirectionDown -> downFocusRequester?.let {
                        it.requestFocus()
                        true
                    } ?: false
                    else -> false
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .focusProperties { canFocus = true }
            .basicMarquee(iterations = Int.MAX_VALUE),
        enabled = true,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = if (focused) TelevisionTheme.colors.Paper else TelevisionTheme.colors.PaperMuted,
        ),
        cursorBrush = SolidColor(TelevisionTheme.colors.Paper),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else KeyboardType.Uri,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(
            onDone = { onDone() },
            onNext = { onDone() },
        ),
        decorationBox = { input ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TelevisionTheme.colors.PaperDisabled,
                    )
                }
                input()
            }
        },
    )
}
