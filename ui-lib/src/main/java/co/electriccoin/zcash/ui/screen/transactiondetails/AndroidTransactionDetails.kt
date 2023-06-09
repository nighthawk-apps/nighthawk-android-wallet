package co.electriccoin.zcash.ui.screen.transactiondetails

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.screen.home.viewmodel.HomeViewModel
import co.electriccoin.zcash.ui.screen.home.viewmodel.WalletViewModel
import co.electriccoin.zcash.ui.screen.transactiondetails.view.TransactionDetails
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun MainActivity.AndroidTransactionDetails(transactionId: Long, onBack: () -> Unit) {
    WrapAndroidTransactionDetails(activity = this, transactionId = transactionId, onBack = onBack)
}

@Composable
internal fun WrapAndroidTransactionDetails(activity: ComponentActivity, transactionId: Long, onBack: () -> Unit) {
    Twig.info { "TransactionId $transactionId" }
    val homeViewModel by activity.viewModels<HomeViewModel>()
    val walletViewModel by activity.viewModels<WalletViewModel>()
    val scope = rememberCoroutineScope()
    DisposableEffect(key1 = Unit) {
        val previousVisibility = homeViewModel.isBottomNavBarVisible.value
        // for handling bottomNavBar visibility due to onDispose of this screen parent class
        scope.launch {
            delay(500.milliseconds)
            homeViewModel.onBottomNavBarVisibilityChanged(show = false)
        }
        onDispose {
            homeViewModel.onBottomNavBarVisibilityChanged(show = previousVisibility)
        }
    }

    val transactionDetailsUIModel = walletViewModel.transactionUiModel(transactionId).collectAsStateWithLifecycle().value
    Twig.info { "TransactionDetailUiModel: $transactionDetailsUIModel" }

    TransactionDetails(transactionDetailsUIModel = transactionDetailsUIModel, onBack = onBack)

}
