package com.nighthawkapps.lib.android.ui.screen.dao.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nighthawkapps.lib.android.global.AppWalletCoordinator
import com.nighthawkapps.lib.android.sdk.dao.DarkfiDaoProposalDetail
import com.nighthawkapps.lib.android.sdk.dao.DarkfiDaoProposalSummary
import com.nighthawkapps.lib.android.sdk.dao.DarkfiDaoSummary
import com.nighthawkapps.lib.android.sdk.uniffi.DarkfiNativeProbe
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiSynchronizer
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiTransferResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    // ── Action state ────────────────────────────────────────────────────
    private val _actionInProgress = MutableStateFlow(false)
    val actionInProgress: StateFlow<Boolean> = _actionInProgress.asStateFlow()

    private val _actionResult = MutableStateFlow<ActionResult?>(null)
    val actionResult: StateFlow<ActionResult?> = _actionResult.asStateFlow()

    sealed class ActionResult {
        data class Success(
            val message: String
        ) : ActionResult()

        data class Error(
            val message: String
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
                _daos.value = sync.listDaos()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load DAOs"
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
                // Reuse already-loaded DAO list when available
                val daos = _daos.value.ifEmpty { sync.listDaos() }
                _selectedDao.value = daos.find { it.name == daoName }
                if (_selectedDao.value == null) {
                    _error.value = "DAO not found: $daoName"
                    _proposals.value = emptyList()
                } else {
                    _proposals.value = sync.listProposals(daoName)
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load DAO detail"
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
                    _error.value = "Proposal not found"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load proposal"
            }
            _loading.value = false
        }
    }

    // ── Propose ─────────────────────────────────────────────────────────

    fun proposeTransfer(
        daoName: String,
        durationBlockwindows: Long,
        amount: String,
        tokenId: String?,
        recipientAddress: String,
    ) {
        viewModelScope.launch {
            _actionInProgress.value = true
            _actionResult.value = null
            val sync = requireSync() ?: return@launch
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
                                "Proposal submitted! Bulla: ${result.value.take(16)}…"
                            )
                        // Refresh proposals list
                        _proposals.value = sync.listProposals(daoName)
                    }

                    is DarkfiTransferResult.Failure -> {
                        _actionResult.value = ActionResult.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                _actionResult.value =
                    ActionResult.Error(
                        e.message ?: "Propose failed"
                    )
            }
            _actionInProgress.value = false
        }
    }

    // ── Vote ────────────────────────────────────────────────────────────

    fun voteOnProposal(
        proposalBullaB58: String,
        voteYes: Boolean,
    ) {
        viewModelScope.launch {
            _actionInProgress.value = true
            _actionResult.value = null
            val sync = requireSync() ?: return@launch
            try {
                when (
                    val result =
                        sync.daoVote(
                            proposalBullaB58 = proposalBullaB58,
                            voteYes = voteYes,
                        )
                ) {
                    is DarkfiTransferResult.Success -> {
                        _actionResult.value =
                            ActionResult.Success(
                                "Vote ${if (voteYes) "YES" else "NO"} submitted! TX: ${result.value.take(16)}…"
                            )
                    }

                    is DarkfiTransferResult.Failure -> {
                        _actionResult.value = ActionResult.Error(result.message)
                    }
                }
            } catch (e: Exception) {
                _actionResult.value =
                    ActionResult.Error(
                        e.message ?: "Vote failed"
                    )
            }
            _actionInProgress.value = false
        }
    }

    private suspend fun requireSync(): DarkfiSynchronizer? {
        if (DarkfiNativeProbe.run() !is DarkfiNativeProbe.Ok) {
            _error.value = "Native wallet library unavailable. Run ./scripts/build-darkfi-mobile-ffi-android.sh and rebuild the app."
            _loading.value = false
            return null
        }
        val sync = walletCoordinator.synchronizer.value
        if (sync == null) {
            _error.value = "Wallet is still loading. Try again in a moment."
            _loading.value = false
            return null
        }
        return sync
    }
}
