package com.nighthawkapps.lib.android.ui.screen.dao.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.sdk.dao.DarkfiDaoProposalSummary
import com.nighthawkapps.lib.android.sdk.dao.DarkfiMintAuthority
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.component.BodyMedium
import com.nighthawkapps.lib.android.ui.design.component.BodySmall
import com.nighthawkapps.lib.android.ui.design.component.PrimaryButton
import com.nighthawkapps.lib.android.ui.design.component.SecondaryButton
import com.nighthawkapps.lib.android.ui.design.component.TitleMedium
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme

private data class VoteCardModel(
    val proposal: DarkfiDaoProposalSummary,
    val canVote: Boolean,
    val canExec: Boolean,
    val busy: Boolean,
)

private data class VoteCardActions(
    val onOpen: (String) -> Unit,
    val onVote: (String, Boolean) -> Unit,
    val onExec: (String) -> Unit,
)

internal data class GroupClicks(
    val onFund: () -> Unit,
    val onIssue: () -> Unit,
    val onMint: (String) -> Unit,
    val onFreeze: (String) -> Unit,
)

@Composable
internal fun GroupVotes(
    state: DaoGroupHomeState,
    actions: DaoGroupHomeActions,
) {
    Column {
        TitleMedium(text = stringResource(R.string.ns_dao_proposals_section))
        if (state.proposals.isEmpty()) {
            BodyMedium(
                text = stringResource(R.string.ns_dao_proposals_empty),
                color = WalletTheme.colors.secondaryTitleText,
            )
        }
        state.proposals.forEach { proposal ->
            VoteCard(
                model =
                    VoteCardModel(
                        proposal = proposal,
                        canVote = state.dao.canVote,
                        canExec = state.dao.canExec,
                        busy = state.busy,
                    ),
                actions =
                    VoteCardActions(
                        onOpen = actions.navigation.onProposal,
                        onVote = actions.navigation.onVote,
                        onExec = actions.navigation.onExec,
                    ),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun GroupSide(
    state: DaoGroupHomeState,
    actions: DaoGroupHomeActions,
    clicks: GroupClicks,
) {
    Column {
        GroupTreasury(state, actions, clicks.onFund)
        Spacer(modifier = Modifier.height(16.dp))
        GroupTokens(state, clicks)
        GroupAdmin(state, actions)
    }
}

@Composable
private fun GroupTreasury(
    state: DaoGroupHomeState,
    actions: DaoGroupHomeActions,
    onFund: () -> Unit,
) {
    Column {
        TitleMedium(text = stringResource(R.string.ns_dao_section_treasury))
        BodyMedium(text = state.treasury)
        BodySmall(
            text = stringResource(R.string.ns_dao_read_only_hint),
            color = WalletTheme.colors.secondaryTitleText,
        )
        if (state.dao.canPropose) {
            PrimaryButton(
                onClick = actions.navigation.onPropose,
                text = stringResource(R.string.ns_dao_propose_payment),
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SecondaryButton(
            onClick = onFund,
            text = stringResource(R.string.ns_dao_add_funds),
            enabled = !state.busy,
        )
    }
}

@Composable
private fun GroupTokens(
    state: DaoGroupHomeState,
    clicks: GroupClicks,
) {
    Column {
        TitleMedium(text = stringResource(R.string.ns_dao_section_tokens))
        BodySmall(text = state.dao.govTokenId, color = WalletTheme.colors.secondaryTitleText)
        state.authorities.forEach { auth ->
            MintAuthorityRow(auth, state.busy, clicks.onMint, clicks.onFreeze)
        }
        SecondaryButton(
            onClick = clicks.onIssue,
            text = stringResource(R.string.ns_dao_issue_token),
            enabled = !state.busy,
        )
    }
}

@Composable
private fun MintAuthorityRow(
    auth: DarkfiMintAuthority,
    busy: Boolean,
    onMint: (String) -> Unit,
    onFreeze: (String) -> Unit,
) {
    Column {
        BodySmall(
            text = auth.alias ?: auth.tokenId,
            color = WalletTheme.colors.secondaryTitleText,
        )
        if (!auth.frozen) {
            TextButton(
                onClick = { onMint(auth.tokenId) },
                enabled = !busy,
            ) {
                Text(stringResource(R.string.ns_dao_issue_more))
            }
            TextButton(
                onClick = { onFreeze(auth.tokenId) },
                enabled = !busy,
            ) {
                Text(stringResource(R.string.ns_dao_freeze))
            }
        }
    }
}

@Composable
private fun GroupAdmin(
    state: DaoGroupHomeState,
    actions: DaoGroupHomeActions,
) {
    var showAdmin by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { showAdmin = !showAdmin }, enabled = !state.busy) {
            Text(stringResource(R.string.ns_dao_section_admin))
        }
        if (showAdmin) {
            BodyMedium(text = state.dao.roleLine())
            RoleExportButtons(state.dao, state.busy, actions.money.onExportRole)
            TextButton(onClick = { showDetails = !showDetails }) {
                Text(stringResource(R.string.ns_dao_details))
            }
            if (showDetails) {
                DaoParamsCard(state.dao)
                CopyableDetailLine(
                    LocalContext.current,
                    stringResource(R.string.ns_dao_group_id),
                    state.dao.bullaB58,
                )
            }
        }
    }
}

@Composable
private fun RoleExportButtons(
    dao: com.nighthawkapps.lib.android.sdk.dao.DarkfiDaoSummary,
    busy: Boolean,
    onExportRole: (String) -> Unit,
) {
    if (dao.canPropose) {
        RoleButton(R.string.ns_dao_role_proposer, "proposer", busy, onExportRole)
    }
    if (dao.canVote) {
        RoleButton(R.string.ns_dao_role_voter, "voter", busy, onExportRole)
    }
    if (dao.canExec) {
        RoleButton(R.string.ns_dao_role_executor, "executor", busy, onExportRole)
    }
    RoleButton(R.string.ns_dao_role_full, "all", busy, onExportRole)
}

@Composable
private fun RoleButton(
    labelRes: Int,
    role: String,
    busy: Boolean,
    onExportRole: (String) -> Unit,
) {
    TextButton(onClick = { onExportRole(role) }, enabled = !busy) {
        Text(stringResource(labelRes))
    }
}

@Composable
private fun VoteCard(
    model: VoteCardModel,
    actions: VoteCardActions,
) {
    val proposal = model.proposal
    val open = !proposal.isExecuted && proposal.mintHeight != null
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { actions.onOpen(proposal.proposalBullaB58) }
            .padding(vertical = 8.dp),
    ) {
        StatusBadge(proposal.statusLabel)
        BodyMedium(text = proposal.paymentTitle)
        if (model.canVote && open) {
            PrimaryButton(
                onClick = { actions.onVote(proposal.proposalBullaB58, true) },
                text = stringResource(R.string.ns_dao_vote_yes),
                enabled = !model.busy,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                onClick = { actions.onVote(proposal.proposalBullaB58, false) },
                text = stringResource(R.string.ns_dao_vote_no),
                enabled = !model.busy,
            )
        }
        if (model.canExec && open) {
            PrimaryButton(
                onClick = { actions.onExec(proposal.proposalBullaB58) },
                text = stringResource(R.string.ns_dao_pay_treasury),
                enabled = !model.busy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
