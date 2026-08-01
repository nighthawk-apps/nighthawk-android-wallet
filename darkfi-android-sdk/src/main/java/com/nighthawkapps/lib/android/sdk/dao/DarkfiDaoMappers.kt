package com.nighthawkapps.lib.android.sdk.dao

import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DrkDaoProposalDetail as FfiProposalDetail
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DrkDaoProposalSummary as FfiProposalSummary
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DrkDaoSummary as FfiDaoSummary

internal fun FfiDaoSummary.toSdk(): DarkfiDaoSummary =
    DarkfiDaoSummary(
        name = name,
        bullaB58 = bullaB58,
        govTokenId = govTokenId,
        quorumDisplay = quorumDisplay,
        proposerLimitDisplay = proposerLimitDisplay,
        approvalRatioPercent = approvalRatioPercent,
        mintHeight = mintHeight.takeIf { it >= 0 },
        canPropose = canPropose,
        canVote = canVote,
        canExec = canExec,
    )

internal fun FfiProposalSummary.toSdk(): DarkfiDaoProposalSummary =
    DarkfiDaoProposalSummary(
        proposalBullaB58 = proposalBullaB58,
        daoName = daoName,
        daoBullaB58 = daoBullaB58,
        authCallCount = authCallCount.toInt(),
        durationBlockwindows = durationBlockwindows.toLong(),
        creationBlockwindow = creationBlockwindow.toLong(),
        mintHeight = mintHeight.takeIf { it >= 0 },
        execHeight = execHeight.takeIf { it >= 0 },
        isExecuted = isExecuted,
        summaryLine = summaryLine,
    )

internal fun FfiProposalDetail.toSdk(): DarkfiDaoProposalDetail {
    val summary =
        DarkfiDaoProposalSummary(
            proposalBullaB58 = proposalBullaB58,
            daoName = daoName,
            daoBullaB58 = daoBullaB58,
            authCallCount = authCallCount.toInt(),
            durationBlockwindows = durationBlockwindows.toLong(),
            creationBlockwindow = creationBlockwindow.toLong(),
            mintHeight = mintHeight.takeIf { it >= 0 },
            execHeight = execHeight.takeIf { it >= 0 },
            isExecuted = isExecuted,
            summaryLine = summaryLine,
        )
    return DarkfiDaoProposalDetail(
        summary = summary,
        proposeTxHash = proposeTxHash,
        execTxHash = execTxHash,
        hasPlaintextData = hasPlaintextData,
    )
}
