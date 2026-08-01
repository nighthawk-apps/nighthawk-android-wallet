@file:Suppress("LongMethod", "LongParameterList")

package com.nighthawkapps.lib.android.ui.screen.wallet.view

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.sdk.daemon.DarkfiDaemonStatus
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSyncStatus
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTransactionOverview
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.DisableScreenTimeout
import com.nighthawkapps.lib.android.ui.common.NighthawkBrandingHeader
import com.nighthawkapps.lib.android.ui.daemon.DarkfiDaemonStatusIndicator
import com.nighthawkapps.lib.android.ui.design.component.BalanceText
import com.nighthawkapps.lib.android.ui.design.component.BodyMedium
import com.nighthawkapps.lib.android.ui.design.component.BodySmall
import com.nighthawkapps.lib.android.ui.design.component.TitleMedium
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.fixture.WalletSnapshotFixture
import com.nighthawkapps.lib.android.ui.screen.fiatcurrency.model.FiatCurrency
import com.nighthawkapps.lib.android.ui.screen.fiatcurrency.model.FiatCurrencyUiState
import com.nighthawkapps.lib.android.ui.screen.home.model.WalletDisplayValues
import com.nighthawkapps.lib.android.ui.screen.home.model.WalletSnapshot
import com.nighthawkapps.lib.android.ui.screen.transactionhistory.view.TransactionOverviewHistoryRow
import com.nighthawkapps.lib.android.ui.screen.wallet.model.BalanceDisplayValues
import com.nighthawkapps.lib.android.ui.screen.wallet.model.BalanceUIModel
import com.nighthawkapps.lib.android.ui.screen.wallet.model.BalanceViewType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Preview
@Composable
fun WalletPreview() {
    WalletTheme(darkTheme = false) {
        Surface {
            WalletView(
                walletSnapshot = WalletSnapshotFixture.new(),
                transactionSnapshot = persistentListOf(),
                isKeepScreenOnWhileSyncing = true,
                isFiatCurrencyPreferred = true,
                isBandit = true,
                fiatCurrencyUiState = FiatCurrencyUiState(FiatCurrency.USD, 25.8),
                onAddressQrCodes = {},
                onTransactionDetail = {},
                onViewTransactionHistory = {},
                onLongItemClick = {},
                onFlipCurrency = {},
                onScanToSend = {}
            )
        }
    }
}

@Preview(device = Devices.NEXUS_6)
@Composable
fun BalanceViewPreview() {
    WalletTheme(darkTheme = false) {
        Surface {
            BalanceView(
                balanceDisplayValues =
                    BalanceDisplayValues(
                        R.drawable.ic_icon_total,
                        "Total Balance",
                        BalanceUIModel(
                            "124.25",
                            "DRK",
                            "125",
                            "USD"
                        ),
                        "expecting (+1 DRK)"
                    ),
                showFlipCurrencyIcon = true,
                onFlipCurrency = {}
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WalletView(
    walletSnapshot: WalletSnapshot,
    transactionSnapshot: ImmutableList<DarkfiTransactionOverview>,
    isKeepScreenOnWhileSyncing: Boolean?,
    isFiatCurrencyPreferred: Boolean,
    fiatCurrencyUiState: FiatCurrencyUiState,
    isBandit: Boolean,
    onAddressQrCodes: () -> Unit,
    onTransactionDetail: (String) -> Unit,
    onViewTransactionHistory: () -> Unit,
    onLongItemClick: (DarkfiTransactionOverview) -> Unit,
    onFlipCurrency: (isFiatCurrencyPreferredOverNative: Boolean) -> Unit,
    onScanToSend: () -> Unit,
    daemonStatus: DarkfiDaemonStatus = DarkfiDaemonStatus.Unknown,
    onDaemonStatusClick: () -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    start = dimensionResource(id = R.dimen.screen_standard_margin),
                    end = dimensionResource(id = R.dimen.screen_standard_margin),
                    top = dimensionResource(id = R.dimen.screen_standard_margin),
                    bottom = 10.dp
                )
    ) {
        val isBalancePrivateMode = remember { mutableStateOf(true) }
        var rotationTarget by remember {
            mutableFloatStateOf(0f)
        }
        val rotationAngle by animateFloatAsState(
            targetValue = rotationTarget,
            animationSpec = tween(durationMillis = 1500, easing = LinearEasing),
            label = "Logo rotate"
        )
        // Reorg recovery banner — shown when chain reorganization is detected
        androidx.compose.animation.AnimatedVisibility(
            visible = daemonStatus == DarkfiDaemonStatus.ReorgRecovery,
        ) {
            androidx.compose.material3.Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                colors =
                    androidx.compose.material3.CardDefaults.cardColors(
                        containerColor =
                            androidx.compose.ui.graphics
                                .Color(0xFFFFF3E0),
                        // amber 50
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Text(
                        text = stringResource(R.string.daemon_status_reorg_recovery),
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            androidx.compose.ui.graphics
                                .Color(0xFFE65100),
                        // deep orange 900
                    )
                }
            }
        }
        // Match Transfer/Settings branding Y-position; QR actions overlay the reserved top slot.
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                NighthawkBrandingHeader(
                    bottomSpacing = 16.dp,
                    logoModifier =
                        if (isBandit) {
                            Modifier
                                .graphicsLayer {
                                    rotationZ = rotationAngle
                                }.clickable { rotationTarget += 90 }
                        } else {
                            Modifier
                        },
                )
                DarkfiDaemonStatusIndicator(
                    status = daemonStatus,
                    onClick = onDaemonStatusClick,
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (walletSnapshot.status == DarkfiSyncStatus.SYNCED) {
                    BodyMedium(
                        text = stringResource(id = R.string.ns_nighthawk_news),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_icon_scan_qr),
                    contentDescription = stringResource(id = R.string.ns_logo_desc),
                    contentScale = ContentScale.Inside,
                    modifier = Modifier.clickable { onAddressQrCodes() }
                )
                Image(
                    painter = painterResource(id = R.drawable.ic_qr_scan),
                    contentDescription = stringResource(id = R.string.ns_logo_desc),
                    contentScale = ContentScale.Inside,
                    modifier = Modifier.clickable { onScanToSend() }
                )
            }
        }

        // Balance / sync status centered in remaining space (not stuck under the logo).
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (walletSnapshot.status == DarkfiSyncStatus.SYNCED) {
                val pageCount = BalanceViewType.TOTAL_VIEWS
                val state = rememberPagerState(initialPage = 0) { pageCount }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HorizontalPager(state = state) { pageNo ->
                        val balanceDisplayValues =
                            BalanceDisplayValues.getNextValue(
                                LocalContext.current,
                                BalanceViewType.getBalanceViewType(pageNo),
                                walletSnapshot,
                                isFiatCurrencyPreferred,
                                fiatCurrencyUiState
                            )
                        BalanceView(
                            balanceDisplayValues = balanceDisplayValues,
                            showFlipCurrencyIcon = fiatCurrencyUiState.fiatCurrency != FiatCurrency.OFF,
                            onFlipCurrency = { onFlipCurrency(isFiatCurrencyPreferred.not()) }
                        )
                    }
                    val balanceViewType = BalanceViewType.getBalanceViewType(state.currentPage)
                    if (balanceViewType != BalanceViewType.SWIPE) {
                        PageIndicator(pageCount = pageCount, pagerState = state)
                    }
                    isBalancePrivateMode.value = balanceViewType == BalanceViewType.SWIPE
                }
            } else {
                val walletDisplayValues =
                    WalletDisplayValues.getNextValues(
                        LocalContext.current,
                        walletSnapshot,
                        false
                    )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = walletDisplayValues.statusIconDrawable),
                        contentDescription = stringResource(id = R.string.ns_logo_desc),
                        contentScale = ContentScale.Inside,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BodySmall(
                        text = walletDisplayValues.statusText,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Bottom Transactions View
        if (transactionSnapshot.isNotEmpty()) {
            BodyMedium(
                text = stringResource(id = R.string.ns_recent_activity),
                color = colorResource(id = com.nighthawkapps.lib.android.ui.design.R.color.ns_parmaviolet)
            )
            Spacer(modifier = Modifier.height(4.dp))
            transactionSnapshot.take(2).toImmutableList().forEach { transactionOverview ->
                TransactionOverviewHistoryRow(
                    transactionOverview = transactionOverview,
                    fiatCurrencyUiState = fiatCurrencyUiState,
                    isBalancePrivateMode = isBalancePrivateMode.value,
                    isFiatCurrencyPreferred = isFiatCurrencyPreferred,
                    onItemClick = { onTransactionDetail(it.rawId) },
                    onItemLongClick = onLongItemClick
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp)
                        .clickable { onViewTransactionHistory() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TitleMedium(text = stringResource(id = R.string.ns_view_all_transactions))
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_arrow_right),
                    contentDescription = null
                )
            }
        }

        if (isKeepScreenOnWhileSyncing == true && isSyncing(walletSnapshot.status)) {
            DisableScreenTimeout()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PageIndicator(
    pageCount: Int,
    pagerState: PagerState
) {
    Row(
        Modifier
            .height(20.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(pageCount) { iteration ->
            val color =
                if (pagerState.currentPage == iteration) WalletTheme.colors.selectedPageIndicator else MaterialTheme.colorScheme.primary
            Box(
                modifier =
                    Modifier
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(color)
                        .size(16.dp)
            )
        }
    }
}

@Composable
fun BalanceView(
    balanceDisplayValues: BalanceDisplayValues,
    showFlipCurrencyIcon: Boolean,
    onFlipCurrency: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth(0.8f)
                    .heightIn(dimensionResource(id = R.dimen.home_view_pager_min_height)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = balanceDisplayValues.iconDrawableRes),
                contentDescription = stringResource(id = R.string.ns_logo_desc),
                contentScale = ContentScale.Inside,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(dimensionResource(id = R.dimen.pageMargin)))
            if (balanceDisplayValues.balanceUIModel.balance.isNotBlank()) {
                BalanceAmountRow(
                    balance = balanceDisplayValues.balanceUIModel.balance,
                    balanceUnit = balanceDisplayValues.balanceUIModel.balanceUnit,
                    showFlipCurrencyIcon = showFlipCurrencyIcon,
                    onFlipCurrency = onFlipCurrency
                )
            }
            if (balanceDisplayValues.balanceUIModel.fiatBalance.isNotBlank()) {
                BodySmall(
                    text = balanceDisplayValues.balanceUIModel.fiatBalance + " ${balanceDisplayValues.balanceUIModel.fiatUnit}",
                    textAlign = TextAlign.Center
                )
            }
            balanceDisplayValues.msg?.takeIf { it.isNotBlank() }?.let { msg ->
                BodySmall(
                    text = msg,
                    textAlign = TextAlign.Center,
                    color = colorResource(id = com.nighthawkapps.lib.android.ui.design.R.color.ns_parmaviolet)
                )
            }
            if (balanceDisplayValues.balanceType.isNotBlank()) {
                BodySmall(
                    text = balanceDisplayValues.balanceType,
                    textAlign = TextAlign.Center,
                    color = colorResource(id = com.nighthawkapps.lib.android.ui.design.R.color.ns_parmaviolet)
                )
            }
        }
    }
}

@Composable
fun BalanceAmountRow(
    balance: String,
    balanceUnit: String,
    showFlipCurrencyIcon: Boolean,
    onFlipCurrency: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier) {
        BalanceText(text = balance)
        Spacer(modifier = Modifier.width(4.dp))
        BalanceText(text = balanceUnit, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(4.dp))
        if (showFlipCurrencyIcon) {
            Icon(
                painter = painterResource(id = R.drawable.ic_icon_up_down),
                contentDescription = stringResource(id = R.string.ns_fiat_balance_desc),
                modifier =
                    Modifier.clickable {
                        onFlipCurrency.invoke()
                    },
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

fun isSyncing(status: DarkfiSyncStatus): Boolean = status == DarkfiSyncStatus.SYNCING
