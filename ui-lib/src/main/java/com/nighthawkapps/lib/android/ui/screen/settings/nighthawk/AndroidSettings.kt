package com.nighthawkapps.lib.android.ui.screen.settings.nighthawk

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatIdentity
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.AlertDialog
import com.nighthawkapps.lib.android.ui.common.VersionInfo
import com.nighthawkapps.lib.android.ui.common.setPlainText
import com.nighthawkapps.lib.android.ui.common.showMessage
import com.nighthawkapps.lib.android.ui.design.component.BodyMedium
import com.nighthawkapps.lib.android.ui.design.component.PrimaryButton
import com.nighthawkapps.lib.android.ui.design.component.TitleMedium
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.screen.chat.LocalDarkfiChatController
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel
import com.nighthawkapps.lib.android.ui.screen.settings.nighthawk.model.NighthawkSettingsNavigationCallbacks
import com.nighthawkapps.lib.android.ui.screen.settings.nighthawk.model.ReScanType
import com.nighthawkapps.lib.android.ui.screen.settings.nighthawk.view.SettingsView
import kotlinx.coroutines.launch

@Composable
internal fun MainActivity.AndroidSettings(navigation: NighthawkSettingsNavigationCallbacks) {
    WrapSettings(activity = this, navigation = navigation)
}

@Composable
internal fun WrapSettings(
    activity: ComponentActivity,
    navigation: NighthawkSettingsNavigationCallbacks,
) {
    val walletViewModel by activity.viewModels<WalletViewModel>()

    val onReScan: (ReScanType) -> Unit = {
        when (it) {
            ReScanType.FULL_SCAN -> {
                walletViewModel.rescanBlockchain()
                activity.showMessage(activity.getString(R.string.dialog_rescan_initiated_title))
            }

            ReScanType.WIPE -> {
                walletViewModel.resetSdk()
                activity.showMessage(activity.getString(R.string.rescan_wallet_wipe_success))
            }
        }
    }

    SettingsView(
        versionInfo = VersionInfo.new(activity),
        navigation = navigation,
        onRescan = onReScan,
    )
}
