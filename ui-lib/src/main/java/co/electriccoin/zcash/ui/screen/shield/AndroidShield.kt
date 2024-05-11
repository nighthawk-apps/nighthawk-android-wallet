package co.electriccoin.zcash.ui.screen.shield

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cash.z.ecc.android.sdk.SdkSynchronizer
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.common.DEFAULT_SHIELDING_THRESHOLD
import co.electriccoin.zcash.ui.common.onLaunchUrl
import co.electriccoin.zcash.ui.screen.home.viewmodel.WalletViewModel
import co.electriccoin.zcash.ui.screen.send.nighthawk.model.SubmitResult
import co.electriccoin.zcash.ui.screen.send.nighthawk.viewmodel.CreateTransactionsViewModel
import co.electriccoin.zcash.ui.screen.shield.model.ShieldUIState
import co.electriccoin.zcash.ui.screen.shield.model.ShieldUiDestination
import co.electriccoin.zcash.ui.screen.shield.model.ShieldingProcessState
import co.electriccoin.zcash.ui.screen.shield.view.AutoShieldingInfo
import co.electriccoin.zcash.ui.screen.shield.view.ShieldFunds
import co.electriccoin.zcash.ui.screen.shield.viewmodel.ShieldViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
internal fun MainActivity.AndroidShield(onBack: () -> Unit) {
    WrapShield(activity = this, onBack = onBack)
}

@Composable
internal fun WrapShield(activity: ComponentActivity, onBack: () -> Unit) {
    val shieldViewModel = viewModel<ShieldViewModel>()
    val createTransactionsViewModel = viewModel<CreateTransactionsViewModel>()
    val walletViewModel by activity.viewModels<WalletViewModel>()
    val scope = rememberCoroutineScope()

    BackHandler(enabled = shieldViewModel.getCurrentDestination() == ShieldUiDestination.ShieldFunds) {
        // Prevent back button click
    }

    DisposableEffect(key1 = Unit) {
        shieldViewModel.checkAutoShieldUiState()
        onDispose {
            shieldViewModel.clearData()
        }
    }

    when (val shieldUIState = shieldViewModel.shieldUIState.collectAsStateWithLifecycle().value) {
        ShieldUIState.Loading -> {
            // We can add loading state
        }

        is ShieldUIState.OnResult -> {
            when (val destination = shieldUIState.destination) {
                is ShieldUiDestination.AutoShieldError -> {
                    // May be log or may be show error and navigate back
                    Twig.debug { "Error is ${destination.message}" }
                    Toast.makeText(activity, destination.message ?: "Error in autoShielding", Toast.LENGTH_SHORT).show()
                    onBack()
                }

                ShieldUiDestination.AutoShieldingInfo -> {
                    shieldViewModel.acknowledgeShieldingInfo()
                    AutoShieldingInfo(
                        onNext = { shieldViewModel.updateShieldUiState(ShieldUIState.OnResult(ShieldUiDestination.ShieldFunds)) },
                        onLaunchUrl = { activity.onLaunchUrl(it) }
                    )
                }

                ShieldUiDestination.ShieldFunds -> {
                    val shieldingProcessState = shieldViewModel.shieldingProcessState.collectAsStateWithLifecycle().value
                    val spendingKey = walletViewModel.spendingKey.collectAsStateWithLifecycle().value
                    val synchronizer = walletViewModel.synchronizer.collectAsStateWithLifecycle().value
                    LaunchedEffect(key1 = synchronizer, key2 = spendingKey) {
                        if (synchronizer != null && spendingKey != null) {
                            scope.launch(Dispatchers.IO) {
                                runCatching {
                                    Twig.debug { "AutoShield onStarted" }
                                    synchronizer.proposeShielding(
                                        spendingKey.account,
                                        Zatoshi(DEFAULT_SHIELDING_THRESHOLD)
                                    )
                                }
                                    .onSuccess { newProposal ->
                                        Twig.info { "Shielding proposal result: ${newProposal?.toPrettyString()}" }

                                        if (newProposal == null) {
                                            shieldViewModel.updateShieldingProcessState(ShieldingProcessState.FAILURE)
                                        } else {
                                            val result =
                                                createTransactionsViewModel.runCreateTransactions(
                                                    synchronizer = synchronizer,
                                                    spendingKey = spendingKey,
                                                    proposal = newProposal
                                                )
                                            when (result) {
                                                SubmitResult.Success -> {
                                                    Twig.info { "Shielding transaction done successfully" }
                                                    shieldViewModel.updateShieldingProcessState(ShieldingProcessState.SUCCESS)
                                                    // Triggering transaction history refresh to be notified about the newly created
                                                    // transaction asap
                                                    (synchronizer as SdkSynchronizer).refreshTransactions()

                                                    // We could consider notifying UI with a change to emphasize the shielding action
                                                    // was successful, or we could switch the selected tab to Account
                                                }
                                                is SubmitResult.SimpleTrxFailure -> {
                                                    Twig.warn { "Shielding transaction failed ${result.errorDescription}" }
                                                    shieldViewModel.updateShieldingProcessState(ShieldingProcessState.FAILURE)
                                                }
                                                is SubmitResult.MultipleTrxFailure -> {
                                                    Twig.warn { "Shielding failed with multi-transactions-submission-error handling" }
                                                    shieldViewModel.updateShieldingProcessState(ShieldingProcessState.FAILURE)
                                                }
                                            }
                                        }
                                    }
                                    .onFailure {
                                        Twig.debug { "AutoShield onFailure $it" }
                                        shieldViewModel.updateShieldingProcessState(ShieldingProcessState.FAILURE)
                                    }
                            }
                        }
                    }
                    shieldViewModel.updateLastAutoShieldTime()
                    ShieldFunds(
                        onBack = onBack,
                        shieldingProcessState = shieldingProcessState
                    )
                }
            }
        }
    }
}
