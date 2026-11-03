package com.nighthawkapps.lib.android.ui.screen.dao.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.SecureScreen
import com.nighthawkapps.lib.android.ui.design.component.BodySmall
import com.nighthawkapps.lib.android.ui.design.component.SecondaryButton
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.screen.dao.viewmodel.DaoCreateRequest
import com.nighthawkapps.lib.android.ui.screen.receive.util.AndroidQrCodeImageGenerator
import com.nighthawkapps.lib.android.ui.screen.receive.util.JvmQrCodeGenerator

internal data class GroupDialogFlags(
    val showFund: Boolean,
    val showIssue: Boolean,
    val mintId: String?,
    val freezeId: String?,
)

internal data class GroupDialogDismiss(
    val fund: () -> Unit,
    val issue: () -> Unit,
    val mint: () -> Unit,
    val freeze: () -> Unit,
)

private data class AmountDialogSpec(
    val title: String,
    val confirm: String,
    val secondLabel: String,
    val requireSecond: Boolean,
    val busy: Boolean,
)

@Composable
internal fun GroupEntryButtons(
    busy: Boolean,
    onCreate: (DaoCreateRequest) -> Unit,
    onImport: (String, String) -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(
                onClick = { showCreate = true },
                text = stringResource(R.string.ns_dao_start_group),
                enabled = !busy,
                modifier = Modifier.weight(1f),
                outerPaddingValues = PaddingValues(end = 4.dp, top = 8.dp, bottom = 8.dp),
            )
            SecondaryButton(
                onClick = { showImport = true },
                text = stringResource(R.string.ns_dao_import_group),
                enabled = !busy,
                modifier = Modifier.weight(1f),
                outerPaddingValues = PaddingValues(start = 4.dp, top = 8.dp, bottom = 8.dp),
            )
        }
        if (showCreate) {
            CreateGroupDialog(
                busy = busy,
                onDismiss = { showCreate = false },
                onSubmit = { name, limit, quorum, early, ratio, token ->
                    showCreate = false
                    onCreate(DaoCreateRequest(name, limit, quorum, early, ratio, token))
                },
            )
        }
        if (showImport) {
            ImportGroupDialog(
                busy = busy,
                onDismiss = { showImport = false },
                onSubmit = { name, toml ->
                    showImport = false
                    onImport(name, toml)
                },
            )
        }
    }
}

@Composable
internal fun DaoGroupDialogs(
    state: DaoGroupHomeState,
    actions: DaoGroupHomeActions,
    flags: GroupDialogFlags,
    dismiss: GroupDialogDismiss,
) {
    if (flags.showFund) {
        AmountDialog(
            spec =
                AmountDialogSpec(
                    title = stringResource(R.string.ns_dao_add_funds),
                    confirm = stringResource(R.string.ns_dao_send_to_treasury),
                    secondLabel = stringResource(R.string.ns_dao_token_optional),
                    requireSecond = false,
                    busy = state.busy,
                ),
            onDismiss = dismiss.fund,
            onSubmit = { amount, token ->
                dismiss.fund()
                actions.money.onFund(amount, token)
            },
        )
    }
    flags.mintId?.let { token ->
        AmountDialog(
            spec =
                AmountDialogSpec(
                    title = stringResource(R.string.ns_dao_issue_more),
                    confirm = stringResource(R.string.ns_dao_mint),
                    secondLabel = stringResource(R.string.ns_dao_recipient),
                    requireSecond = true,
                    busy = state.busy,
                ),
            onDismiss = dismiss.mint,
            onSubmit = { amount, recipient ->
                dismiss.mint()
                actions.money.onMint(token, amount, recipient.orEmpty())
            },
        )
    }
    flags.freezeId?.let { token ->
        FreezeDialog(token, state.busy, dismiss.freeze) {
            dismiss.freeze()
            actions.money.onFreeze(token)
        }
    }
    if (flags.showIssue) {
        IssueTokenDialog(
            busy = state.busy,
            onDismiss = dismiss.issue,
        ) { name, amount, recipient ->
            dismiss.issue()
            actions.money.onIssue(name, amount, recipient)
        }
    }
}

@Composable
internal fun ProposeTransferDialog(
    daoName: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Long, String, String?, String) -> Unit,
) {
    var duration by remember { mutableStateOf("10") }
    var amount by remember { mutableStateOf("") }
    var tokenId by remember { mutableStateOf("") }
    var recipient by remember { mutableStateOf("") }
    val parsed = positiveDaoAmount(amount)
    val windows = duration.trim().toLongOrNull()
    val enabled =
        parsed != null && recipient.trim().isNotEmpty() && windows != null && windows > 0L && !busy
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ns_dao_propose_payment)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DaoField(recipient, { recipient = it }, stringResource(R.string.ns_dao_recipient), false)
                DaoAmountField(amount, { amount = it })
                DaoField(tokenId, { tokenId = it }, stringResource(R.string.ns_dao_token_optional), false)
                DaoField(
                    duration,
                    { duration = it },
                    stringResource(R.string.ns_dao_duration),
                    true,
                )
                BodySmall(
                    text = stringResource(R.string.ns_dao_propose_hint, daoName),
                    color = WalletTheme.colors.secondaryTitleText,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val ready = positiveDaoAmount(amount) ?: return@TextButton
                    val window = duration.trim().toLongOrNull() ?: return@TextButton
                    onSubmit(
                        window,
                        ready.plainAmount(),
                        tokenId.trim().ifEmpty { null },
                        recipient.trim(),
                    )
                },
                enabled = enabled,
            ) {
                Text(stringResource(R.string.ns_dao_propose))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ns_cancel)) } },
    )
}

@Composable
private fun AmountDialog(
    spec: AmountDialogSpec,
    onDismiss: () -> Unit,
    onSubmit: (String, String?) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    val parsed = positiveDaoAmount(amount)
    val secondOk = !spec.requireSecond || second.trim().isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(spec.title) },
        text = {
            Column {
                DaoAmountField(amount, { amount = it })
                DaoField(second, { second = it }, spec.secondLabel, false)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val ready = positiveDaoAmount(amount) ?: return@TextButton
                    onSubmit(ready.plainAmount(), second.trim().ifEmpty { null })
                },
                enabled = parsed != null && secondOk && !spec.busy,
            ) {
                Text(spec.confirm)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ns_cancel)) } },
    )
}

@Composable
private fun IssueTokenDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String?, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var recipient by remember { mutableStateOf("") }
    val parsed = positiveDaoAmount(amount)
    val recipientOk = recipient.trim().isNotEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ns_dao_issue_token)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                DaoField(name, { name = it }, stringResource(R.string.ns_dao_name), false)
                DaoAmountField(amount, { amount = it })
                DaoField(recipient, { recipient = it }, stringResource(R.string.ns_dao_recipient), false)
                BodySmall(
                    text = stringResource(R.string.ns_dao_token_decimals),
                    color = WalletTheme.colors.secondaryTitleText,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val ready = positiveDaoAmount(amount) ?: return@TextButton
                    onSubmit(name.trim().ifEmpty { null }, ready.plainAmount(), recipient.trim())
                },
                enabled = parsed != null && recipientOk && !busy,
            ) {
                Text(stringResource(R.string.ns_dao_issue_token_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ns_cancel)) } },
    )
}

@Composable
private fun CreateGroupDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, Double, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var quorum by remember { mutableStateOf("1") }
    var proposerMinimum by remember { mutableStateOf("1") }
    var earlyQuorum by remember { mutableStateOf("") }
    var yesShare by remember { mutableStateOf("0.67") }
    var token by remember { mutableStateOf("") }
    val quorumValue = positiveDaoAmount(quorum)
    val proposerValue = positiveDaoAmount(proposerMinimum)
    val earlyValue = if (earlyQuorum.isBlank()) quorumValue else positiveDaoAmount(earlyQuorum)
    val share = yesShareRatio(yesShare)
    val earlyOk = quorumValue != null && earlyValue != null && earlyValue >= quorumValue
    val enabled = name.trim().isNotEmpty() && proposerValue != null && earlyOk && share != null && !busy
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ns_dao_start_group)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                DaoField(name, { name = it }, stringResource(R.string.ns_dao_name), false)
                DaoField(token, { token = it }, stringResource(R.string.ns_dao_gov_token_field), false)
                DaoField(yesShare, { yesShare = it }, stringResource(R.string.ns_dao_yes_share), true)
                DaoField(quorum, { quorum = it }, stringResource(R.string.ns_dao_votes_needed), true)
                DaoField(
                    proposerMinimum,
                    { proposerMinimum = it },
                    stringResource(R.string.ns_dao_proposer_minimum),
                    true,
                )
                DaoField(
                    earlyQuorum,
                    { earlyQuorum = it },
                    stringResource(R.string.ns_dao_early_quorum),
                    true,
                )
                BodySmall(
                    text = stringResource(R.string.ns_dao_early_quorum_hint),
                    color = WalletTheme.colors.secondaryTitleText,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    submitCreate(
                        CreateDraft(name, proposerMinimum, quorum, earlyQuorum, yesShare, token),
                        onSubmit,
                    )
                },
                enabled = enabled,
            ) {
                Text(stringResource(R.string.ns_dao_create_group))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ns_cancel)) } },
    )
}

private data class CreateDraft(
    val name: String,
    val proposerMinimum: String,
    val quorum: String,
    val earlyQuorum: String,
    val yesShare: String,
    val token: String,
)

private fun submitCreate(
    draft: CreateDraft,
    onSubmit: (String, String, String, String, Double, String) -> Unit,
) {
    val quorumValue = positiveDaoAmount(draft.quorum)
    val proposerValue = positiveDaoAmount(draft.proposerMinimum)
    val earlyValue =
        if (draft.earlyQuorum.isBlank()) {
            quorumValue
        } else {
            positiveDaoAmount(draft.earlyQuorum)
        }
    val share = yesShareRatio(draft.yesShare)
    if (quorumValue != null && proposerValue != null && earlyValue != null && share != null) {
        if (earlyValue >= quorumValue) {
            onSubmit(
                draft.name.trim(),
                proposerValue.plainAmount(),
                quorumValue.plainAmount(),
                earlyValue.plainAmount(),
                share.toDouble(),
                draft.token.trim().ifEmpty { "DRK" },
            )
        }
    }
}

@Composable
private fun ImportGroupDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var toml by remember { mutableStateOf("") }
    val enabled = name.trim().isNotEmpty() && toml.trim().isNotEmpty() && !busy
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ns_dao_import_group)) },
        text = {
            Column {
                DaoField(name, { name = it }, stringResource(R.string.ns_dao_name), false)
                OutlinedTextField(
                    value = toml,
                    onValueChange = { toml = it },
                    label = { Text(stringResource(R.string.ns_dao_import_toml)) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(name.trim(), toml) },
                enabled = enabled,
            ) {
                Text(stringResource(R.string.ns_dao_import_group))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ns_cancel)) } },
    )
}

@Composable
private fun FreezeDialog(
    token: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ns_dao_freeze)) },
        text = { Text(token) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text(stringResource(R.string.ns_dao_freeze))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ns_cancel)) } },
    )
}

@Composable
internal fun RoleExportDialog(
    payload: String,
    onDismiss: () -> Unit,
) {
    SecureScreen()
    val qr =
        remember(payload) {
            runCatching {
                AndroidQrCodeImageGenerator.generate(
                    JvmQrCodeGenerator.generate(payload, ROLE_QR_SIZE),
                    ROLE_QR_SIZE,
                )
            }.getOrNull()
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ns_dao_role_export_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (qr != null) {
                    Image(
                        bitmap = qr,
                        contentDescription = stringResource(R.string.ns_dao_role_qr),
                        modifier = Modifier.size(ROLE_QR_SIZE.dp),
                    )
                } else {
                    Text(stringResource(R.string.ns_dao_qr_failed))
                }
                Spacer(modifier = Modifier.height(8.dp))
                BodySmall(
                    text = stringResource(R.string.ns_dao_role_import_hint),
                    color = WalletTheme.colors.secondaryTitleText,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ns_dao_close)) }
        },
    )
}

private const val ROLE_QR_SIZE = 240
