@file:Suppress("UnusedPrivateProperty")

package com.nighthawkapps.lib.android.ui.screen.onboarding.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle

class OnboardingViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    val isImporting = savedStateHandle.getStateFlow(KEY_IS_IMPORTING, false)

    fun setIsImporting(isImporting: Boolean) {
        savedStateHandle[KEY_IS_IMPORTING] = isImporting
    }

    companion object {
        private const val KEY_STAGE = "stage" // $NON-NLS
        private const val KEY_IS_IMPORTING = "is_importing" // $NON-NLS
    }
}
