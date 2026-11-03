package com.nighthawkapps.lib.android.ui.screen.dao.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.sdk.dao.DarkfiDaoProposalDetail
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.design.component.BodyMedium
import com.nighthawkapps.lib.android.ui.design.component.BodySmall
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBar
import com.nighthawkapps.lib.android.ui.design.component.NighthawkTopBarLeading
import com.nighthawkapps.lib.android.ui.design.component.PrimaryButton
import com.nighthawkapps.lib.android.ui.design.component.TitleLarge
import com.nighthawkapps.lib.android.ui.design.component.TitleMedium
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.screen.dao.viewmodel.DaoViewModel

@Composable
fun DaoHubScreen(
    viewModel: DaoViewModel,
    onBack: () -> Unit,
    onSelectDao: (String) -> Unit,
) {
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val daos by viewModel.daos.collectAsStateWithLifecycle()
    val busy by viewModel.actionInProgress.collectAsStateWithLifecycle()
    val actionResult by viewModel.actionResult.collectAsStateWithLifecycle()

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
                DaoEmptyState(
                    onRefresh = { viewModel.loadHub() },
                    busy = busy,
                    onCreate = viewModel::createGroup,
                    onImport = viewModel::importGroup,
                )
            }

            else -> {
                val openVotes by viewModel.openVotes.collectAsStateWithLifecycle()
                DaoHubList(
                    entries = DaoHubEntries(daos, openVotes, busy),
                    commands =
                        DaoHubCommands(
                            onSelectDao = onSelectDao,
                            onCreate = viewModel::createGroup,
                            onImport = viewModel::importGroup,
                        ),
                )
            }
        }
        actionResult?.let { result ->
            Spacer(modifier = Modifier.height(8.dp))
            ActionResultBanner(result, onDismiss = { viewModel.clearActionResult() })
        }
    }
}

@Composable
@Suppress("LongMethod")
fun DaoDetailScreen(
    viewModel: DaoViewModel,
    onBack: () -> Unit,
    onSelectProposal: (String) -> Unit,
) {
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val dao by viewModel.selectedDao.collectAsStateWithLifecycle()
    val proposals by viewModel.proposals.collectAsStateWithLifecycle()
    val busy by viewModel.actionInProgress.collectAsStateWithLifecycle()
    val actionResult by viewModel.actionResult.collectAsStateWithLifecycle()
    val roleExport by viewModel.roleExport.collectAsStateWithLifecycle()
    val treasury by viewModel.treasury.collectAsStateWithLifecycle()
    val authorities by viewModel.mintAuthorities.collectAsStateWithLifecycle()
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
                val group = checkNotNull(dao)
                Column {
                    DaoGroupHome(
                        state =
                            DaoGroupHomeState(
                                dao = group,
                                proposals = proposals,
                                treasury = treasury,
                                authorities = authorities,
                                busy = busy,
                                wide = wideDaoLayout(),
                            ),
                        actions =
                            daoHomeActions(
                                viewModel = viewModel,
                                group = group,
                                onSelectProposal = onSelectProposal,
                                onPropose = { showProposeDialog = true },
                            ),
                    )
                    actionResult?.let { result ->
                        Spacer(modifier = Modifier.height(8.dp))
                        ActionResultBanner(result, onDismiss = { viewModel.clearActionResult() })
                    }
                }
            }
        }
        roleExport?.let { payload ->
            RoleExportDialog(payload = payload, onDismiss = { viewModel.clearRoleExport() })
        }
        if (showProposeDialog && dao != null) {
            val groupName = dao!!.name
            ProposeTransferDialog(
                daoName = groupName,
                busy = busy,
                onDismiss = { showProposeDialog = false },
                onSubmit = { duration, amount, tokenId, recipient ->
                    showProposeDialog = false
                    viewModel.proposeTransfer(
                        daoName = groupName,
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
    val busy by viewModel.actionInProgress.collectAsStateWithLifecycle()
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
                val summary = detail!!.summary
                val group by viewModel.selectedDao.collectAsStateWithLifecycle()
                Column {
                    ProposalDetailBody(detail!!)
                    ProposalActions(
                        state =
                            ProposalActionState(
                                summary = summary,
                                canVote = group?.canVote == true,
                                canExec = group?.canExec == true,
                                busy = busy,
                            ),
                        onVote = { yes -> viewModel.voteOnProposal(summary.proposalBullaB58, yes) },
                        onExec = { viewModel.execProposal(summary.proposalBullaB58) },
                    )
                    actionResult?.let { result ->
                        Spacer(modifier = Modifier.height(12.dp))
                        ActionResultBanner(result, onDismiss = { viewModel.clearActionResult() })
                    }
                }
            }
        }
    }
}

@Composable
internal fun ActionResultBanner(
    result: DaoViewModel.ActionResult,
    onDismiss: () -> Unit,
) {
    val text =
        when (result) {
            is DaoViewModel.ActionResult.Success -> result.message
            is DaoViewModel.ActionResult.Error -> result.message
        }
    val bgColor =
        when (result) {
            is DaoViewModel.ActionResult.Success -> MaterialTheme.colorScheme.secondary
            is DaoViewModel.ActionResult.Error -> MaterialTheme.colorScheme.error
        }
    Snackbar(
        action = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.ns_dao_dismiss),
                    color = MaterialTheme.colorScheme.onSecondary,
                )
            }
        },
        containerColor = bgColor,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSecondary)
    }
}

@Composable
private fun DaoScaffold(
    title: String,
    onBack: () -> Unit,
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
        TitleLarge(text = title)
        Spacer(modifier = Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun DaoEmptyState(
    onRefresh: () -> Unit,
    busy: Boolean,
    onCreate: (com.nighthawkapps.lib.android.ui.screen.dao.viewmodel.DaoCreateRequest) -> Unit,
    onImport: (String, String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TitleMedium(text = stringResource(R.string.ns_dao_hub_empty))
        Spacer(modifier = Modifier.height(12.dp))
        PrimaryButton(
            onClick = onRefresh,
            text = stringResource(R.string.ns_dao_refresh),
            enabled = !busy,
        )
        Spacer(modifier = Modifier.height(8.dp))
        GroupEntryButtons(busy = busy, onCreate = onCreate, onImport = onImport)
    }
}

@Composable
internal fun DaoLoading() {
    Box(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun DaoMessage(text: String) {
    BodyMedium(
        text = text,
        textAlign = TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun votingWindowLabel(blockWindows: Long): String {
    val windows = blockWindows.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    val hours =
        (blockWindows.coerceAtLeast(0L) * HOURS_PER_WINDOW)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    return pluralStringResource(
        R.plurals.ns_dao_voting_window,
        windows,
        windows,
        hours,
    )
}

private const val HOURS_PER_WINDOW = 4L

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
