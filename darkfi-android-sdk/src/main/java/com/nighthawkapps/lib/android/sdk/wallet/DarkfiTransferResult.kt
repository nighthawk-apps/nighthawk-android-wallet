package com.nighthawkapps.lib.android.sdk.wallet

/** Outcome of a native transfer build / fee estimate / broadcast. */
sealed interface DarkfiTransferResult<out T> {
    data class Success<T>(
        val value: T
    ) : DarkfiTransferResult<T>

    data class Failure(
        val message: String,
        val cause: Throwable? = null
    ) : DarkfiTransferResult<Nothing>
}
