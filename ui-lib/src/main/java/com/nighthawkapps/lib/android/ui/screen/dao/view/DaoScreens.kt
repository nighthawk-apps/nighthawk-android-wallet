package com.nighthawkapps.lib.android.ui.screen.dao.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.sdk.dao.DarkfiDaoProposalDetail
import com.nighthawkapps.lib.android.sdk.dao.DarkfiDaoProposalSummary
import com.nighthawkapps.lib.android.sdk.dao.DarkfiDaoSummary
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.SettingsListItem
import com.nighthawkapps.lib.android.ui.design.component.BodyMedium
import com.nighthawkapps.lib.android.ui.design.component.BodySmall
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBar
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBarLeading
import com.nighthawkapps.lib.android.ui.design.component.TitleLarge
import com.nighthawkapps.lib.android.ui.design.component.TitleMedium
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.screen.dao.viewmodel.DaoViewModel

@Composable
fun DaoHubScreen(
    viewModel: DaoViewModel,
    onBack: () -> Unit,
    onDaoSelected: (String) -> Unit,
) {
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val daos by viewModel.daos.collectAsStateWithLifecycle()

    DaoScaffold(
        title = stringResource(R.string.ns_dao_hub_title),
        onBack = onBack,
    ) {
        when {
            loading -> {
                DaoLoading()
            }

            error != null -> {
                DaoMessage(error!!)
            }

            daos.isEmpty() -> {
                DaoEmptyState()
            }

            else -> {
                BodySmall(
                    text = stringResource(R.string.ns_dao_hub_subtitle),
                    color = WalletTheme.colors.secondaryTitleText,
                )
                Spacer(modifier = Modifier.height(12.dp))
                daos.forEach { dao ->
                    DaoRow(dao = dao, onClick = { onDaoSelected(dao.name) })
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun DaoDetailScreen(
    viewModel: DaoViewModel,
    onBack: () -> Unit,
    onProposalSelected: (String) -> Unit,
) {
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val dao by viewModel.selectedDao.collectAsStateWithLifecycle()
    val proposals by viewModel.proposals.collectAsStateWithLifecycle()
    val actionInProgress by viewModel.actionInProgress.collectAsStateWithLifecycle()
    val actionResult by viewModel.actionResult.collectAsStateWithLifecycle()
    var showProposeDialog by remember { mutableStateOf(false) }

    DaoScaffold(
        title = dao?.name ?: stringResource(R.string.ns_dao_detail_title),
        onBack = onBack,
    ) {
        when {
            loading -> {
                DaoLoading()
            }

            error != null -> {
                DaoMessage(error!!)
            }

            dao == null -> {
                DaoMessage(stringResource(R.string.ns_dao_not_found))
            }

            else -> {
                DaoParamsCard(checkNotNull(dao))
                Spacer(modifier = Modifier.height(16.dp))

                // ── Create Proposal button ──────────────────────
                if (dao!!.canPropose) {
                    Button(
                        onClick = { showProposeDialog = true },
                        enabled = !actionInProgress,
                        modifier = Modifier.fillMaxWidth(),
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50),
                            ),
                    ) {
                        if (actionInProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Create Proposal")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                TitleMedium(text = stringResource(R.string.ns_dao_proposals_section))
                Spacer(modifier = Modifier.height(8.dp))
                if (proposals.isEmpty()) {
                    BodyMedium(
                        text = stringResource(R.string.ns_dao_proposals_empty),
                        color = WalletTheme.colors.secondaryTitleText,
                    )
                } else {
                    proposals.forEach { proposal ->
                        ProposalRow(proposal = proposal, onClick = { onProposalSelected(proposal.proposalBullaB58) })
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // ── Action result snackbar ──────────────────────
                actionResult?.let { result ->
                    Spacer(modifier = Modifier.height(8.dp))
                    ActionResultBanner(result, onDismiss = { viewModel.clearActionResult() })
                }
            }
        }

        // ── Propose Dialog ──────────────────────────────────────────────
        if (showProposeDialog && dao != null) {
            ProposeTransferDialog(
                daoName = dao!!.name,
                onDismiss = { showProposeDialog = false },
                onSubmit = { duration, amount, tokenId, recipient ->
                    showProposeDialog = false
                    viewModel.proposeTransfer(
                        daoName = dao!!.name,
                        durationBlockwindows = duration,
                        amount = amount,
                        tokenId = tokenId,
                        recipientAddress = recipient,
                    )
                },
            )
        }
    }
}

@Composable
fun DaoProposalDetailScreen(
    viewModel: DaoViewModel,
    onBack: () -> Unit,
) {
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val detail by viewModel.proposalDetail.collectAsStateWithLifecycle()
    val actionInProgress by viewModel.actionInProgress.collectAsStateWithLifecycle()
    val actionResult by viewModel.actionResult.collectAsStateWithLifecycle()

    DaoScaffold(
        title = stringResource(R.string.ns_dao_proposal_detail_title),
        onBack = onBack,
    ) {
        when {
            loading -> {
                DaoLoading()
            }

            error != null -> {
                DaoMessage(error!!)
            }

            detail == null -> {
                DaoMessage(stringResource(R.string.ns_dao_proposal_not_found))
            }

            else -> {
                ProposalDetailBody(detail!!)

                // ── Vote buttons (active, non-executed proposals) ────
                val summary = detail!!.summary
                if (!summary.isExecuted && summary.mintHeight != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TitleMedium(text = "Cast Your Vote")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Button(
                            onClick = {
                                viewModel.voteOnProposal(
                                    proposalBullaB58 = summary.proposalBullaB58,
                                    voteYes = true,
                                )
                            },
                            enabled = !actionInProgress,
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50),
                                ),
                            modifier = Modifier.weight(1f),
                        ) {
                            if (actionInProgress) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White,
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text("Vote YES")
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.voteOnProposal(
                                    proposalBullaB58 = summary.proposalBullaB58,
                                    voteYes = false,
                                )
                            },
                            enabled = !actionInProgress,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Vote NO")
                        }
                    }
                    BodySmall(
                        text = "Your full governance token balance will be used as vote weight.",
                        color = WalletTheme.colors.secondaryTitleText,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                // ── Action result ────────────────────────────────────
                actionResult?.let { result ->
                    Spacer(modifier = Modifier.height(12.dp))
                    ActionResultBanner(result, onDismiss = { viewModel.clearActionResult() })
                }
            }
        }
    }
}

// ── Propose Dialog ──────────────────────────────────────────────────────

@Composable
private fun ProposeTransferDialog(
    daoName: String,
    onDismiss: () -> Unit,
    onSubmit: (duration: Long, amount: String, tokenId: String?, recipient: String) -> Unit,
) {
    var duration by remember { mutableStateOf("10") }
    var amount by remember { mutableStateOf("") }
    var tokenId by remember { mutableStateOf("") }
    var recipient by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Proposal for $daoName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = recipient,
                    onValueChange = { recipient = it },
                    label = { Text("Recipient Address") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tokenId,
                    onValueChange = { tokenId = it },
                    label = { Text("Token ID (optional, default DRK)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text("Duration (block windows)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                BodySmall(
                    text = "This will generate ZK proofs on-device and broadcast the proposal transaction.",
                    color = WalletTheme.colors.secondaryTitleText,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val d = duration.toLongOrNull() ?: 10L
                    onSubmit(d, amount.trim(), tokenId.trim().takeIf { it.isNotEmpty() }, recipient.trim())
                },
                enabled = amount.isNotBlank() && recipient.isNotBlank(),
            ) {
                Text("Submit Proposal")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ── Action Result Banner ────────────────────────────────────────────────

@Composable
private fun ActionResultBanner(
    result: DaoViewModel.ActionResult,
    onDismiss: () -> Unit,
) {
    val (text, bgColor) =
        when (result) {
            is DaoViewModel.ActionResult.Success -> result.message to Color(0xFF4CAF50)
            is DaoViewModel.ActionResult.Error -> result.message to MaterialTheme.colorScheme.error
        }
    Snackbar(
        action = {
            TextButton(onClick = onDismiss) { Text("Dismiss", color = Color.White) }
        },
        containerColor = bgColor,
    ) {
        Text(text, color = Color.White)
    }
}

// ── Scaffold ────────────────────────────────────────────────────────────

@Composable
private fun DaoScaffold(
    title: String,
    onBack: () -> Unit,
    showReadOnlyBadge: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WalletTheme.dimens.spacingDefault)
                .padding(vertical = dimensionResource(R.dimen.screen_standard_margin)),
    ) {
        NighthawkTopBar(
            onLeadingClick = onBack,
            leading = NighthawkTopBarLeading.Back,
            title = null,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TitleLarge(text = title)
            if (showReadOnlyBadge) {
                Spacer(modifier = Modifier.width(8.dp))
                ReadOnlyBadge()
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        content()
    }
}

// ── Empty State ─────────────────────────────────────────────────────────

@Composable
private fun DaoEmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "🏛️",
            fontSize = 48.sp,
        )
        Spacer(modifier = Modifier.height(16.dp))
        TitleMedium(text = "No DAOs found")
        Spacer(modifier = Modifier.height(8.dp))
        BodySmall(
            text =
                "Import a wallet that participates in a DAO, or wait for sync to finish. " +
                    "DAOs appear here when your wallet scans governance data from darkfid.",
            color = WalletTheme.colors.secondaryTitleText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

// ── DAO Row (Hub list) ──────────────────────────────────────────────────

@Composable
private fun DaoRow(
    dao: DarkfiDaoSummary,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = dimensionResource(R.dimen.setting_list_item_min_height))
                .padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BodyMedium(text = dao.name, modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(4.dp))
        BodySmall(
            text = stringResource(R.string.ns_dao_hub_row_desc, dao.quorumDisplay, dao.approvalRatioPercent),
            color = WalletTheme.colors.secondaryTitleText,
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (dao.canPropose) RoleBadge("Proposer", Color(0xFF4CAF50))
            if (dao.canVote) RoleBadge("Voter", Color(0xFF2196F3))
            if (dao.canExec) RoleBadge("Executor", Color(0xFFFF9800))
            if (!dao.canPropose && !dao.canVote && !dao.canExec) {
                RoleBadge("Observer", WalletTheme.colors.secondaryTitleText)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
    }
}

// ── DAO Params Card ─────────────────────────────────────────────────────

@Composable
private fun DaoParamsCard(dao: DarkfiDaoSummary) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        CopyableDetailLine(context, stringResource(R.string.ns_dao_field_gov_token), dao.govTokenId)
        DetailLine(stringResource(R.string.ns_dao_field_quorum), dao.quorumDisplay)
        DetailLine(stringResource(R.string.ns_dao_field_proposer_limit), dao.proposerLimitDisplay)
        DetailLine(
            stringResource(R.string.ns_dao_field_approval_ratio),
            "${dao.approvalRatioPercent}%",
        )
        dao.mintHeight?.let { DetailLine(stringResource(R.string.ns_dao_field_mint_height), it.toString()) }
        // Role badges instead of plain text
        BodySmall(text = stringResource(R.string.ns_dao_field_roles), color = WalletTheme.colors.secondaryTitleText)
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (dao.canPropose) RoleBadge("Proposer", Color(0xFF4CAF50))
            if (dao.canVote) RoleBadge("Voter", Color(0xFF2196F3))
            if (dao.canExec) RoleBadge("Executor", Color(0xFFFF9800))
            if (!dao.canPropose && !dao.canVote && !dao.canExec) {
                RoleBadge(stringResource(R.string.ns_dao_roles_none), WalletTheme.colors.secondaryTitleText)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

// ── Detail Lines ────────────────────────────────────────────────────────

@Composable
private fun DetailLine(
    label: String,
    value: String,
) {
    BodySmall(text = label, color = WalletTheme.colors.secondaryTitleText)
    BodyMedium(text = value)
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun CopyableDetailLine(
    context: Context,
    label: String,
    value: String,
) {
    BodySmall(text = label, color = WalletTheme.colors.secondaryTitleText)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable { copyToClipboard(context, label, value) },
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Filled.ContentCopy,
            contentDescription = "Copy $label",
            modifier = Modifier.size(14.dp),
            tint = WalletTheme.colors.secondaryTitleText,
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
}

// ── Proposal Row ────────────────────────────────────────────────────────

@Composable
private fun ProposalRow(
    proposal: DarkfiDaoProposalSummary,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = dimensionResource(R.dimen.setting_list_item_min_height))
                .padding(vertical = 8.dp),
    ) {
        StatusBadge(proposal.statusLabel)
        Spacer(modifier = Modifier.height(4.dp))
        BodySmall(
            text = proposal.summaryLine,
            color = WalletTheme.colors.secondaryTitleText,
        )
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
    }
}

// ── Proposal Detail ─────────────────────────────────────────────────────

@Composable
private fun ProposalDetailBody(detail: DarkfiDaoProposalDetail) {
    val context = LocalContext.current
    val p = detail.summary
    DetailLine(stringResource(R.string.ns_dao_field_dao), p.daoName)
    StatusBadge(p.statusLabel)
    Spacer(modifier = Modifier.height(8.dp))
    DetailLine(stringResource(R.string.ns_dao_field_auth_calls), p.authCallCount.toString())
    DetailLine(stringResource(R.string.ns_dao_field_duration_blocks), p.durationBlockwindows.toString())
    p.mintHeight?.let { DetailLine(stringResource(R.string.ns_dao_field_mint_height), it.toString()) }
    p.execHeight?.let { DetailLine(stringResource(R.string.ns_dao_field_exec_height), it.toString()) }
    detail.proposeTxHash?.let { CopyableDetailLine(context, stringResource(R.string.ns_dao_field_propose_tx), it) }
    detail.execTxHash?.let { CopyableDetailLine(context, stringResource(R.string.ns_dao_field_exec_tx), it) }
}

// ── Shared Components ───────────────────────────────────────────────────

@Composable
private fun ReadOnlyBadge() {
    Text(
        text = "Read-only · M1",
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier =
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun RoleBadge(
    label: String,
    color: Color,
) {
    Text(
        text = label,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier =
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(color.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun StatusBadge(status: String) {
    val color =
        when (status) {
            "Executed" -> Color(0xFF4CAF50)
            "Active" -> Color(0xFFFF9800)
            else -> WalletTheme.colors.secondaryTitleText
        }
    Text(
        text = status,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
    )
}

@Composable
private fun DaoLoading() {
    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DaoMessage(text: String) {
    BodyMedium(
        text = text,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun copyToClipboard(
    context: Context,
    label: String,
    value: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
}

// ── Preview ─────────────────────────────────────────────────────────────

@Preview
@Composable
private fun DaoHubPreview() {
    WalletTheme(darkTheme = false) {
        Surface {
            Column(Modifier.padding(16.dp)) {
                TitleLarge(stringResource(R.string.ns_dao_hub_title))
                BodySmall(stringResource(R.string.ns_dao_hub_subtitle))
            }
        }
    }
}
