package co.electriccoin.zcash.ui.screen.wallet.view

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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import cash.z.ecc.android.sdk.Synchronizer
import cash.z.ecc.android.sdk.ext.convertZecToZatoshi
import cash.z.ecc.android.sdk.model.TransactionOverview
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.common.DisableScreenTimeout
import co.electriccoin.zcash.ui.common.MIN_ZEC_FOR_SHIELDING
import co.electriccoin.zcash.ui.common.toFormattedString
import co.electriccoin.zcash.ui.design.component.BalanceText
import co.electriccoin.zcash.ui.design.component.BodyMedium
import co.electriccoin.zcash.ui.design.component.BodySmall
import co.electriccoin.zcash.ui.design.component.PrimaryButton
import co.electriccoin.zcash.ui.design.component.TitleLarge
import co.electriccoin.zcash.ui.design.component.TitleMedium
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.fixture.WalletSnapshotFixture
import co.electriccoin.zcash.ui.screen.fiatcurrency.model.FiatCurrency
import co.electriccoin.zcash.ui.screen.fiatcurrency.model.FiatCurrencyUiState
import co.electriccoin.zcash.ui.screen.home.model.WalletDisplayValues
import co.electriccoin.zcash.ui.screen.home.model.WalletSnapshot
import co.electriccoin.zcash.ui.screen.transactionhistory.view.TransactionOverviewHistoryRow
import co.electriccoin.zcash.ui.screen.wallet.model.BalanceDisplayValues
import co.electriccoin.zcash.ui.screen.wallet.model.BalanceUIModel
import co.electriccoin.zcash.ui.screen.wallet.model.BalanceViewType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Preview
@Composable
fun WalletPreview() {
    ZcashTheme(darkTheme = false) {
        Surface {
            WalletView(
                walletSnapshot = WalletSnapshotFixture.new(),
                transactionSnapshot = persistentListOf(),
                isKeepScreenOnWhileSyncing = true,
                isFiatCurrencyPreferred = true,
                isBandit = true,
                fiatCurrencyUiState = FiatCurrencyUiState(FiatCurrency.USD, 25.8),
                onShieldNow = {},
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
    ZcashTheme(darkTheme = false) {
        Surface {
            BalanceView(
                balanceDisplayValues = BalanceDisplayValues(
                    R.drawable.ic_icon_total,
                    "Total Balance",
                    BalanceUIModel(
                        "124.25",
                        "ZEC",
                        "125",
                        "USD"
                    ),
                    "expecting (+1 ZEC)"
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
    transactionSnapshot: ImmutableList<TransactionOverview>,
    isKeepScreenOnWhileSyncing: Boolean?,
    isFiatCurrencyPreferred: Boolean,
    fiatCurrencyUiState: FiatCurrencyUiState,
    isBandit: Boolean,
    onShieldNow: () -> Unit,
    onAddressQrCodes: () -> Unit,
    onTransactionDetail: (String) -> Unit,
    onViewTransactionHistory: () -> Unit,
    onLongItemClick: (TransactionOverview) -> Unit,
    onFlipCurrency: (isFiatCurrencyPreferredOverZec: Boolean) -> Unit,
    onScanToSend: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                start = dimensionResource(id = R.dimen.screen_standard_margin),
                end = dimensionResource(id = R.dimen.screen_standard_margin),
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
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.top_margin_back_btn)))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_icon_scan_qr),
                contentDescription = "logo", contentScale = ContentScale.Inside,
                modifier = Modifier.clickable { onAddressQrCodes() }
            )
            Image(
                painter = painterResource(id = R.drawable.ic_qr_scan),
                contentDescription = "logo", contentScale = ContentScale.Inside,
                modifier = Modifier.clickable { onScanToSend() }
            )
        }
        Image(
            imageVector = ImageVector.vectorResource(id = R.drawable.ic_nighthawk_logo),
            contentDescription = "logo", contentScale = ContentScale.Inside,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .then(
                    if (isBandit) {
                        Modifier
                            .graphicsLayer {
                                rotationZ = rotationAngle
                            }
                            .clickable { rotationTarget += 90 }
                    } else Modifier
                )
        )
        Spacer(Modifier.height(dimensionResource(id = R.dimen.pageMargin)))
        TitleLarge(
            text = stringResource(id = R.string.ns_nighthawk),
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(dimensionResource(id = R.dimen.home_view_pager_min_height)))

        if (walletSnapshot.status == Synchronizer.Status.SYNCED) {
            // show synced status with viewpager
            val pageCount = BalanceViewType.TOTAL_VIEWS
            val state = rememberPagerState(initialPage = 0) { pageCount }
            HorizontalPager(state = state) { pageNo ->
                val balanceDisplayValues = BalanceDisplayValues.getNextValue(
                    LocalContext.current,
                    BalanceViewType.getBalanceViewType(pageNo),
                    walletSnapshot,
                    isFiatCurrencyPreferred,
                    fiatCurrencyUiState
                )
                BalanceView(
                    balanceDisplayValues = balanceDisplayValues,
                    showFlipCurrencyIcon = fiatCurrencyUiState.fiatCurrency != FiatCurrency.OFF,
                    onFlipCurrency = { onFlipCurrency(isFiatCurrencyPreferred.not()) })
            }
            val balanceViewType = BalanceViewType.getBalanceViewType(state.currentPage)
            if (balanceViewType != BalanceViewType.SWIPE) {
                PageIndicator(pageCount = pageCount, pagerState = state)
            }
            isBalancePrivateMode.value = balanceViewType == BalanceViewType.SWIPE
            // Show shield now button in last if balanceViewType is Transparent and some transparentBalance is available 0.01 ZEC
            if (balanceViewType == BalanceViewType.TRANSPARENT && walletSnapshot.transparentBalance > MIN_ZEC_FOR_SHIELDING.convertZecToZatoshi()) {
                Spacer(Modifier.height(dimensionResource(id = R.dimen.pageMargin)))
                PrimaryButton(
                    onClick = onShieldNow,
                    text = stringResource(id = R.string.ns_shield_now).uppercase(),
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .sizeIn(
                            minWidth = dimensionResource(id = R.dimen.button_min_width),
                            minHeight = dimensionResource(id = R.dimen.button_height)
                        )
                )
            }
        } else {
            val walletDisplayValues = WalletDisplayValues.getNextValues(
                LocalContext.current,
                walletSnapshot,
                false
            )
            Image(
                painter = painterResource(id = walletDisplayValues.statusIconDrawable),
                contentDescription = "logo", contentScale = ContentScale.Inside,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(20.dp))
            BodySmall(
                text = walletDisplayValues.statusText,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        // Bottom Transactions View
        if (transactionSnapshot.isNotEmpty()) {
            Spacer(modifier = Modifier.weight(1f))
            BodyMedium(
                text = stringResource(id = R.string.ns_recent_activity),
                color = colorResource(id = co.electriccoin.zcash.ui.design.R.color.ns_parmaviolet)
            )
            Spacer(modifier = Modifier.height(4.dp))
            transactionSnapshot.take(2).toImmutableList().forEach { transactionOverview ->
                TransactionOverviewHistoryRow(
                    transactionOverview = transactionOverview,
                    fiatCurrencyUiState = fiatCurrencyUiState,
                    isBalancePrivateMode = isBalancePrivateMode.value,
                    isFiatCurrencyPreferred = isFiatCurrencyPreferred,
                    onItemClick = { onTransactionDetail(it.rawId.byteArray.toFormattedString()) },
                    onItemLongClick = onLongItemClick
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
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
fun PageIndicator(pageCount: Int, pagerState: PagerState) {
    Row(
        Modifier
            .height(20.dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(pageCount) { iteration ->
            val color =
                if (pagerState.currentPage == iteration) ZcashTheme.colors.selectedPageIndicator else MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
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
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .heightIn(dimensionResource(id = R.dimen.home_view_pager_min_height)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = balanceDisplayValues.iconDrawableRes),
                contentDescription = "logo", contentScale = ContentScale.Inside,
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
            if (balanceDisplayValues.msg.isNullOrBlank().not()) {
                BodySmall(
                    text = balanceDisplayValues.msg
                        ?: "",
                    textAlign = TextAlign.Center,
                    color = colorResource(id = co.electriccoin.zcash.ui.design.R.color.ns_parmaviolet)
                )
            }
            if (balanceDisplayValues.balanceType.isNotBlank()) {
                BodySmall(
                    text = balanceDisplayValues.balanceType,
                    textAlign = TextAlign.Center,
                    color = colorResource(id = co.electriccoin.zcash.ui.design.R.color.ns_parmaviolet)
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
                contentDescription = "Fiat balance",
                modifier = Modifier.clickable {
                    onFlipCurrency.invoke()
                },
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

fun isSyncing(status: Synchronizer.Status): Boolean {
    return status == Synchronizer.Status.SYNCING
}
