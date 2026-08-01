@file:Suppress("TooManyFunctions")

package com.nighthawkapps.lib.android.ui.screen.home.view

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ContactSupport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiPercent
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSyncMethod
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSyncStatus
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.DisableScreenTimeout
import com.nighthawkapps.lib.android.ui.common.closeDrawerMenu
import com.nighthawkapps.lib.android.ui.common.openDrawerMenu
import com.nighthawkapps.lib.android.ui.design.component.Body
import com.nighthawkapps.lib.android.ui.design.component.BodyWithFiatCurrencySymbol
import com.nighthawkapps.lib.android.ui.design.component.GradientSurface
import com.nighthawkapps.lib.android.ui.design.component.HeaderWithDrkIcon
import com.nighthawkapps.lib.android.ui.design.component.PrimaryButton
import com.nighthawkapps.lib.android.ui.design.component.TertiaryButton
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.fixture.WalletSnapshotFixture
import com.nighthawkapps.lib.android.ui.screen.home.HomeTag
import com.nighthawkapps.lib.android.ui.screen.home.model.HomeFiatConversionRateState
import com.nighthawkapps.lib.android.ui.screen.home.model.WalletDisplayValues
import com.nighthawkapps.lib.android.ui.screen.home.model.WalletSnapshot
import kotlinx.coroutines.CoroutineScope

@Preview("Home")
@Composable
private fun ComposablePreview() {
    WalletTheme(darkTheme = true) {
        GradientSurface {
            Home(
                walletSnapshot = WalletSnapshotFixture.new(),
                isUpdateAvailable = false,
                isKeepScreenOnDuringSync = false,
                isDebugMenuEnabled = false,
                isFiatConversionEnabled = false,
                isCircularProgressBarEnabled = false,
                goSeedPhrase = {},
                goSettings = {},
                goSupport = {},
                goAbout = {},
                goReceive = {},
                goSend = {},
                goHistory = {},
                resetSdk = {},
                drawerState = rememberDrawerState(DrawerValue.Closed),
                scope = rememberCoroutineScope()
            )
        }
    }
}

@Suppress("LongParameterList")
@Composable
fun Home(
    walletSnapshot: WalletSnapshot,
    isUpdateAvailable: Boolean,
    isKeepScreenOnDuringSync: Boolean?,
    isFiatConversionEnabled: Boolean,
    isCircularProgressBarEnabled: Boolean,
    isDebugMenuEnabled: Boolean,
    goSeedPhrase: () -> Unit,
    goSettings: () -> Unit,
    goSupport: () -> Unit,
    goAbout: () -> Unit,
    goReceive: () -> Unit,
    goSend: () -> Unit,
    goHistory: () -> Unit,
    resetSdk: () -> Unit,
    drawerState: DrawerState,
    scope: CoroutineScope
) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            HomeDrawer(
                onCloseDrawer = { drawerState.closeDrawerMenu(scope) },
                goSeedPhrase = goSeedPhrase,
                goSettings = goSettings,
                goSupport = goSupport,
                goAbout = goAbout
            )
        },
        content = {
            Scaffold(topBar = {
                HomeTopAppBar(
                    isDebugMenuEnabled = isDebugMenuEnabled,
                    openDrawer = { drawerState.openDrawerMenu(scope) },
                    resetSdk = resetSdk
                )
            }) { paddingValues ->
                HomeMainContent(
                    walletSnapshot = walletSnapshot,
                    isUpdateAvailable = isUpdateAvailable,
                    isKeepScreenOnDuringSync = isKeepScreenOnDuringSync,
                    isFiatConversionEnabled = isFiatConversionEnabled,
                    isCircularProgressBarEnabled = isCircularProgressBarEnabled,
                    goReceive = goReceive,
                    goSend = goSend,
                    goHistory = goHistory,
                    modifier =
                        Modifier.padding(
                            top = paddingValues.calculateTopPadding() + WalletTheme.dimens.spacingHuge,
                            bottom = paddingValues.calculateBottomPadding() + WalletTheme.dimens.spacingDefault,
                            start = WalletTheme.dimens.spacingDefault,
                            end = WalletTheme.dimens.spacingDefault
                        )
                )
            }
        }
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HomeTopAppBar(
    isDebugMenuEnabled: Boolean,
    openDrawer: () -> Unit,
    resetSdk: () -> Unit,
) {
    TopAppBar(
        title = { Text(text = stringResource(id = R.string.app_name)) },
        navigationIcon = {
            IconButton(
                onClick = openDrawer,
                modifier = Modifier.testTag(HomeTag.DRAWER_MENU_OPEN_BUTTON)
            ) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = stringResource(R.string.home_menu_content_description)
                )
            }
        },
        actions = {
            if (isDebugMenuEnabled) {
                DebugMenu(resetSdk)
            }
        }
    )
}

@Composable
private fun DebugMenu(resetSdk: () -> Unit) {
    Column {
        var expanded by rememberSaveable { mutableStateOf(false) }
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = null)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Throw Uncaught Exception") },
                onClick = {
                    // Supposed to be generic, for manual debugging only
                    @Suppress("TooGenericExceptionThrown")
                    throw RuntimeException("Manually crashed from debug menu")
                }
            )
            DropdownMenuItem(
                text = { Text("Report Caught Exception") },
                onClick = {
                    // Eventually this shouldn't rely on the Android implementation, but rather an expect/actual
                    // should be used at the crash API level.
                    /*GlobalCrashReporter.reportCaughtException(
                        RuntimeException("Manually caught exception from debug menu")
                    )*/
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Reset SDK") },
                onClick = {
                    resetSdk()
                    expanded = false
                }
            )
        }
    }
}

@Composable
private fun HomeDrawer(
    onCloseDrawer: () -> Unit,
    goSeedPhrase: () -> Unit,
    goSettings: () -> Unit,
    goSupport: () -> Unit,
    goAbout: () -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier.testTag(HomeTag.DRAWER_MENU)
    ) {
        Spacer(Modifier.height(WalletTheme.dimens.spacingDefault))
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Password, contentDescription = null) },
            label = { Text(stringResource(id = R.string.home_menu_seed_phrase)) },
            selected = false,
            onClick = {
                onCloseDrawer()
                goSeedPhrase()
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
            label = { Text(stringResource(id = R.string.home_menu_settings)) },
            selected = false,
            onClick = {
                onCloseDrawer()
                goSettings()
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.AutoMirrored.Filled.ContactSupport, contentDescription = null) },
            label = { Text(stringResource(id = R.string.home_menu_support)) },
            selected = false,
            onClick = {
                onCloseDrawer()
                goSupport()
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            label = { Text(stringResource(id = R.string.home_menu_about)) },
            selected = false,
            onClick = {
                onCloseDrawer()
                goAbout()
            },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
    }
}

@Suppress("LongParameterList")
@Composable
private fun HomeMainContent(
    walletSnapshot: WalletSnapshot,
    isUpdateAvailable: Boolean,
    isKeepScreenOnDuringSync: Boolean?,
    isFiatConversionEnabled: Boolean,
    isCircularProgressBarEnabled: Boolean,
    goReceive: () -> Unit,
    goSend: () -> Unit,
    goHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        Modifier
            .fillMaxHeight()
            .verticalScroll(
                rememberScrollState()
            ).then(modifier)
    ) {
        Status(
            walletSnapshot,
            isUpdateAvailable,
            isFiatConversionEnabled,
            isCircularProgressBarEnabled
        )

        if (isSyncing(walletSnapshot.status)) {
            Spacer(modifier = Modifier.height(WalletTheme.dimens.spacingLarge))
            Body(text = stringResource(id = R.string.home_information))
        }

        Spacer(modifier = Modifier.height(WalletTheme.dimens.spacingLarge))

        PrimaryButton(
            onClick = goSend,
            text = stringResource(R.string.home_button_send),
            outerPaddingValues =
                PaddingValues(
                    horizontal = WalletTheme.dimens.spacingNone,
                    vertical = WalletTheme.dimens.spacingSmall
                )
        )
        PrimaryButton(
            onClick = goReceive,
            text = stringResource(R.string.home_button_receive),
            outerPaddingValues =
                PaddingValues(
                    horizontal = WalletTheme.dimens.spacingNone,
                    vertical = WalletTheme.dimens.spacingSmall
                )
        )

        TertiaryButton(onClick = goHistory, text = stringResource(R.string.home_button_history))

        if (isKeepScreenOnDuringSync == true && isSyncing(walletSnapshot.status)) {
            DisableScreenTimeout()
        }
    }
}

private fun isSyncing(status: DarkfiSyncStatus): Boolean =
    status == DarkfiSyncStatus.SYNCING ||
        status == DarkfiSyncStatus.CONNECTING ||
        status == DarkfiSyncStatus.RETRYING

@Composable
@Suppress("LongMethod", "MagicNumber")
private fun Status(
    walletSnapshot: WalletSnapshot,
    updateAvailable: Boolean,
    isFiatConversionEnabled: Boolean,
    isCircularProgressBarEnabled: Boolean
) {
    val configuration = LocalConfiguration.current
    val contentSizeRatioRatio =
        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            0.45f
        } else {
            0.9f
        }

    // UI parts sizes
    val progressCircleStroke = 12.dp
    val progressCirclePadding = progressCircleStroke + 6.dp
    val contentPadding = progressCircleStroke + progressCirclePadding + 10.dp

    val walletDisplayValues =
        WalletDisplayValues.getNextValues(
            LocalContext.current,
            walletSnapshot,
            updateAvailable
        )

    // wrapper box
    Box(
        Modifier
            .fillMaxWidth()
            .testTag(HomeTag.STATUS_VIEWS),
        contentAlignment = Alignment.Center
    ) {
        // relatively sized box
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(contentSizeRatioRatio)
                    .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            // progress circle
            if (isCircularProgressBarEnabled) {
                if (walletDisplayValues.progress.decimal > DarkfiPercent.ZERO_PERCENT.decimal) {
                    CircularProgressIndicator(
                        progress = { walletDisplayValues.progress.decimal },
                        modifier =
                            Modifier
                                .matchParentSize()
                                .padding(progressCirclePadding)
                                .testTag(HomeTag.PROGRESS),
                        color = Color.Gray,
                        strokeWidth = progressCircleStroke,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
            // texts
            Column(
                modifier =
                    Modifier
                        .padding(contentPadding)
                        .wrapContentSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(WalletTheme.dimens.spacingLarge))

                if (walletDisplayValues.balanceAmountText.isNotEmpty()) {
                    HeaderWithDrkIcon(amount = walletDisplayValues.balanceAmountText)
                }

                if (isFiatConversionEnabled) {
                    Column(Modifier.testTag(HomeTag.FIAT_CONVERSION)) {
                        Spacer(modifier = Modifier.height(WalletTheme.dimens.spacingSmall))

                        when (walletDisplayValues.fiatCurrencyAmountState) {
                            is HomeFiatConversionRateState.Current -> {
                                BodyWithFiatCurrencySymbol(
                                    amount = walletDisplayValues.fiatCurrencyAmountText
                                )
                            }

                            is HomeFiatConversionRateState.Stale -> {
                                // Note: we should show information about staleness too
                                BodyWithFiatCurrencySymbol(
                                    amount = walletDisplayValues.fiatCurrencyAmountText
                                )
                            }

                            HomeFiatConversionRateState.Unavailable -> {
                                Body(text = walletDisplayValues.fiatCurrencyAmountText)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(WalletTheme.dimens.spacingLarge))

                if (walletDisplayValues.statusText.isNotEmpty()) {
                    Body(
                        text = walletDisplayValues.statusText,
                        modifier = Modifier.testTag(HomeTag.SINGLE_LINE_TEXT)
                    )
                }

                // Block sync progress — uses lightwallet sync status
                val syncMsg = walletSnapshot.syncStatusMessage
                if (syncMsg.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = syncMsg,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.testTag("SYNC_STATUS_TEXT"),
                    )
                } else {
                    // Fallback: legacy block display
                    val info = walletSnapshot.processorInfo
                    if (info.chainTip > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val blockText =
                            if (walletSnapshot.status == DarkfiSyncStatus.SYNCING) {
                                "Block ${info.scannedBlocks} / ${info.chainTip}"
                            } else {
                                "Synced · Block ${info.chainTip}"
                            }
                        Text(
                            text = blockText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                }

                // Sync method / sync type label — show the most specific info available.
                // When the Rust SyncMethod enum is known (PerfOMR, LWEmongrass, etc.),
                // prefer that over the free-text syncType label to avoid redundant
                // stacking like "🔒 OMR" + "🔒 PerfOMR".
                val syncMethod = walletSnapshot.syncMethod
                val syncTypeMsg = walletSnapshot.syncTypeMessage

                if (syncMethod != DarkfiSyncMethod.UNKNOWN) {
                    Spacer(modifier = Modifier.height(2.dp))
                    val phaseHint =
                        when {
                            walletSnapshot.syncStatusMessage.contains("1/2") -> " · scanning"
                            walletSnapshot.syncStatusMessage.contains("2/2") -> " · fetching"
                            else -> ""
                        }
                    Text(
                        text =
                            if (syncMethod.isPrivateRetrieval) {
                                "🔒 ${syncMethod.displayLabel}$phaseHint"
                            } else {
                                syncMethod.displayLabel
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (syncMethod.isPrivateRetrieval) {
                                Color(0xFF4CAF50) // Green — private oblivious retrieval
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            },
                        modifier = Modifier.testTag("SYNC_METHOD_TEXT"),
                    )
                } else if (syncTypeMsg.isNotEmpty() && syncTypeMsg != "Idle") {
                    Spacer(modifier = Modifier.height(2.dp))
                    val syncTypeColor =
                        when {
                            syncTypeMsg.startsWith("OMR") -> {
                                Color(0xFF4CAF50)
                            }

                            syncTypeMsg.contains("fallback", ignoreCase = true) -> {
                                Color(0xFFFF9800)
                            }

                            // Amber — degraded to fallback
                            else -> {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            }
                        }
                    Text(
                        text =
                            if (syncTypeMsg.contains("fallback", ignoreCase = true)) {
                                "⚠️ $syncTypeMsg"
                            } else {
                                syncTypeMsg
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color = syncTypeColor,
                        modifier = Modifier.testTag("SYNC_TYPE_TEXT"),
                    )
                }
            }
        }
    }
}
