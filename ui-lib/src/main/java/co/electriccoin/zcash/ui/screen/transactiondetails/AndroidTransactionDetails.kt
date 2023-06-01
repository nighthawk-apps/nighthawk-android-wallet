package co.electriccoin.zcash.ui.screen.transactiondetails

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.screen.home.viewmodel.HomeViewModel
import co.electriccoin.zcash.ui.screen.home.viewmodel.WalletViewModel
import co.electriccoin.zcash.ui.screen.transactiondetails.view.TransactionDetails

@Composable
internal fun MainActivity.AndroidTransactionDetails(transactionId: Long, onBack: () -> Unit) {
    WrapAndroidTransactionDetails(activity = this, transactionId = transactionId, onBack = onBack)
}

@Composable
internal fun WrapAndroidTransactionDetails(activity: ComponentActivity, transactionId: Long, onBack: () -> Unit) {
    Twig.info { "TransactionId $transactionId" }
    val homeViewModel by activity.viewModels<HomeViewModel>()
    val walletViewModel by activity.viewModels<WalletViewModel>()
    DisposableEffect(key1 = Unit) {
        homeViewModel.onBottomNavBarVisibilityChanged(show = false)
        onDispose {
            homeViewModel.onBottomNavBarVisibilityChanged(show = true)
        }
    }

    val transactionOverview = walletViewModel.transactionSnapshot.collectAsStateWithLifecycle().value.firstOrNull {
        it.id == transactionId
    }
    Twig.info { "TransactionOverview: $transactionOverview" }

    TransactionDetails(transactionOverview = transactionOverview, onBack = onBack)

}
