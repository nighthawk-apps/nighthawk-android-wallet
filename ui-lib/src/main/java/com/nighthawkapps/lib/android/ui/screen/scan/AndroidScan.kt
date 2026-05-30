package com.nighthawkapps.lib.android.ui.screen.scan

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.global.DeepLinkUtil
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiAddressType
import com.nighthawkapps.lib.android.sdk.wallet.validateAddressStub
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel
import com.nighthawkapps.lib.android.ui.screen.scan.util.SettingsUtil
import com.nighthawkapps.lib.android.ui.screen.scan.view.Scan
import kotlinx.coroutines.launch

@Composable
internal fun MainActivity.WrapScanValidator(
    onScanValid: (address: String) -> Unit,
    goBack: () -> Unit
) {
    WrapScan(
        this,
        onScanValid = onScanValid,
        goBack = goBack
    )
}

@Composable
fun WrapScan(
    activity: ComponentActivity,
    onScanValid: (address: String) -> Unit,
    goBack: () -> Unit
) {
    val walletViewModel by activity.viewModels<WalletViewModel>()

    val synchronizer = walletViewModel.synchronizer.collectAsStateWithLifecycle().value

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    if (synchronizer == null) {
        // Display loading indicator
    } else {
        Scan(
            snackbarHostState = snackbarHostState,
            onBack = goBack,
            onScanned = { result ->
                scope.launch {
                    val sendDeepLinkData = DeepLinkUtil.getSendDeepLinkData(result.toUri())
                    val address = sendDeepLinkData?.address ?: result
                    val isAddressValid = synchronizer.validateAddressStub(address) != DarkfiAddressType.Invalid
                    if (isAddressValid) {
                        onScanValid(address)
                    } else {
                        snackbarHostState.showSnackbar(
                            message = activity.getString(R.string.scan_validation_invalid_address)
                        )
                    }
                }
            },
            onOpenSettings = {
                runCatching {
                    activity.startActivity(SettingsUtil.newSettingsIntent(activity.packageName))
                }.onFailure {
                    // This case should not really happen, as the Settings app should be available on every
                    // Android device, but we need to handle it somehow.
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = activity.getString(R.string.scan_settings_open_failed)
                        )
                    }
                }
            },
            onScanStateChanged = {}
        )
    }
}
