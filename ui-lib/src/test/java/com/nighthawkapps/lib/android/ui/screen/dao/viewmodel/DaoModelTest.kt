package com.nighthawkapps.lib.android.ui.screen.dao.viewmodel

import com.nighthawkapps.lib.android.sdk.dao.DarkfiDaoProposalDetail
import com.nighthawkapps.lib.android.sdk.dao.DarkfiDaoProposalSummary
import com.nighthawkapps.lib.android.sdk.dao.DarkfiDaoSummary
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DarkfiDaoSummary] / [DarkfiDaoProposalSummary] model behavior
 * and status label derivation. These don't depend on Android and can run as plain
 * JUnit tests; the ViewModel itself needs Robolectric or instrumented tests because
 * it extends [AndroidViewModel].
 */
class DaoModelTest {
    // ── DarkfiDaoProposalSummary.statusLabel ─────────────────────────────

    @Test
    fun statusLabel_executedProposal() {
        val proposal = sampleProposal(isExecuted = true, mintHeight = 100)
        assertEquals("Executed", proposal.statusLabel)
    }

    @Test
    fun statusLabel_activeProposal() {
        val proposal = sampleProposal(isExecuted = false, mintHeight = 100)
        assertEquals("Active", proposal.statusLabel)
    }

    @Test
    fun statusLabel_pendingProposal() {
        val proposal = sampleProposal(isExecuted = false, mintHeight = null)
        assertEquals("Pending", proposal.statusLabel)
    }

    // ── DarkfiDaoSummary role checks ─────────────────────────────────────

    @Test
    fun daoSummary_allRolesTrue() {
        val dao = sampleDao(canPropose = true, canVote = true, canExec = true)
        assertTrue(dao.canPropose)
        assertTrue(dao.canVote)
        assertTrue(dao.canExec)
    }

    @Test
    fun daoSummary_noRoles() {
        val dao = sampleDao(canPropose = false, canVote = false, canExec = false)
        assertFalse(dao.canPropose)
        assertFalse(dao.canVote)
        assertFalse(dao.canExec)
    }

    // ── DarkfiDaoProposalDetail ──────────────────────────────────────────

    @Test
    fun proposalDetail_txHashesNullWhenAbsent() {
        val detail =
            DarkfiDaoProposalDetail(
                summary = sampleProposal(),
                proposeTxHash = null,
                execTxHash = null,
                hasPlaintextData = false,
            )
        assertNull(detail.proposeTxHash)
        assertNull(detail.execTxHash)
        assertFalse(detail.hasPlaintextData)
    }

    @Test
    fun proposalDetail_withTxHashes() {
        val detail =
            DarkfiDaoProposalDetail(
                summary = sampleProposal(isExecuted = true),
                proposeTxHash = "tx_propose_abc",
                execTxHash = "tx_exec_xyz",
                hasPlaintextData = true,
            )
        assertEquals("tx_propose_abc", detail.proposeTxHash)
        assertEquals("tx_exec_xyz", detail.execTxHash)
        assertTrue(detail.hasPlaintextData)
    }

    // ── ActionResult sealed class ─────────────────────────────────────

    @Test
    fun actionResult_successMessage() {
        val result = DaoViewModel.ActionResult.Success("Proposal submitted!")
        assertTrue(result is DaoViewModel.ActionResult.Success)
        assertEquals("Proposal submitted!", (result as DaoViewModel.ActionResult.Success).message)
    }

    @Test
    fun actionResult_errorMessage() {
        val result = DaoViewModel.ActionResult.Error("Vote failed: insufficient tokens")
        assertTrue(result is DaoViewModel.ActionResult.Error)
        assertEquals("Vote failed: insufficient tokens", (result as DaoViewModel.ActionResult.Error).message)
    }

    // ── canVote / canPropose role gating ─────────────────────────────

    @Test
    fun proposerOnly_canProposeTrue() {
        val dao = sampleDao(canPropose = true, canVote = false, canExec = false)
        assertTrue(dao.canPropose)
        assertFalse(dao.canVote)
    }

    @Test
    fun voterOnly_canVoteTrue() {
        val dao = sampleDao(canPropose = false, canVote = true, canExec = false)
        assertFalse(dao.canPropose)
        assertTrue(dao.canVote)
    }

    @Test
    fun activeProposal_isVoteable() {
        // Active = not executed + has mintHeight
        val proposal = sampleProposal(isExecuted = false, mintHeight = 100)
        assertEquals("Active", proposal.statusLabel)
        assertFalse(proposal.isExecuted)
    }

    @Test
    fun executedProposal_isNotVoteable() {
        val proposal = sampleProposal(isExecuted = true, mintHeight = 100)
        assertEquals("Executed", proposal.statusLabel)
        assertTrue(proposal.isExecuted)
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun sampleDao(
        name: String = "TestDAO",
        canPropose: Boolean = false,
        canVote: Boolean = false,
        canExec: Boolean = false,
    ) = DarkfiDaoSummary(
        name = name,
        bullaB58 = "bulla_test",
        govTokenId = "DRK",
        quorumDisplay = "100.00",
        proposerLimitDisplay = "10.00",
        approvalRatioPercent = 60.0,
        mintHeight = 1234L,
        canPropose = canPropose,
        canVote = canVote,
        canExec = canExec,
    )

    private fun sampleProposal(
        isExecuted: Boolean = false,
        mintHeight: Long? = null,
    ) = DarkfiDaoProposalSummary(
        proposalBullaB58 = "prop_bulla_test",
        daoName = "TestDAO",
        daoBullaB58 = "dao_bulla_test",
        authCallCount = 2,
        durationBlockwindows = 10,
        creationBlockwindow = 5,
        mintHeight = mintHeight,
        execHeight = if (isExecuted) 200L else null,
        isExecuted = isExecuted,
        summaryLine = "Test proposal summary",
    )
}
