package com.nighthawkapps.lib.android.ui.screen.dao

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.screen.dao.view.DaoDetailScreen
import com.nighthawkapps.lib.android.ui.screen.dao.view.DaoHubScreen
import com.nighthawkapps.lib.android.ui.screen.dao.view.DaoProposalDetailScreen
import com.nighthawkapps.lib.android.ui.screen.dao.viewmodel.DaoViewModel

@Composable
internal fun MainActivity.AndroidDaoHub(
    onBack: () -> Unit,
    onOpenDao: (String) -> Unit
) {
    val viewModel by viewModels<DaoViewModel>()
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { viewModel.loadHub() }
    DaoHubScreen(viewModel = viewModel, onBack = onBack, onDaoSelected = onOpenDao)
}

@Composable
internal fun MainActivity.AndroidDaoDetail(
    daoName: String,
    onBack: () -> Unit,
    onOpenProposal: (String) -> Unit,
) {
    val viewModel by viewModels<DaoViewModel>()
    BackHandler(onBack = onBack)
    LaunchedEffect(daoName) { viewModel.loadDaoDetail(daoName) }
    DaoDetailScreen(
        viewModel = viewModel,
        onBack = onBack,
        onProposalSelected = onOpenProposal,
    )
}

@Composable
internal fun MainActivity.AndroidDaoProposalDetail(
    proposalBullaB58: String,
    onBack: () -> Unit,
) {
    val viewModel by viewModels<DaoViewModel>()
    BackHandler(onBack = onBack)
    LaunchedEffect(proposalBullaB58) { viewModel.loadProposalDetail(proposalBullaB58) }
    DaoProposalDetailScreen(viewModel = viewModel, onBack = onBack)
}
