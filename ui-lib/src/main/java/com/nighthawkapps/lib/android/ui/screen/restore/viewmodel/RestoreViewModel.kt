@file:Suppress("MagicNumber")

package com.nighthawkapps.lib.android.ui.screen.restore.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.nighthawkapps.lib.android.ui.common.ANDROID_STATE_FLOW_TIMEOUT
import com.nighthawkapps.lib.android.ui.screen.restore.model.RestoreStage
import com.nighthawkapps.lib.android.ui.screen.restore.model.SeedPhraseValidation
import com.nighthawkapps.lib.android.ui.screen.restore.state.RestoreState
import com.nighthawkapps.lib.android.ui.screen.restore.state.WordList
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.validateDarkfiMnemonic
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RestoreViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    val restoreState: RestoreState =
        run {
            val initialValue =
                if (savedStateHandle.contains(KEY_STAGE)) {
                    savedStateHandle.get<RestoreStage>(KEY_STAGE)
                } else {
                    null
                }

            if (null == initialValue) {
                RestoreState()
            } else {
                RestoreState(initialValue)
            }
        }

    val userWordList: WordList =
        run {
            val initialValue =
                if (savedStateHandle.contains(KEY_WORD_LIST)) {
                    savedStateHandle.get<ArrayList<String>>(KEY_WORD_LIST)
                } else {
                    null
                }

            if (null == initialValue) {
                WordList()
            } else {
                WordList(initialValue)
            }
        }

    val userBirthdayHeight: MutableStateFlow<Long?> =
        run {
            val initialValue: Long? = savedStateHandle.get<Long>(KEY_BIRTHDAY_HEIGHT)
            MutableStateFlow(initialValue)
        }

    val completeWordList =
        flow {
            val loaded =
                withContext(Dispatchers.IO) {
                    DarkfiEnglishWordListForRestore.words(getApplication<Application>().assets)
                }
            emit(CompleteWordSetState.Loaded(loaded.toPersistentSet()))
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
            CompleteWordSetState.Loading,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val seedPhraseValidation: StateFlow<SeedPhraseValidation?> =
        userWordList.current
            .flatMapLatest { words ->
                flow {
                    if (words.isEmpty()) {
                        emit(null)
                    } else if (words.size != 22) {
                        emit(SeedPhraseValidation.BadCount)
                    } else if (validateDarkfiMnemonic(words)) {
                        emit(SeedPhraseValidation.Valid(words))
                    } else {
                        emit(SeedPhraseValidation.FailedChecksum)
                    }
                }
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT.inWholeMilliseconds),
                null,
            )

    init {
        viewModelScope.launch {
            userWordList.current.collect {
                savedStateHandle[KEY_WORD_LIST] = ArrayList(it)
            }
        }
        viewModelScope.launch {
            userBirthdayHeight.collect {
                savedStateHandle[KEY_BIRTHDAY_HEIGHT] = it
            }
        }
    }

    companion object {
        private const val KEY_STAGE = "stage" // $NON-NLS

        private const val KEY_WORD_LIST = "word_list" // $NON-NLS

        private const val KEY_BIRTHDAY_HEIGHT = "birthday_height" // $NON-NLS
    }
}

sealed class CompleteWordSetState {
    object Loading : CompleteWordSetState()

    data class Loaded(
        val list: ImmutableSet<String>
    ) : CompleteWordSetState()
}

/** Loads BIP-39 English words once (bundled by darkfi-android-sdk assets). */
private object DarkfiEnglishWordListForRestore {
    private var cached: Set<String>? = null

    fun words(assetManager: android.content.res.AssetManager): Set<String> {
        cached?.let { return it }
        val loaded =
            assetManager.open("bip39/english.txt").bufferedReader().useLines { seq ->
                seq.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            }
        require(loaded.size == 2048) { "BIP39 english.txt must contain 2048 words, got ${loaded.size}" }
        cached = loaded
        return loaded
    }
}
