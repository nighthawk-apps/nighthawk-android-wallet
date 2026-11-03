package com.nighthawkapps.lib.android.ui.screen.dao.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.sdk.dao.DarkfiDaoProposalSummary
import com.nighthawkapps.lib.android.sdk.dao.DarkfiDaoSummary
import com.nighthawkapps.lib.android.sdk.dao.DarkfiMintAuthority
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.component.BodySmall
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.screen.dao.viewmodel.DaoCreateRequest
import com.nighthawkapps.lib.android.ui.screen.dao.viewmodel.DaoViewModel

internal data class DaoGroupHomeState(
    val dao: DarkfiDaoSummary,
    val proposals: List<DarkfiDaoProposalSummary>,
    val treasury: String,
    val authorities: List<DarkfiMintAuthority>,
    val busy: Boolean,
    val wide: Boolean,
)

internal data class DaoNavigationActions(
    val onPropose: () -> Unit,
    val onProposal: (String) -> Unit,
    val onVote: (String, Boolean) -> Unit,
    val onExec: (String) -> Unit,
)

internal data class DaoMoneyActions(
    val onFund: (String, String?) -> Unit,
    val onIssue: (String?, String, String) -> Unit,
    val onMint: (String, String, String) -> Unit,
    val onFreeze: (String) -> Unit,
    val onExportRole: (String) -> Unit,
)

internal data class DaoGroupHomeActions(
    val navigation: DaoNavigationActions,
    val money: DaoMoneyActions,
)

internal data class DaoHubEntries(
    val daos: List<DarkfiDaoSummary>,
    val openVotes: Map<String, Int>,
    val busy: Boolean,
)

internal data class DaoHubCommands(
    val onSelectDao: (String) -> Unit,
    val onCreate: (DaoCreateRequest) -> Unit,
    val onImport: (String, String) -> Unit,
)

@Composable
internal fun DaoHubList(
    entries: DaoHubEntries,
    commands: DaoHubCommands,
) {
    Column {
        BodySmall(
            text = stringResource(R.string.ns_dao_hub_subtitle),
            color = WalletTheme.colors.secondaryTitleText,
        )
        Spacer(modifier = Modifier.height(12.dp))
        entries.daos.forEach { dao ->
            DaoRow(
                dao = dao,
                openVotes = entries.openVotes[dao.name] ?: 0,
                onClick = { commands.onSelectDao(dao.name) },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        GroupEntryButtons(
            busy = entries.busy,
            onCreate = commands.onCreate,
            onImport = commands.onImport,
        )
    }
}

@Composable
internal fun wideDaoLayout(): Boolean = LocalConfiguration.current.screenWidthDp >= WIDE_DP

@Composable
internal fun DaoGroupHome(
    state: DaoGroupHomeState,
    actions: DaoGroupHomeActions,
) {
    var showFund by remember { mutableStateOf(false) }
    var showIssue by remember { mutableStateOf(false) }
    var mintId by remember { mutableStateOf<String?>(null) }
    var freezeId by remember { mutableStateOf<String?>(null) }
    val clicks =
        GroupClicks(
            onFund = { showFund = true },
            onIssue = { showIssue = true },
            onMint = { id -> mintId = id },
            onFreeze = { id -> freezeId = id },
        )
    Column {
        if (state.wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) { GroupVotes(state, actions) }
                Column(Modifier.weight(1f)) { GroupSide(state, actions, clicks) }
            }
        } else {
            GroupSide(state, actions, clicks)
            Spacer(modifier = Modifier.height(16.dp))
            GroupVotes(state, actions)
        }
        DaoGroupDialogs(
            state = state,
            actions = actions,
            flags = GroupDialogFlags(showFund, showIssue, mintId, freezeId),
            dismiss =
                GroupDialogDismiss(
                    fund = { showFund = false },
                    issue = { showIssue = false },
                    mint = { mintId = null },
                    freeze = { freezeId = null },
                ),
        )
    }
}

internal fun daoHomeActions(
    viewModel: DaoViewModel,
    group: DarkfiDaoSummary,
    onSelectProposal: (String) -> Unit,
    onPropose: () -> Unit,
): DaoGroupHomeActions =
    DaoGroupHomeActions(
        navigation =
            DaoNavigationActions(
                onPropose = onPropose,
                onProposal = onSelectProposal,
                onVote = { bulla, yes -> viewModel.voteOnProposal(bulla, yes) },
                onExec = { bulla -> viewModel.execProposal(bulla) },
            ),
        money =
            DaoMoneyActions(
                onFund = { amount, token -> viewModel.fundDao(group.name, amount, token) },
                onIssue = { alias, amount, recipient ->
                    viewModel.issueToken(alias, amount, recipient)
                },
                onMint = { token, amount, recipient ->
                    viewModel.mintToken(token, amount, recipient)
                },
                onFreeze = { token -> viewModel.freezeToken(token) },
                onExportRole = { role -> viewModel.exportRole(group.name, role) },
            ),
    )

private const val WIDE_DP = 840
