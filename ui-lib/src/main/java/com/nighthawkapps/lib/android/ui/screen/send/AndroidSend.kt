@file:Suppress("ktlint:standard:filename", "LongMethod", "CyclomaticComplexMethod", "MagicNumber")

package com.nighthawkapps.lib.android.ui.screen.send

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiAmountFormatter
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiAmountParser
import com.nighthawkapps.lib.android.sdk.wallet.SendBalanceCheck
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel
import com.nighthawkapps.lib.android.ui.screen.send.model.SendArgumentsWrapper
import com.nighthawkapps.lib.android.ui.screen.send.viewmodel.SendViewModel
import kotlinx.coroutines.delay

/**
 * LEGACY single-screen Material send form.
 *
 * Production uses [com.nighthawkapps.lib.android.ui.screen.send.nighthawk.AndroidSend]
 * (Amount → Recipient → Memo → Review wizard with PerfOMR/OMD default).
 * Only referenced from unused [com.nighthawkapps.lib.android.ui.Navigation].
 */
@Composable
internal fun MainActivity.WrapSend(
    sendArgumentsWrapper: SendArgumentsWrapper?,
    goToQrScanner: () -> Unit,
    goBack: () -> Unit,
) {
    WrapSend(this, sendArgumentsWrapper, goToQrScanner, goBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod", "CyclomaticComplexMethod", "MagicNumber")
@Composable
private fun WrapSend(
    activity: ComponentActivity,
    sendArgumentsWrapper: SendArgumentsWrapper?,
    goToQrScanner: () -> Unit,
    goBack: () -> Unit,
) {
    val walletViewModel by activity.viewModels<WalletViewModel>()
    val sendViewModel by activity.viewModels<SendViewModel>()
    val walletSnapshot by walletViewModel.walletSnapshot.collectAsStateWithLifecycle()
    val synchronizer by sendViewModel.synchronizer.collectAsStateWithLifecycle()
    val feeAtomic by sendViewModel.feeAtomic.collectAsStateWithLifecycle()
    val isEstimatingFee by sendViewModel.isEstimatingFee.collectAsStateWithLifecycle()
    val isSubmitting by sendViewModel.isSubmitting.collectAsStateWithLifecycle()
    val errorMessage by sendViewModel.errorMessage.collectAsStateWithLifecycle()
    val lastTxId by sendViewModel.lastTxId.collectAsStateWithLifecycle()
    val tokenBalances by (
        synchronizer?.tokenBalances
            ?: kotlinx.coroutines.flow.flowOf(emptyList())
    ).collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedTokenId by remember { mutableStateOf<String?>(null) }
    var tokenMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(tokenBalances) {
        if (selectedTokenId == null && tokenBalances.isNotEmpty()) {
            selectedTokenId = tokenBalances.first().tokenId
        }
    }

    var recipient by remember(sendArgumentsWrapper) {
        mutableStateOf(sendArgumentsWrapper?.recipientAddress.orEmpty())
    }
    var amountText by remember(sendArgumentsWrapper) {
        mutableStateOf(sendArgumentsWrapper?.amount.orEmpty())
    }
    var memoText by remember(sendArgumentsWrapper) {
        mutableStateOf(sendArgumentsWrapper?.memo.orEmpty())
    }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    val amountOk = DarkfiAmountParser.parseDisplayAmountToDrkString(amountText) != null
    val balanceAtomic = walletSnapshot?.confirmedBalanceAtomic ?: 0L
    val fieldsReady =
        canSendFields(
            recipient = recipient,
            amountOk = amountOk,
            nativeAvailable = synchronizer?.supportsNativeTransfer == true,
        )
    val balanceCheck =
        if (fieldsReady) {
            sendViewModel.evaluateBalance(balanceAtomic, amountText)
        } else {
            null
        }
    val canSend = fieldsReady && balanceCheck == SendBalanceCheck.Ok

    LaunchedEffect(recipient, amountText, memoText, selectedTokenId, fieldsReady) {
        if (!fieldsReady) {
            return@LaunchedEffect
        }
        delay(400)
        sendViewModel.estimateFee(
            recipient,
            amountText,
            tokenId = selectedTokenId,
            paymentMemo = memoText.ifBlank { null },
        )
    }

    BackHandler { goBack() }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.send_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text =
                if (synchronizer?.supportsNativeTransfer == true) {
                    stringResource(R.string.send_body_native)
                } else {
                    stringResource(R.string.send_body_unavailable)
                },
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text =
                stringResource(
                    R.string.send_balance_atomic,
                    balanceAtomic,
                    DarkfiAmountFormatter.formatAtomic(balanceAtomic),
                ),
            style = MaterialTheme.typography.bodySmall,
        )
        feeAtomic?.let { fee ->
            Text(
                text =
                    stringResource(
                        R.string.send_estimated_fee,
                        fee,
                        DarkfiAmountFormatter.formatAtomic(fee),
                    ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (isEstimatingFee) {
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator()
        }
        if (balanceCheck is SendBalanceCheck.Insufficient) {
            Spacer(Modifier.height(8.dp))
            Text(
                text =
                    stringResource(
                        R.string.send_insufficient_balance,
                        DarkfiAmountFormatter.formatAtomic(balanceCheck.requiredAtomic),
                        DarkfiAmountFormatter.formatAtomic(balanceCheck.availableAtomic),
                    ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        errorMessage?.let { msg ->
            Spacer(Modifier.height(8.dp))
            Text(text = msg, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = recipient,
            onValueChange = { recipient = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.send_recipient_label)) },
            singleLine = true,
        )
        if (tokenBalances.size > 1) {
            Spacer(Modifier.height(12.dp))
            val selectedLabel =
                tokenBalances
                    .firstOrNull { it.tokenId == selectedTokenId }
                    ?.displayName
                    ?: selectedTokenId.orEmpty()
            ExposedDropdownMenuBox(
                expanded = tokenMenuExpanded,
                onExpandedChange = { tokenMenuExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                    label = { Text(stringResource(R.string.send_token_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tokenMenuExpanded) },
                )
                ExposedDropdownMenu(
                    expanded = tokenMenuExpanded,
                    onDismissRequest = { tokenMenuExpanded = false },
                ) {
                    tokenBalances.forEach { token ->
                        DropdownMenuItem(
                            text = { Text("${token.displayName} (${DarkfiAmountFormatter.formatAtomic(token.balanceAtomic)})") },
                            onClick = {
                                selectedTokenId = token.tokenId
                                tokenMenuExpanded = false
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.send_amount_label)) },
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = memoText,
            onValueChange = { memoText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.send_memo)) },
            minLines = 2,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (sendViewModel.evaluateBalance(balanceAtomic, amountText) == SendBalanceCheck.Ok) {
                    showConfirmDialog = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = canSend && !isSubmitting,
        ) {
            Text(stringResource(R.string.send_submit))
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = goToQrScanner, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.send_scan_qr))
        }
    }

    if (showConfirmDialog) {
        val feeLabel = sendViewModel.formatFeeAtomic(feeAtomic)
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showConfirmDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        sendViewModel.submitTransfer(
                            recipientAddress = recipient,
                            amountDisplay = amountText,
                            confirmedBalanceAtomic = balanceAtomic,
                            tokenId = selectedTokenId,
                            paymentMemo = memoText.ifBlank { null },
                        ) {
                            showConfirmDialog = false
                            showSuccessDialog = true
                        }
                    },
                    enabled = !isSubmitting,
                ) {
                    Text(stringResource(R.string.send_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmDialog = false },
                    enabled = !isSubmitting,
                ) {
                    Text(stringResource(R.string.send_cancel))
                }
            },
            title = { Text(stringResource(R.string.send_confirm_title)) },
            text = {
                Column {
                    Text(
                        stringResource(
                            R.string.send_confirm_message,
                            amountText,
                            recipient,
                            feeLabel ?: stringResource(R.string.send_fee_unknown),
                        ),
                    )
                    memoText.trim().takeIf { it.isNotEmpty() }?.let { memo ->
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.send_confirmation_memo_format, memo))
                    }
                }
            },
        )
    }

    if (showSuccessDialog && lastTxId != null) {
        AlertDialog(
            onDismissRequest = {
                showSuccessDialog = false
                sendViewModel.clearTransientState()
                goBack()
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSuccessDialog = false
                        sendViewModel.clearTransientState()
                        goBack()
                    },
                ) {
                    Text(stringResource(R.string.send_success_ok))
                }
            },
            title = { Text(stringResource(R.string.send_success_title)) },
            text = { Text(stringResource(R.string.send_success_message, lastTxId!!)) },
        )
    }
}

private fun canSendFields(
    recipient: String,
    amountOk: Boolean,
    nativeAvailable: Boolean,
): Boolean = nativeAvailable && recipient.isNotBlank() && amountOk
