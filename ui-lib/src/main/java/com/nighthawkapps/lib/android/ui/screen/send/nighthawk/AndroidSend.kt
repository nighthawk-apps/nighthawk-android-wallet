@file:Suppress("LongMethod", "CyclomaticComplexMethod", "UnusedParameter")

package com.nighthawkapps.lib.android.ui.screen.send.nighthawk

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiAmountFormatter
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiAmountParser
import com.nighthawkapps.lib.android.sdk.wallet.SendBalanceCheck
import com.nighthawkapps.lib.android.sdk.wallet.maxSpendableAtomic
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.theme.LocalCyberpunkChrome
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.HomeViewModel
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel
import com.nighthawkapps.lib.android.ui.screen.send.model.SendArgumentsWrapper
import com.nighthawkapps.lib.android.ui.screen.send.model.SendStage
import com.nighthawkapps.lib.android.ui.screen.send.nighthawk.model.SendConfirmationState
import com.nighthawkapps.lib.android.ui.screen.send.nighthawk.view.EnterAmount
import com.nighthawkapps.lib.android.ui.screen.send.nighthawk.view.EnterMemo
import com.nighthawkapps.lib.android.ui.screen.send.nighthawk.view.EnterReceiverAddress
import com.nighthawkapps.lib.android.ui.screen.send.nighthawk.view.ReviewSend
import com.nighthawkapps.lib.android.ui.screen.send.nighthawk.view.SendConfirmation
import com.nighthawkapps.lib.android.ui.screen.send.viewmodel.SendViewModel
import kotlinx.coroutines.delay

/**
 * Full Nighthawk send wizard.
 *
 * Amount → Recipient → Memo → Review → Sending → Success | Failed
 *
 * OMR/OMD (PerfOMR) is the default and only send path: Rust
 * `build_transfer` / `broadcast_transfer` wrap the tx and call
 * lightwalletd `RegisterOmrClue` after broadcast.
 */
@Composable
internal fun MainActivity.AndroidSend(
    onBack: () -> Unit,
    onTopUpWallet: () -> Unit,
    navigateTo: (String) -> Unit,
    onScan: () -> Unit,
    sendArgumentsWrapper: SendArgumentsWrapper? = null,
) {
    NighthawkSendFlow(
        activity = this,
        sendArgumentsWrapper = sendArgumentsWrapper,
        onExit = onBack,
        onScan = onScan,
    )
}

@Composable
private fun NighthawkSendFlow(
    activity: ComponentActivity,
    sendArgumentsWrapper: SendArgumentsWrapper?,
    onExit: () -> Unit,
    onScan: () -> Unit,
) {
    val homeViewModel by activity.viewModels<HomeViewModel>()
    val walletViewModel by activity.viewModels<WalletViewModel>()
    val sendViewModel by activity.viewModels<SendViewModel>()
    val walletSnapshot by walletViewModel.walletSnapshot.collectAsStateWithLifecycle()
    val synchronizer by sendViewModel.synchronizer.collectAsStateWithLifecycle()
    val feeAtomic by sendViewModel.feeAtomic.collectAsStateWithLifecycle()
    val isEstimatingFee by sendViewModel.isEstimatingFee.collectAsStateWithLifecycle()
    val isSubmitting by sendViewModel.isSubmitting.collectAsStateWithLifecycle()
    val errorMessage by sendViewModel.errorMessage.collectAsStateWithLifecycle()
    val lastTxId by sendViewModel.lastTxId.collectAsStateWithLifecycle()
    val stageName by sendViewModel.stage.collectAsStateWithLifecycle()
    val amountText by sendViewModel.amountText.collectAsStateWithLifecycle()
    val recipient by sendViewModel.recipient.collectAsStateWithLifecycle()
    val memoText by sendViewModel.memoText.collectAsStateWithLifecycle()
    val selectedTokenIdRaw by sendViewModel.selectedTokenId.collectAsStateWithLifecycle()
    val pendingDeepLink by homeViewModel.sendDeepLinkDataFlow.collectAsStateWithLifecycle()
    val tokenBalances by (
        synchronizer?.tokenBalances
            ?: kotlinx.coroutines.flow.flowOf(emptyList())
    ).collectAsStateWithLifecycle(initialValue = emptyList())
    val stage = runCatching { SendStage.valueOf(stageName) }.getOrDefault(SendStage.Amount)
    val selectedTokenId = selectedTokenIdRaw.takeIf { it.isNotBlank() }

    LaunchedEffect(sendArgumentsWrapper) {
        sendViewModel.applyIncoming(
            recipientAddress = sendArgumentsWrapper?.recipientAddress,
            amountDisplay = sendArgumentsWrapper?.amount,
            memo = sendArgumentsWrapper?.memo,
            tokenId = sendArgumentsWrapper?.tokenId,
        )
    }

    LaunchedEffect(pendingDeepLink) {
        val link = pendingDeepLink ?: return@LaunchedEffect
        homeViewModel.sendDeepLinkData = null
        sendViewModel.applyDeepLink(link)
    }

    LaunchedEffect(tokenBalances) {
        val firstToken = tokenBalances.firstOrNull()?.tokenId ?: return@LaunchedEffect
        sendViewModel.selectTokenIfUnset(firstToken)
    }

    val amountOk = DarkfiAmountParser.parseDisplayAmountToDrkString(amountText) != null
    val balanceAtomic = walletSnapshot?.confirmedBalanceAtomic ?: 0L
    val nativeAvailable = synchronizer?.supportsNativeTransfer == true
    val fieldsReady = nativeAvailable && recipient.isNotBlank() && amountOk

    LaunchedEffect(recipient, amountText, memoText, selectedTokenId, fieldsReady, stage) {
        if (!fieldsReady || stage != SendStage.Review) return@LaunchedEffect
        delay(400)
        sendViewModel.estimateFee(
            recipient,
            amountText,
            tokenId = selectedTokenId,
            paymentMemo = memoText.ifBlank { null },
        )
    }

    fun goBackStage() {
        when (stage) {
            SendStage.Amount -> {
                sendViewModel.onFlowExit()
                onExit()
            }

            SendStage.Recipient -> {
                sendViewModel.setStage(SendStage.Amount)
            }

            SendStage.Memo -> {
                sendViewModel.setStage(SendStage.Recipient)
            }

            SendStage.Review -> {
                sendViewModel.setStage(SendStage.Memo)
            }

            SendStage.Sending -> {
                Unit
            }

            SendStage.Failed -> {
                sendViewModel.setStage(SendStage.Review)
            }

            SendStage.Success -> {
                sendViewModel.clearTransientState()
                sendViewModel.clearDraft()
                sendViewModel.onFlowExit()
                onExit()
            }
        }
    }

    BackHandler { goBackStage() }

    Surface(
        color =
            if (LocalCyberpunkChrome.current) {
                Color.Transparent
            } else {
                MaterialTheme.colorScheme.background
            },
    ) {
        when (stage) {
            SendStage.Amount -> {
                val unit =
                    tokenBalances.firstOrNull { it.tokenId == selectedTokenId }?.displayName
                        ?: "DRK"
                EnterAmount(
                    amountText = amountText,
                    spendableBalanceLabel =
                        stringResource(
                            R.string.ns_spendable_balance,
                            DarkfiAmountFormatter.formatAtomic(balanceAtomic),
                            unit,
                        ),
                    tokenBalances = tokenBalances,
                    selectedTokenId = selectedTokenId,
                    isContinueEnabled = amountOk && nativeAvailable,
                    errorMessage =
                        when {
                            !nativeAvailable -> stringResource(R.string.send_body_unavailable)
                            else -> errorMessage
                        },
                    onBack = { goBackStage() },
                    onAmountChanged = { sendViewModel.setAmountText(it) },
                    onTokenSelected = { sendViewModel.setSelectedTokenId(it) },
                    onMaxAmount = {
                        val token = tokenBalances.firstOrNull { it.tokenId == selectedTokenId }
                        val rawAtomic = token?.balanceAtomic ?: balanceAtomic
                        val feeFromThisAsset =
                            token == null ||
                                token.displayName.equals("DRK", ignoreCase = true)
                        sendViewModel.setAmountText(
                            DarkfiAmountFormatter.formatAtomic(
                                maxSpendableAtomic(
                                    availableAtomic = rawAtomic,
                                    feeAtomic = feeAtomic,
                                    feePaidFromThisAsset = feeFromThisAsset,
                                ),
                            ),
                        )
                    },
                    onContinue = { sendViewModel.setStage(SendStage.Recipient) },
                    onScanPaymentRequest = onScan,
                )
            }

            SendStage.Recipient -> {
                EnterReceiverAddress(
                    receiverAddress = recipient,
                    isContinueBtnEnabled = recipient.isNotBlank(),
                    onBack = { goBackStage() },
                    onValueChanged = { sendViewModel.setRecipient(it) },
                    onContinue = {
                        sendViewModel.setRecipient(it)
                        sendViewModel.setStage(SendStage.Memo)
                    },
                    onScan = onScan,
                )
            }

            SendStage.Memo -> {
                EnterMemo(
                    memoText = memoText,
                    onBack = { goBackStage() },
                    onMemoChanged = { sendViewModel.setMemoText(it) },
                    onContinue = { sendViewModel.setStage(SendStage.Review) },
                    onSkip = {
                        sendViewModel.setMemoText("")
                        sendViewModel.setStage(SendStage.Review)
                    },
                )
            }

            SendStage.Review -> {
                val balanceCheck =
                    if (fieldsReady) {
                        sendViewModel.evaluateBalance(balanceAtomic, amountText)
                    } else {
                        null
                    }
                val canSend = fieldsReady && balanceCheck == SendBalanceCheck.Ok && !isSubmitting
                val unit =
                    tokenBalances.firstOrNull { it.tokenId == selectedTokenId }?.displayName
                        ?: "DRK"
                ReviewSend(
                    amountLabel = "$amountText $unit",
                    recipient = recipient,
                    memo = memoText.ifBlank { null },
                    feeLabel = sendViewModel.formatFeeAtomic(feeAtomic)?.let { "$it $unit" },
                    isEstimatingFee = isEstimatingFee,
                    isSendEnabled = canSend,
                    errorMessage =
                        when {
                            !nativeAvailable -> {
                                stringResource(R.string.ns_send_ffi_unavailable)
                            }

                            balanceCheck is SendBalanceCheck.Insufficient -> {
                                stringResource(
                                    R.string.send_insufficient_balance,
                                    DarkfiAmountFormatter.formatAtomic(balanceCheck.requiredAtomic),
                                    DarkfiAmountFormatter.formatAtomic(balanceCheck.availableAtomic),
                                )
                            }

                            else -> {
                                errorMessage
                            }
                        },
                    onBack = { goBackStage() },
                    onSend = {
                        sendViewModel.setStage(SendStage.Sending)
                        sendViewModel.submitTransfer(
                            recipientAddress = recipient,
                            amountDisplay = amountText,
                            confirmedBalanceAtomic = balanceAtomic,
                            tokenId = selectedTokenId,
                            paymentMemo = memoText.ifBlank { null },
                        ) {
                            sendViewModel.setStage(SendStage.Success)
                        }
                    },
                )
            }

            SendStage.Sending -> {
                SendConfirmation(
                    sendConfirmationState = SendConfirmationState.Sending,
                    errorMessage = errorMessage,
                    onCancel = {
                        sendViewModel.onFlowExit()
                        onExit()
                    },
                    onTryAgain = {},
                    onDone = {},
                )
                LaunchedEffect(isSubmitting, errorMessage, lastTxId) {
                    if (isSubmitting) {
                        return@LaunchedEffect
                    }
                    if (lastTxId != null) {
                        sendViewModel.setStage(SendStage.Success)
                    } else if (errorMessage != null) {
                        sendViewModel.setStage(SendStage.Failed)
                    }
                }
            }

            SendStage.Failed -> {
                SendConfirmation(
                    sendConfirmationState = SendConfirmationState.Failed,
                    errorMessage = errorMessage,
                    onCancel = {
                        sendViewModel.clearTransientState()
                        sendViewModel.onFlowExit()
                        onExit()
                    },
                    onTryAgain = {
                        sendViewModel.clearTransientState()
                        sendViewModel.setStage(SendStage.Review)
                    },
                    onDone = {},
                )
            }

            SendStage.Success -> {
                SendConfirmation(
                    sendConfirmationState = SendConfirmationState.Success,
                    txId = lastTxId,
                    onCancel = {},
                    onTryAgain = {},
                    onDone = {
                        sendViewModel.clearTransientState()
                        sendViewModel.clearDraft()
                        onExit()
                    },
                )
            }
        }
    }
}
