package com.nighthawkapps.lib.android.ui.screen.settingbackupwallet

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.SecureScreen
import com.nighthawkapps.lib.android.ui.common.authenticate
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.SecretState
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel
import com.nighthawkapps.lib.android.ui.screen.onboarding.nighthawk.view.SeedBackup
import com.nighthawkapps.lib.android.ui.screen.pin.view.AuthenticatePin
import com.nighthawkapps.lib.android.ui.screen.pin.viewmodel.PinViewModel

@Composable
internal fun MainActivity.AndroidSettingBackUpWallet(onBack: () -> Unit) {
    WrapAndroidSettingBackUpWallet(activity = this, onBack = onBack)
}

@Composable
internal fun WrapAndroidSettingBackUpWallet(
    activity: ComponentActivity,
    onBack: () -> Unit
) {
    SecureScreen()
    val walletViewModel by activity.viewModels<WalletViewModel>()
    val pinViewModel by activity.viewModels<PinViewModel>()
    val secretState = walletViewModel.secretState.collectAsStateWithLifecycle().value
    val isPinConfigured = pinViewModel.isPinConfigured.collectAsStateWithLifecycle().value
    val isTouchIdOrFaceIdEnabled = pinViewModel.isTouchIdOrFaceIdEnabled.collectAsStateWithLifecycle().value

    var isAuthenticated by rememberSaveable { mutableStateOf(false) }

    if (isPinConfigured == true && !isAuthenticated) {
        AuthenticatePin(
            verifyPin = pinViewModel::verifyPin,
            onBack = onBack,
            onAuthentication = { success ->
                if (success) {
                    isAuthenticated = true
                }
            },
            pinPromptResId = R.string.enter_six_digit_pin_code,
        )

        LaunchedEffect(isTouchIdOrFaceIdEnabled) {
            if (isTouchIdOrFaceIdEnabled == true && pinViewModel.isBioMetricEnabledOnMobile()) {
                (activity as? FragmentActivity)?.authenticate(
                    "",
                    activity.getString(R.string.biometric_backup_phrase_title),
                ) {
                    isAuthenticated = true
                }
            }
        }
        return
    }

    // SecretState.NeedBackUp and SecretState.Ready only contains the persistWallet
    if (secretState is SecretState.Ready) {
        SeedBackup(persistableWallet = secretState.persistableWallet, navigationFromSettings = true, onBack = onBack)
    }
}
