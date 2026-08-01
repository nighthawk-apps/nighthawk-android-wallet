package com.nighthawkapps.lib.android.ui.screen.backup

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import com.nighthawkapps.lib.android.sdk.wallet.PersistableDarkfiWallet
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.common.setSensitivePlainText
import com.nighthawkapps.lib.android.ui.configuration.ConfigurationEntries
import com.nighthawkapps.lib.android.ui.configuration.RemoteConfig
import com.nighthawkapps.lib.android.ui.screen.backup.ext.Saver
import com.nighthawkapps.lib.android.ui.screen.backup.state.BackupState
import com.nighthawkapps.lib.android.ui.screen.backup.state.TestChoices
import com.nighthawkapps.lib.android.ui.screen.backup.view.LongNewWalletBackup
import com.nighthawkapps.lib.android.ui.screen.backup.view.ShortNewWalletBackup
import kotlinx.coroutines.launch

@Composable
internal fun MainActivity.WrapNewWallet(
    persistableWallet: PersistableDarkfiWallet,
    onBackupComplete: () -> Unit
) {
    if (ConfigurationEntries.IS_SHORT_NEW_WALLET_BACKUP_UX.getValue(RemoteConfig.current)) {
        WrapShortNewWallet(
            persistableWallet,
            onBackupComplete = onBackupComplete
        )
    } else {
        WrapLongNewWallet(
            persistableWallet,
            onBackupComplete = onBackupComplete
        )
    }
}

@Composable
internal fun MainActivity.WrapLongNewWallet(
    persistableWallet: PersistableDarkfiWallet,
    onBackupComplete: () -> Unit
) {
    WrapLongNewWallet(this, persistableWallet, onBackupComplete)
}

@Composable
internal fun WrapLongNewWallet(
    activity: ComponentActivity,
    persistableWallet: PersistableDarkfiWallet,
    onBackupComplete: () -> Unit
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    WrapLongNewWallet(
        persistableWallet,
        onCopyToClipboard = {
            scope.launch {
                clipboard.setSensitivePlainText(
                    context = context,
                    text = persistableWallet.seedPhrase.joinToString(),
                    label = "Seed",
                )
            }
        },
        onBackupComplete = onBackupComplete
    )
}

// This extra layer of indirection allows unit tests to validate the screen state retention.
@Composable
internal fun WrapLongNewWallet(
    persistableWallet: PersistableDarkfiWallet,
    onCopyToClipboard: () -> Unit,
    onBackupComplete: () -> Unit
) {
    val testChoices by rememberSaveable(stateSaver = TestChoices.Saver) { mutableStateOf(TestChoices()) }
    val backupState by rememberSaveable(stateSaver = BackupState.Saver) { mutableStateOf(BackupState()) }

    LongNewWalletBackup(
        persistableWallet,
        backupState,
        testChoices,
        onCopyToClipboard = onCopyToClipboard,
        onComplete = onBackupComplete,
        null
    )
}

@Composable
private fun MainActivity.WrapShortNewWallet(
    persistableWallet: PersistableDarkfiWallet,
    onBackupComplete: () -> Unit
) {
    WrapShortNewWallet(this, persistableWallet, onBackupComplete)
}

@Composable
private fun WrapShortNewWallet(
    activity: ComponentActivity,
    persistableWallet: PersistableDarkfiWallet,
    onBackupComplete: () -> Unit
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    WrapShortNewWallet(
        persistableWallet,
        onCopyToClipboard = {
            scope.launch {
                clipboard.setSensitivePlainText(
                    context = context,
                    text = persistableWallet.seedPhrase.joinToString(),
                    label = "Seed",
                )
            }
        },
        onNewWalletComplete = onBackupComplete
    )
}

@Composable
private fun WrapShortNewWallet(
    persistableWallet: PersistableDarkfiWallet,
    onCopyToClipboard: () -> Unit,
    onNewWalletComplete: () -> Unit
) {
    ShortNewWalletBackup(
        persistableWallet,
        onCopyToClipboard = onCopyToClipboard,
        onComplete = onNewWalletComplete,
    )
}
