package com.nighthawkapps.lib.android.ui.screen.shield.model

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import com.nighthawkapps.lib.android.ui.R

sealed class ShieldUIState {
    object Loading : ShieldUIState()

    data class OnResult(
        val destination: ShieldUiDestination
    ) : ShieldUIState()
}

sealed interface ShieldUiDestination {
    object AutoShieldingInfo : ShieldUiDestination

    object ShieldFunds : ShieldUiDestination

    data class AutoShieldError(
        val message: String? = null
    ) : ShieldUiDestination
}

enum class ShieldingProcessState(
    @RawRes val animRes: Int,
    @StringRes val statusRes: Int
) {
    CREATING(R.raw.lottie_shielding, R.string.shielding),
    SUCCESS(R.raw.lottie_auto_shield_success, R.string.ns_success),
    FAILURE(R.raw.lottie_auto_shield_failed, R.string.ns_failed)
}
