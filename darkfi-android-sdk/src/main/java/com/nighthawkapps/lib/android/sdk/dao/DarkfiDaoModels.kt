package com.nighthawkapps.lib.android.sdk.dao

/**
 * Wallet-imported DAO (read-only M1).
 */
data class DarkfiDaoSummary(
    val name: String,
    val bullaB58: String,
    val govTokenId: String,
    val quorumDisplay: String,
    val proposerLimitDisplay: String,
    val approvalRatioPercent: Double,
    val mintHeight: Long?,
    val canPropose: Boolean,
    val canVote: Boolean,
    val canExec: Boolean,
)

data class DarkfiDaoProposalSummary(
    val proposalBullaB58: String,
    val daoName: String,
    val daoBullaB58: String,
    val authCallCount: Int,
    val durationBlockwindows: Long,
    val creationBlockwindow: Long,
    val mintHeight: Long?,
    val execHeight: Long?,
    val isExecuted: Boolean,
    val summaryLine: String,
) {
    val statusLabel: String
        get() =
            when {
                isExecuted -> "Paid"
                mintHeight != null -> "Open"
                else -> "Waiting"
            }

    /** Payment line when the summary has payout text. Otherwise a generic proposal. */
    val paymentTitle: String
        get() {
            val rest = summaryLine.substringAfter("· ", summaryLine).trim()
            return if (rest.startsWith("Pay ")) rest else "Generic proposal"
        }
}

data class DarkfiMintAuthority(
    val tokenId: String,
    val alias: String?,
    val frozen: Boolean,
)

data class DarkfiDaoProposalDetail(
    val summary: DarkfiDaoProposalSummary,
    val proposeTxHash: String?,
    val execTxHash: String?,
    val hasPlaintextData: Boolean,
)
