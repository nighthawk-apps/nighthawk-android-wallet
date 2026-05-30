package com.nighthawkapps.lib.android.ui.screen.send.nighthawk.model

import androidx.annotation.RawRes
import androidx.annotation.StringRes
import com.nighthawkapps.lib.android.ui.R

sealed class SendConfirmationState(
    @StringRes val titleResId: Int,
    @RawRes val animRes: Int
) {
    data object Sending : SendConfirmationState(R.string.ns_sending, R.raw.lottie_sending)

    data object Failed : SendConfirmationState(R.string.ns_failed, R.raw.lottie_send_failure)

    data object Success : SendConfirmationState(R.string.ns_success, R.raw.lottie_send_success)
}
