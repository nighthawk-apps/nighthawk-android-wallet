package com.nighthawkapps.lib.android.ui.screen.wallet.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiAmountFormatter
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSyncMethod
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSyncStatus
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTransactionOverview
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.component.BodyMedium
import com.nighthawkapps.lib.android.ui.design.component.PrimaryButton
import com.nighthawkapps.lib.android.ui.design.component.TitleMedium
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.screen.home.model.WalletSnapshot
import kotlin.math.abs

@Composable
internal fun WalletCompanionHub(
    walletSnapshot: WalletSnapshot,
    receiveAddress: String,
    onCopyAddress: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(WalletTheme.dimens.spacingDefault),
    ) {
        TitleMedium(
            text = stringResource(id = R.string.ns_sync_method),
            color = WalletTheme.colors.secondaryTitleText,
        )
        Spacer(modifier = Modifier.height(8.dp))
        BodyMedium(
            text = privacyLine(walletSnapshot),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (receiveAddress.isNotBlank()) {
            Spacer(modifier = Modifier.height(24.dp))
            TitleMedium(
                text = stringResource(id = R.string.ns_address),
                color = WalletTheme.colors.secondaryTitleText,
            )
            Spacer(modifier = Modifier.height(8.dp))
            BodyMedium(
                text = receiveAddress,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(onClick = onCopyAddress) {
                BodyMedium(text = stringResource(id = R.string.ns_copy))
            }
        }
    }
}

@Composable
@Suppress("LongMethod")
internal fun TransactionDetailAndNextSteps(
    transaction: DarkfiTransactionOverview,
    onCopyTxId: () -> Unit,
    onSendAgain: () -> Unit,
) {
    val recipient = transaction.recipientAddress?.takeIf { it.isNotBlank() }
    val direction =
        stringResource(
            id =
                if (transaction.isSentTransaction) {
                    R.string.ns_sent
                } else {
                    R.string.ns_received
                },
        )
    val amount = DarkfiAmountFormatter.formatAtomic(transaction.amountExcludingFeeAtomic())
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(WalletTheme.dimens.spacingDefault),
    ) {
        TitleMedium(
            text = stringResource(id = R.string.ns_transaction_details),
            color = WalletTheme.colors.secondaryTitleText,
        )
        Spacer(modifier = Modifier.height(8.dp))
        BodyMedium(
            text = stringResource(R.string.ns_tx_direction_amount, direction, amount),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        BodyMedium(
            text =
                transaction.minedHeight?.let { height ->
                    stringResource(id = R.string.ns_block_id) + " $height"
                } ?: stringResource(id = R.string.ns_pending),
            color = WalletTheme.colors.secondaryTitleText,
        )
        if (recipient != null) {
            Spacer(modifier = Modifier.height(8.dp))
            BodyMedium(
                text = recipient,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (transaction.syncMethod != DarkfiSyncMethod.UNKNOWN) {
            Spacer(modifier = Modifier.height(8.dp))
            BodyMedium(
                text = transaction.syncMethod.displayLabel,
                color = WalletTheme.colors.secondaryTitleText,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        BodyMedium(
            text = transaction.rawId,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(24.dp))
        TitleMedium(
            text = stringResource(id = R.string.ns_next_steps),
            color = WalletTheme.colors.secondaryTitleText,
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
            onClick = onCopyTxId,
            modifier = Modifier.fillMaxWidth(),
        ) {
            BodyMedium(
                text = stringResource(id = R.string.ns_copy_transaction_id),
                textAlign = TextAlign.Center,
            )
        }
        if (transaction.isSentTransaction && recipient != null) {
            PrimaryButton(
                onClick = onSendAgain,
                text = stringResource(id = R.string.ns_send_again).uppercase(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Suppress("MaxLineLength")
internal fun DarkfiTransactionOverview.amountExcludingFeeAtomic(): Long = (abs(netValueAtomic) - totalFeeAtomic).coerceAtLeast(0L)

@Composable
private fun privacyLine(snapshot: WalletSnapshot): String {
    val method = syncMethodLabel(snapshot)
    val status = syncStatusLabel(snapshot)
    return listOf(method, status).filter { it.isNotBlank() }.joinToString(" · ")
}

@Composable
private fun syncMethodLabel(snapshot: WalletSnapshot): String =
    when (snapshot.syncMethod) {
        DarkfiSyncMethod.UNIF_OMR -> {
            if (snapshot.fallbackUserMessage.isNotBlank()) {
                stringResource(R.string.ns_sync_unifomr_fallback)
            } else {
                stringResource(R.string.ns_sync_unifomr)
            }
        }

        DarkfiSyncMethod.TRIAL_DECRYPT -> {
            stringResource(R.string.ns_sync_trial_decryption)
        }

        DarkfiSyncMethod.UNKNOWN -> {
            snapshot.syncTypeMessage.ifBlank { snapshot.syncMethod.displayLabel }
        }
    }

@Composable
private fun syncStatusLabel(snapshot: WalletSnapshot): String {
    val custom = snapshot.syncStatusMessage.takeIf { it.isNotBlank() }
    val tip = snapshot.processorInfo.chainTip
    val scanning =
        snapshot.status == DarkfiSyncStatus.SYNCING || snapshot.status == DarkfiSyncStatus.SCANNING
    val scanned = snapshot.processorInfo.scannedBlocks.toString()
    val tipLabel = tip.toString()
    return when {
        custom != null -> custom
        tip <= 0L -> ""
        scanning -> stringResource(R.string.ns_sync_block_progress, scanned, tipLabel)
        else -> stringResource(R.string.ns_sync_synced_block, tipLabel)
    }
}
