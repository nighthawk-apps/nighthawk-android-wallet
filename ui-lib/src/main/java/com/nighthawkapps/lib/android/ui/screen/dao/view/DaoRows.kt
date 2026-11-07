package com.nighthawkapps.lib.android.ui.screen.dao.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nighthawkapps.lib.android.sdk.dao.DarkfiDaoProposalDetail
import com.nighthawkapps.lib.android.sdk.dao.DarkfiDaoProposalSummary
import com.nighthawkapps.lib.android.sdk.dao.DarkfiDaoSummary
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.component.BodyMedium
import com.nighthawkapps.lib.android.ui.design.component.BodySmall
import com.nighthawkapps.lib.android.ui.design.component.PrimaryButton
import com.nighthawkapps.lib.android.ui.design.component.SecondaryButton
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme

@Composable
internal fun DaoRow(
    dao: DarkfiDaoSummary,
    openVotes: Int,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = dimensionResource(R.dimen.setting_list_item_min_height))
                    .clickable(onClick = onClick)
                    .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            BodyMedium(text = dao.name, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(4.dp))
            BodySmall(
                text = stringResource(R.string.ns_dao_hub_row_desc, dao.roleLine(), openVotes),
                color = WalletTheme.colors.secondaryTitleText,
            )
        }
        HorizontalDivider(thickness = 1.dp, color = WalletTheme.colors.divider)
    }
}

@Composable
internal fun DaoParamsCard(dao: DarkfiDaoSummary) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        CopyableDetailLine(context, stringResource(R.string.ns_dao_field_gov_token), dao.govTokenId)
        DetailLine(stringResource(R.string.ns_dao_field_quorum), dao.quorumDisplay)
        DetailLine(stringResource(R.string.ns_dao_field_proposer_limit), dao.proposerLimitDisplay)
        DetailLine(
            stringResource(R.string.ns_dao_field_approval_ratio),
            "${dao.approvalRatioPercent}%",
        )
        dao.mintHeight?.let { height ->
            DetailLine(stringResource(R.string.ns_dao_field_mint_height), height.toString())
        }
        BodySmall(
            text = stringResource(R.string.ns_dao_field_roles),
            color = WalletTheme.colors.secondaryTitleText,
        )
        Spacer(modifier = Modifier.height(4.dp))
        BodyMedium(text = dao.roleLine())
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(
            modifier = Modifier.fillMaxWidth(),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
internal fun ProposalDetailBody(detail: DarkfiDaoProposalDetail) {
    val context = LocalContext.current
    val proposal = detail.summary
    var showDetails by remember { mutableStateOf(false) }
    Column {
        DetailLine(stringResource(R.string.ns_dao_field_dao), proposal.daoName)
        StatusBadge(proposal.statusLabel)
        Spacer(modifier = Modifier.height(8.dp))
        BodyMedium(text = proposal.paymentTitle)
        Spacer(modifier = Modifier.height(8.dp))
        BodySmall(
            text = votingWindowLabel(proposal.durationBlockwindows),
            color = WalletTheme.colors.secondaryTitleText,
        )
        TextButton(onClick = { showDetails = !showDetails }) {
            Text(stringResource(R.string.ns_dao_details))
        }
        if (showDetails) {
            ProposalExtraDetails(context, detail, proposal)
        }
    }
}

@Composable
private fun ProposalExtraDetails(
    context: Context,
    detail: DarkfiDaoProposalDetail,
    proposal: DarkfiDaoProposalSummary,
) {
    Column {
        CopyableDetailLine(
            context,
            stringResource(R.string.ns_dao_proposal_id),
            proposal.proposalBullaB58,
        )
        proposal.mintHeight?.let { height ->
            DetailLine(stringResource(R.string.ns_dao_field_mint_height), height.toString())
        }
        proposal.execHeight?.let { height ->
            DetailLine(stringResource(R.string.ns_dao_field_exec_height), height.toString())
        }
        detail.proposeTxHash?.let { hash ->
            CopyableDetailLine(context, stringResource(R.string.ns_dao_field_propose_tx), hash)
        }
        detail.execTxHash?.let { hash ->
            CopyableDetailLine(context, stringResource(R.string.ns_dao_field_exec_tx), hash)
        }
    }
}

internal data class ProposalActionState(
    val summary: DarkfiDaoProposalSummary,
    val canVote: Boolean,
    val canExec: Boolean,
    val busy: Boolean,
)

@Composable
internal fun ProposalActions(
    state: ProposalActionState,
    onVote: (Boolean) -> Unit,
    onExec: () -> Unit,
) {
    val summary = state.summary
    val open = !summary.isExecuted && summary.mintHeight != null
    Column {
        if (state.canVote && open) {
            Spacer(modifier = Modifier.height(16.dp))
            PrimaryButton(
                onClick = { onVote(true) },
                text = stringResource(R.string.ns_dao_vote_yes),
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                onClick = { onVote(false) },
                text = stringResource(R.string.ns_dao_vote_no),
                enabled = !state.busy,
            )
        }
        if (state.canExec && open) {
            PrimaryButton(
                onClick = onExec,
                text = stringResource(R.string.ns_dao_pay_treasury),
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun StatusBadge(status: String) {
    Text(
        text = status,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = WalletTheme.colors.secondaryTitleText,
    )
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
) {
    Column {
        BodySmall(text = label, color = WalletTheme.colors.secondaryTitleText)
        BodyMedium(text = value)
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
internal fun CopyableDetailLine(
    context: Context,
    label: String,
    value: String,
) {
    Column {
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
                contentDescription = stringResource(R.string.ns_dao_copy_cd, label),
                modifier = Modifier.size(14.dp),
                tint = WalletTheme.colors.secondaryTitleText,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

private fun copyToClipboard(
    context: Context,
    label: String,
    value: String,
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, context.getString(R.string.ns_dao_copied, label), Toast.LENGTH_SHORT).show()
}

@Composable
internal fun DarkfiDaoSummary.roleLine(): String {
    val roles =
        listOfNotNull(
            stringResource(R.string.ns_dao_role_proposer).takeIf { canPropose },
            stringResource(R.string.ns_dao_role_voter).takeIf { canVote },
            stringResource(R.string.ns_dao_role_executor).takeIf { canExec },
        )
    return if (roles.isEmpty()) {
        stringResource(R.string.ns_dao_roles_none)
    } else {
        roles.joinToString(", ")
    }
}
