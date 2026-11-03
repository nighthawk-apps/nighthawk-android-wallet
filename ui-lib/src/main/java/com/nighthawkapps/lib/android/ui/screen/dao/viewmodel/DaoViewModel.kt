@file:Suppress("TooManyFunctions", "TooGenericExceptionCaught")

package com.nighthawkapps.lib.android.ui.screen.dao.viewmodel

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nighthawkapps.lib.android.global.AppWalletCoordinator
import com.nighthawkapps.lib.android.sdk.dao.DarkfiDaoProposalDetail
import com.nighthawkapps.lib.android.sdk.dao.DarkfiDaoProposalSummary
import com.nighthawkapps.lib.android.sdk.dao.DarkfiDaoSummary
import com.nighthawkapps.lib.android.sdk.dao.DarkfiMintAuthority
import com.nighthawkapps.lib.android.sdk.uniffi.DarkfiNativeProbe
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSynchronizer
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTransferResult
import com.nighthawkapps.lib.android.ui.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DaoCreateRequest(
    val name: String,
    val proposerLimit: String,
    val quorum: String,
    val earlyExecQuorum: String,
    val approvalRatio: Double,
    val govTokenId: String,
)

class DaoViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val walletCoordinator = AppWalletCoordinator.get(application)

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _daos = MutableStateFlow<List<DarkfiDaoSummary>>(emptyList())
    val daos: StateFlow<List<DarkfiDaoSummary>> = _daos.asStateFlow()

    private val _selectedDao = MutableStateFlow<DarkfiDaoSummary?>(null)
    val selectedDao: StateFlow<DarkfiDaoSummary?> = _selectedDao.asStateFlow()

    private val _proposals = MutableStateFlow<List<DarkfiDaoProposalSummary>>(emptyList())
    val proposals: StateFlow<List<DarkfiDaoProposalSummary>> = _proposals.asStateFlow()

    private val _proposalDetail = MutableStateFlow<DarkfiDaoProposalDetail?>(null)
    val proposalDetail: StateFlow<DarkfiDaoProposalDetail?> = _proposalDetail.asStateFlow()

    private val _actionInProgress = MutableStateFlow(false)
    val actionInProgress: StateFlow<Boolean> = _actionInProgress.asStateFlow()

    private val _actionResult = MutableStateFlow<ActionResult?>(null)
    val actionResult: StateFlow<ActionResult?> = _actionResult.asStateFlow()

    private val _openVotes = MutableStateFlow<Map<String, Int>>(emptyMap())
    val openVotes: StateFlow<Map<String, Int>> = _openVotes.asStateFlow()

    private val _treasury = MutableStateFlow(text(R.string.ns_dao_not_scanned))
    val treasury: StateFlow<String> = _treasury.asStateFlow()

    private val _mintAuthorities = MutableStateFlow<List<DarkfiMintAuthority>>(emptyList())
    val mintAuthorities: StateFlow<List<DarkfiMintAuthority>> = _mintAuthorities.asStateFlow()

    private val _roleExport = MutableStateFlow<String?>(null)
    val roleExport: StateFlow<String?> = _roleExport.asStateFlow()

    sealed class ActionResult {
        data class Success(
            val message: String,
        ) : ActionResult()

        data class Error(
            val message: String,
        ) : ActionResult()
    }

    fun clearActionResult() {
        _actionResult.value = null
    }

    fun loadHub() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val sync = requireSync() ?: return@launch
            try {
                refreshHub(sync)
            } catch (e: Exception) {
                _error.value = e.message ?: text(R.string.ns_dao_load_failed)
            }
            _loading.value = false
        }
    }

    fun loadDaoDetail(daoName: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val sync = requireSync() ?: return@launch
            try {
                val daos = _daos.value.ifEmpty { sync.listDaos() }
                _selectedDao.value = daos.find { it.name == daoName }
                if (_selectedDao.value == null) {
                    _error.value = text(R.string.ns_dao_missing, daoName)
                    _proposals.value = emptyList()
                } else {
                    refreshTreasuryAndProposals(sync, daoName)
                    _mintAuthorities.value = mintAuthoritiesOf(sync)
                }
            } catch (e: Exception) {
                _error.value = e.message ?: text(R.string.ns_dao_detail_load_failed)
            }
            _loading.value = false
        }
    }

    fun loadProposalDetail(proposalBullaB58: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val sync = requireSync() ?: return@launch
            try {
                _proposalDetail.value = sync.getProposal(proposalBullaB58)
                if (_proposalDetail.value == null) {
                    _error.value = text(R.string.ns_dao_proposal_missing)
                }
            } catch (e: Exception) {
                _error.value = e.message ?: text(R.string.ns_dao_proposal_load_failed)
            }
            _loading.value = false
        }
    }

    fun proposeTransfer(
        daoName: String,
        durationBlockwindows: Long,
        amount: String,
        tokenId: String?,
        recipientAddress: String,
    ) {
        if (_actionInProgress.value) return
        _actionInProgress.value = true
        viewModelScope.launch {
            _actionResult.value = null
            val sync = requireSync()
            if (sync == null) {
                _actionInProgress.value = false
                return@launch
            }
            try {
                when (
                    val result =
                        sync.daoProposeTransfer(
                            daoName = daoName,
                            durationBlockwindows = durationBlockwindows,
                            amount = amount,
                            tokenId = tokenId,
                            recipientAddress = recipientAddress,
                        )
                ) {
                    is DarkfiTransferResult.Success -> {
                        _actionResult.value =
                            ActionResult.Success(
                                text(R.string.ns_dao_proposal_submitted, result.value.take(SHORT_ID)),
                            )
                        refreshTreasuryAndProposals(sync, daoName)
                    }

                    is DarkfiTransferResult.Failure -> {
                        _actionResult.value = ActionResult.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Error(e.message ?: text(R.string.ns_dao_propose_failed))
            }
            _actionInProgress.value = false
        }
    }

    fun voteOnProposal(
        proposalBullaB58: String,
        voteYes: Boolean,
    ) {
        if (_actionInProgress.value) return
        _actionInProgress.value = true
        viewModelScope.launch {
            _actionResult.value = null
            val sync = requireSync()
            if (sync == null) {
                _actionInProgress.value = false
                return@launch
            }
            try {
                when (
                    val result =
                        sync.daoVote(
                            proposalBullaB58 = proposalBullaB58,
                            voteYes = voteYes,
                        )
                ) {
                    is DarkfiTransferResult.Success -> {
                        val messageId =
                            if (voteYes) {
                                R.string.ns_dao_vote_yes_submitted
                            } else {
                                R.string.ns_dao_vote_no_submitted
                            }
                        _actionResult.value =
                            ActionResult.Success(text(messageId, result.value.take(SHORT_ID)))
                        daoNameFor(proposalBullaB58)?.let { refreshTreasuryAndProposals(sync, it) }
                    }

                    is DarkfiTransferResult.Failure -> {
                        _actionResult.value = ActionResult.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Error(e.message ?: text(R.string.ns_dao_vote_failed))
            }
            _actionInProgress.value = false
        }
    }

    fun execProposal(proposalBullaB58: String) {
        runDaoAction(
            success = text(R.string.ns_dao_payment_sent),
            refresh = DaoRefresh.Treasury,
            daoName = daoNameFor(proposalBullaB58),
        ) { it.daoExec(proposalBullaB58) }
    }

    fun fundDao(
        daoName: String,
        amount: String,
        tokenId: String?,
    ) {
        runDaoAction(
            success = text(R.string.ns_dao_funds_sent),
            refresh = DaoRefresh.Treasury,
            daoName = daoName,
        ) { it.daoFund(daoName, amount, tokenId) }
    }

    fun createGroup(request: DaoCreateRequest) {
        runDaoAction(
            success = text(R.string.ns_dao_group_submitted),
            refresh = DaoRefresh.Hub,
        ) {
            it.daoCreate(
                request.name,
                request.proposerLimit,
                request.quorum,
                request.earlyExecQuorum,
                request.approvalRatio,
                request.govTokenId,
            )
        }
    }

    fun importGroup(
        name: String,
        toml: String,
    ) {
        runDaoAction(
            success = text(R.string.ns_dao_group_imported),
            refresh = DaoRefresh.Hub,
        ) { it.daoImport(name, toml) }
    }

    fun issueToken(
        alias: String?,
        amount: String,
        recipient: String,
    ) {
        if (_actionInProgress.value) return
        _actionInProgress.value = true
        viewModelScope.launch {
            _actionResult.value = null
            val sync = requireSync()
            if (sync == null) {
                _actionInProgress.value = false
                return@launch
            }
            try {
                when (val created = sync.tokenGenerate(alias?.takeIf { it.isNotBlank() })) {
                    is DarkfiTransferResult.Failure -> {
                        _actionResult.value = ActionResult.Error(created.message)
                    }

                    is DarkfiTransferResult.Success -> {
                        when (val minted = sync.tokenMint(created.value, amount, recipient)) {
                            is DarkfiTransferResult.Failure -> {
                                _actionResult.value = ActionResult.Error(minted.message)
                            }

                            is DarkfiTransferResult.Success -> {
                                _actionResult.value = ActionResult.Success(text(R.string.ns_dao_token_issued))
                            }
                        }
                        _mintAuthorities.value = mintAuthoritiesOf(sync)
                    }
                }
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Error(e.message ?: text(R.string.ns_dao_token_failed))
            }
            _actionInProgress.value = false
        }
    }

    fun mintToken(
        tokenId: String,
        amount: String,
        recipient: String,
    ) {
        runDaoAction(
            success = text(R.string.ns_dao_tokens_minted),
            refresh = DaoRefresh.Mints,
        ) { it.tokenMint(tokenId, amount, recipient) }
    }

    fun freezeToken(tokenId: String) {
        runDaoAction(
            success = text(R.string.ns_dao_supply_frozen),
            refresh = DaoRefresh.Mints,
        ) { it.tokenFreeze(tokenId) }
    }

    fun exportRole(
        daoName: String,
        role: String,
    ) {
        if (_actionInProgress.value) return
        _actionInProgress.value = true
        viewModelScope.launch {
            val sync = requireSync()
            if (sync == null) {
                _actionInProgress.value = false
                return@launch
            }
            _roleExport.value = null
            when (val result = sync.daoExportRole(daoName, role)) {
                is DarkfiTransferResult.Success -> _roleExport.value = result.value
                is DarkfiTransferResult.Failure -> _actionResult.value = ActionResult.Error(result.message)
            }
            _actionInProgress.value = false
        }
    }

    fun clearRoleExport() {
        _roleExport.value = null
    }

    private fun runDaoAction(
        success: String,
        refresh: DaoRefresh,
        daoName: String? = null,
        block: suspend (DarkfiSynchronizer) -> DarkfiTransferResult<String>,
    ) {
        if (_actionInProgress.value) return
        _actionInProgress.value = true
        viewModelScope.launch {
            _actionResult.value = null
            val sync = requireSync()
            if (sync == null) {
                _actionInProgress.value = false
                return@launch
            }
            try {
                when (val result = block(sync)) {
                    is DarkfiTransferResult.Success -> {
                        _actionResult.value = ActionResult.Success(success)
                        applyRefresh(sync, refresh, daoName)
                    }

                    is DarkfiTransferResult.Failure -> {
                        _actionResult.value = ActionResult.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                _actionResult.value = ActionResult.Error(e.message ?: success)
            }
            _actionInProgress.value = false
        }
    }

    private suspend fun applyRefresh(
        sync: DarkfiSynchronizer,
        refresh: DaoRefresh,
        daoName: String?,
    ) {
        when (refresh) {
            DaoRefresh.Hub -> refreshHub(sync)
            DaoRefresh.Treasury -> daoName?.let { refreshTreasuryAndProposals(sync, it) }
            DaoRefresh.Mints -> _mintAuthorities.value = mintAuthoritiesOf(sync)
        }
    }

    private suspend fun refreshHub(sync: DarkfiSynchronizer) {
        _daos.value = sync.listDaos()
        refreshOpenVotes(sync)
    }

    private suspend fun refreshTreasuryAndProposals(
        sync: DarkfiSynchronizer,
        daoName: String,
    ) {
        _proposals.value = sync.listProposals(daoName)
        _treasury.value = sync.daoTreasury(daoName)
        refreshOpenVotes(sync)
    }

    private suspend fun refreshOpenVotes(sync: DarkfiSynchronizer) {
        val proposals = runCatching { sync.listProposals(null) }.getOrDefault(emptyList())
        _openVotes.value =
            proposals
                .filter { !it.isExecuted }
                .groupingBy { it.daoName }
                .eachCount()
    }

    private suspend fun mintAuthoritiesOf(sync: DarkfiSynchronizer): List<DarkfiMintAuthority> =
        runCatching { sync.listMintAuthorities() }.getOrDefault(emptyList())

    private fun daoNameFor(proposalBullaB58: String): String? =
        _selectedDao.value?.name
            ?: _proposalDetail.value
                ?.summary
                ?.takeIf { it.proposalBullaB58 == proposalBullaB58 }
                ?.daoName
            ?: _proposals.value.firstOrNull { it.proposalBullaB58 == proposalBullaB58 }?.daoName

    private suspend fun requireSync(): DarkfiSynchronizer? {
        val sync = walletCoordinator.synchronizer.value
        val message =
            when {
                DarkfiNativeProbe.run() !is DarkfiNativeProbe.Ok -> text(R.string.ns_dao_native_unavailable)
                sync == null -> text(R.string.ns_dao_wallet_loading)
                else -> null
            }
        if (message != null) {
            _error.value = message
            _loading.value = false
        }
        return if (message == null) sync else null
    }

    private fun text(
        @StringRes id: Int,
        vararg args: Any,
    ): String = getApplication<Application>().getString(id, *args)

    private enum class DaoRefresh {
        Hub,
        Treasury,
        Mints,
    }

    private companion object {
        const val SHORT_ID = 16
    }
}
