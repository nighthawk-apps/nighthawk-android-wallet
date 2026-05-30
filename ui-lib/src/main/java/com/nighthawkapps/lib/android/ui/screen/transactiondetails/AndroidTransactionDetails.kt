package com.nighthawkapps.lib.android.ui.screen.transactiondetails

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nighthawkapps.lib.android.spackle.Twig
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.common.onLaunchUrl
import com.nighthawkapps.lib.android.ui.common.toBalanceUiModel
import com.nighthawkapps.lib.android.ui.common.toBalanceValueModel
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.HomeViewModel
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel
import com.nighthawkapps.lib.android.ui.screen.transactiondetails.view.TransactionDetails
import com.nighthawkapps.lib.android.ui.screen.transactiondetails.viewmodel.TransactionViewModel
import com.nighthawkapps.lib.android.ui.screen.wallet.model.BalanceUIModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun MainActivity.AndroidTransactionDetails(
    transactionId: String,
    onBack: () -> Unit
) {
    WrapAndroidTransactionDetails(activity = this, transactionId = transactionId, onBack = onBack)
}

@Composable
internal fun WrapAndroidTransactionDetails(
    activity: ComponentActivity,
    transactionId: String,
    onBack: () -> Unit
) {
    Twig.debug { "TransactionId $transactionId" }
    val walletViewModel by activity.viewModels<WalletViewModel>()
    val homeViewModel by activity.viewModels<HomeViewModel>()
    val transactionUiViewModel = viewModel<TransactionViewModel>()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val synchronizerJob: MutableState<Job?> =
        remember {
            mutableStateOf(null)
        }

    LaunchedEffect(key1 = Unit) {
        synchronizerJob.value =
            scope.launch(Dispatchers.IO) {
                val synchronizer = walletViewModel.synchronizer.filterNotNull().first()
                transactionUiViewModel.getTransactionUiModel(transactionId, synchronizer)
            }
    }

    val isNavigateAwayFromAppWarningShown =
        transactionUiViewModel.isNavigateAwayFromWarningShown.collectAsStateWithLifecycle().value
    val transactionDetailsUIModel =
        transactionUiViewModel.transactionDetailsUIModel.collectAsStateWithLifecycle().value
    val fiatCurrencyUiState by homeViewModel.fiatCurrencyUiStateFlow.collectAsStateWithLifecycle()
    val isFiatCurrencyPreferred by homeViewModel.isFiatCurrencyPreferredOverNative.collectAsStateWithLifecycle()
    val balanceUIModel =
        remember(transactionDetailsUIModel) {
            derivedStateOf {
                transactionDetailsUIModel?.transactionOverview?.let {
                    val netAtomic = (it.netValueAtomic - it.totalFeeAtomic).coerceAtLeast(0L)
                    netAtomic
                        .toBalanceValueModel(
                            fiatCurrencyUiState,
                            isFiatCurrencyPreferred,
                        ).toBalanceUiModel(context)
                } ?: BalanceUIModel()
            }
        }

    Twig.debug { "TransactionDetailUiModel: $transactionDetailsUIModel" }

    TransactionDetails(
        transactionDetailsUIModel = transactionDetailsUIModel,
        balanceUIModel = balanceUIModel.value,
        isNavigateAwayFromAppWarningShown = isNavigateAwayFromAppWarningShown,
        onBack = onBack,
        viewOnBlockExplorer = { url, updateWarningStatus ->
            if (updateWarningStatus) {
                transactionUiViewModel.updateNavigateAwayFromWaringFlag(true)
            }
            activity.onLaunchUrl(url)
        }
    )
}
