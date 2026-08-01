package com.nighthawkapps.lib.android.ui.screen.pin

import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.SecureScreen
import com.nighthawkapps.lib.android.ui.common.authenticate
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel
import com.nighthawkapps.lib.android.ui.screen.pin.view.AuthenticatePin
import com.nighthawkapps.lib.android.ui.screen.pin.view.SetUpPin
import com.nighthawkapps.lib.android.ui.screen.pin.viewmodel.PinViewModel
import com.nighthawkapps.lib.android.ui.screen.security.viewmodel.SecurityViewModel

@Composable
internal fun MainActivity.AndroidPin(
    isPinSetup: Boolean = false,
    verifyPinToDisable: Boolean = false,
    onBack: () -> Unit,
) {
    WrapAndroidPin(
        activity = this,
        isPinSetup = isPinSetup,
        verifyPinToDisable = verifyPinToDisable,
        onBack = onBack,
    )
}

@Composable
internal fun WrapAndroidPin(
    activity: MainActivity,
    isPinSetup: Boolean,
    verifyPinToDisable: Boolean,
    onBack: () -> Unit,
) {
    val pinViewModel by activity.viewModels<PinViewModel>()
    val walletViewModel by activity.viewModels<WalletViewModel>()
    val securityViewModel by activity.viewModels<SecurityViewModel>()

    SecureScreen()

    BackHandler(true) {
        // Stop acting on back press
    }

    if (isPinSetup) {
        val onPinSelected: (String) -> Unit = {
            pinViewModel.savePin(it)
            onBack()
        }
        SetUpPin(onBack = onBack, onPinSelected = onPinSelected)
    } else {
        val pinConfigured = pinViewModel.isPinConfigured.collectAsStateWithLifecycle().value
        val isTouchIdOrFaceIdEnabled = pinViewModel.isTouchIdOrFaceIdEnabled.collectAsStateWithLifecycle().value
        if (pinConfigured == null || isTouchIdOrFaceIdEnabled == null) {
            // we can show Loader if it take time, this value should not be null in real case
        } else {
            AuthenticatePin(
                verifyPin = pinViewModel::verifyPin,
                onBack = onBack,
                pinPromptResId =
                    if (verifyPinToDisable) {
                        R.string.ns_enter_pin_to_disable
                    } else {
                        R.string.enter_six_digit_pin_code
                    },
                onAuthentication = { authenticated ->
                    if (!authenticated) {
                        return@AuthenticatePin
                    }
                    if (verifyPinToDisable) {
                        securityViewModel.disablePin()
                        onBack()
                    } else {
                        walletViewModel.updateAuthenticationState(true)
                    }
                },
            )

            if (!verifyPinToDisable) {
                LaunchedEffect(key1 = Unit) {
                    if (isTouchIdOrFaceIdEnabled && pinViewModel.isBioMetricEnabledOnMobile()) {
                        activity.authenticate("", activity.getString(R.string.biometric_backup_phrase_title)) {
                            walletViewModel.updateAuthenticationState(true)
                        }
                    }
                }
            }
        }
    }
}
