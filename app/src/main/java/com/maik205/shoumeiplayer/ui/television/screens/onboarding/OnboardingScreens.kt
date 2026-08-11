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
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.testTag
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
import com.maik205.shoumeiplayer.ui.television.components.TelevisionLoadingShape
import com.maik205.shoumeiplayer.ui.television.components.TelevisionLoadingState
import com.maik205.shoumeiplayer.ui.television.components.televisionHorizontalWrap
import com.maik205.shoumeiplayer.ui.television.components.televisionBringIntoViewOnFocus
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionTheme
import kotlinx.coroutines.isActive

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
    val connectFocus = remember { FocusRequester() }
    var initialFocusAssigned by remember { mutableStateOf(false) }
    var insecureWasPresent by remember { mutableStateOf(false) }
    var insecureReturnFocus by remember { mutableStateOf<FocusRequester?>(null) }

    LaunchedEffect(state.insecureConnection) {
        if (state.insecureConnection != null) {
            insecureWasPresent = true
            while (isActive && !runCatching { insecureAllowFocus.requestFocus() }.getOrDefault(false)) {
                withFrameNanos { }
            }
        } else if (insecureWasPresent) {
            insecureWasPresent = false
            runCatching { (insecureReturnFocus ?: addressFocus).requestFocus() }
            insecureReturnFocus = null
        }
    }

    BackHandler(enabled = state.insecureConnection != null, onBack = onCancelInsecureConnection)

    LaunchedEffect(state.servers, state.discovering, state.insecureConnection) {
        if (state.insecureConnection == null && !state.discovering && !initialFocusAssigned) {
            val target = when {
                state.servers.isNotEmpty() -> firstServerFocus
                state.discoveryError == null -> addressFocus
                else -> null
            }
            if (target != null) {
                while (isActive && !runCatching { target.requestFocus() }.getOrDefault(false)) {
                    withFrameNanos { }
                }
            }
            initialFocusAssigned = true
        }
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
                        // ConnectScreen owns entry focus. Let it place focus in the address field;
                        // otherwise this retry action steals focus and leaves no D-pad path down.
                        requestInitialFocus = false,
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
                                    insecureReturnFocus = if (server == state.servers.firstOrNull()) {
                                        firstServerFocus
                                    } else {
                                        addressFocus
                                    }
                                    onServerClick(server)
                                },
                                focusRequester = if (server == state.servers.firstOrNull()) firstServerFocus else null,
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
                            insecureReturnFocus = addressFocus
                            onConnect()
                        },
                        focusRequester = addressFocus,
                        modifier = Modifier
                            .weight(1f)
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                                    runCatching { connectFocus.requestFocus() }.getOrDefault(false)
                                } else {
                                    false
                                }
                            },
                    )
                    Spacer(Modifier.width(12.dp))
                    TelevisionFocusSurface(
                        onClick = {
                            insecureReturnFocus = connectFocus
                            onConnect()
                        },
                        enabled = state.address.isNotBlank() && !state.connecting,
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
                                enabled = !state.connecting,
                                focusRequester = insecureAllowFocus,
                                expandedWidth = 164.dp,
                            )
                            TelevisionFocusRevealButton(
                                label = stringResource(R.string.tv_cancel_insecure_connection),
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                onClick = onCancelInsecureConnection,
                                enabled = !state.connecting,
                                expandedWidth = 116.dp,
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
) {
    TelevisionFocusSurface(
        onClick = onClick,
        focusRequester = focusRequester,
        scaleTo = TelevisionFocusScale.Navigation,
        restingAlpha = 0.38f,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
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
    LaunchedEffect(state.profiles) {
        if (state.profiles.isNotEmpty() && !initialProfileFocusAssigned) {
            firstFocus.requestFocus()
            initialProfileFocusAssigned = true
        }
    }

    TelevisionBackground(imageUrl = backdropUrl) {
        Box(Modifier.fillMaxSize()) {
            TelevisionFocusRevealButton(
                label = stringResource(R.string.tv_server),
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                expandedWidth = 104.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = TelevisionDimensions.SafeHorizontal,
                        top = TelevisionDimensions.SafeTop,
                    ),
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
                                    onClick = { onProfileClick(profile) },
                                    enabled = !state.loading,
                                    focusRequester = profileFocusRequesters.getOrNull(index),
                                    modifier = Modifier.televisionHorizontalWrap(
                                        index,
                                        profileFocusRequesters,
                                        profileRailState,
                                    ),
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
    val quickConnectFocusable = state.quickConnectAvailable &&
        !state.quickConnectChecking &&
        !state.quickConnectLoading
    val signInFocusable = state.userName.isNotBlank() && !state.signingIn

    LaunchedEffect(Unit) {
        if (state.userName.isBlank()) usernameFocus.requestFocus() else passwordFocus.requestFocus()
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
                            down = if (signInFocusable) signInFocus else forgotPasswordFocus
                        },
                        upFocusRequester = usernameFocus,
                        downFocusRequester = if (signInFocusable) signInFocus else forgotPasswordFocus,
                    )

                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TelevisionFocusRevealButton(
                                label = stringResource(R.string.sign_in),
                                icon = Icons.AutoMirrored.Filled.ArrowForward,
                                onClick = onSignIn,
                                enabled = signInFocusable,
                                expandedWidth = 104.dp,
                                focusRequester = signInFocus,
                                modifier = Modifier.focusProperties {
                                    left = FocusRequester.Cancel
                                    up = passwordFocus
                                    right = forgotPasswordFocus
                                    down = if (quickConnectFocusable) {
                                        quickConnectFocus
                                    } else {
                                        FocusRequester.Cancel
                                    }
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
                        TelevisionFocusRevealButton(
                            label = stringResource(R.string.tv_forgot_password),
                            icon = Icons.Default.LockReset,
                            onClick = onForgotPassword,
                            expandedWidth = 164.dp,
                            focusRequester = forgotPasswordFocus,
                            modifier = Modifier.focusProperties {
                                left = if (signInFocusable) {
                                    signInFocus
                                } else {
                                    FocusRequester.Cancel
                                }
                                up = passwordFocus
                                right = FocusRequester.Cancel
                                down = if (quickConnectFocusable) quickConnectFocus else FocusRequester.Cancel
                            },
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.quickConnectChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else if (state.quickConnectAvailable) {
                            TelevisionFocusRevealButton(
                                label = state.quickConnectCode
                                    ?: stringResource(R.string.tv_quick_connect),
                                icon = Icons.Default.Key,
                                onClick = onQuickConnect,
                                enabled = !state.quickConnectLoading,
                                selected = state.quickConnectCode != null,
                                expandedWidth = if (state.quickConnectCode == null) 148.dp else 118.dp,
                                focusRequester = quickConnectFocus,
                                modifier = Modifier.focusProperties {
                                    left = FocusRequester.Cancel
                                    up = if (signInFocusable) signInFocus else forgotPasswordFocus
                                    right = FocusRequester.Cancel
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
    val requestFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(state.result, state.error) {
        if (state.result != null || state.error != null) {
            if (state.userName.isNotBlank()) requestFocus.requestFocus() else backFocus.requestFocus()
        } else {
            usernameFocus.requestFocus()
        }
    }

    TelevisionBackground(imageUrl = backdropUrl) {
        Box(Modifier.fillMaxSize()) {
            TelevisionFocusRevealButton(
                label = stringResource(R.string.sign_in),
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                focusRequester = backFocus,
                expandedWidth = 106.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = TelevisionDimensions.SafeHorizontal,
                        top = TelevisionDimensions.SafeTop,
                    ),
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
                        modifier = Modifier.testTag("recovery_username"),
                    )
                    Spacer(Modifier.height(24.dp))
                    TelevisionFocusRevealButton(
                        label = stringResource(
                            if (state.error != null) {
                                R.string.retry
                            } else if (state.result == null) {
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
                        focusRequester = requestFocus,
                        expandedWidth = 154.dp,
                        modifier = Modifier.testTag("recovery_action"),
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
                            modifier = Modifier.testTag("recovery_message"),
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
) {
    val primaryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { primaryFocus.requestFocus() }

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
    val keyboardController = LocalSoftwareKeyboardController.current
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
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        keyboardController?.show()
                        true
                    }
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
            showKeyboardOnFocus = false,
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
