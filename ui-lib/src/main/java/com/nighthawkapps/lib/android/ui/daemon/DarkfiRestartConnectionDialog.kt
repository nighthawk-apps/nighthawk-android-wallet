package com.nighthawkapps.lib.android.ui.daemon

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.AlertDialog

@Composable
fun DarkfiRestartConnectionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = stringResource(R.string.daemon_restart_dialog_title),
        desc = stringResource(R.string.daemon_restart_dialog_body),
        confirmText = stringResource(R.string.daemon_restart_dialog_confirm),
        dismissText = stringResource(R.string.daemon_restart_dialog_cancel),
        onConfirm = {
            onConfirm()
            onDismiss()
        },
        onDismiss = onDismiss,
        onDismissRequest = onDismiss,
    )
}
