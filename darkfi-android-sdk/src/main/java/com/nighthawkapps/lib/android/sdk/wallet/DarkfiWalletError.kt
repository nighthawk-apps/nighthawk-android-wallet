package com.nighthawkapps.lib.android.sdk.wallet

sealed class DarkfiWalletError {
    abstract fun causeMessage(): String?

    abstract fun stackTraceSnippet(limit: Int = 250): String?

    data class Critical(
        val error: Throwable?
    ) : DarkfiWalletError() {
        override fun causeMessage(): String? = error?.localizedMessage

        override fun stackTraceSnippet(limit: Int): String? = error?.stackTraceToString()?.take(limit)
    }

    data class Processor(
        val error: Throwable?
    ) : DarkfiWalletError() {
        override fun causeMessage(): String? = error?.localizedMessage

        override fun stackTraceSnippet(limit: Int): String? = error?.stackTraceToString()?.take(limit)
    }

    data class Submission(
        val error: Throwable?
    ) : DarkfiWalletError() {
        override fun causeMessage(): String? = error?.localizedMessage

        override fun stackTraceSnippet(limit: Int): String? = error?.stackTraceToString()?.take(limit)
    }

    data class Setup(
        val error: Throwable?
    ) : DarkfiWalletError() {
        override fun causeMessage(): String? = error?.localizedMessage

        override fun stackTraceSnippet(limit: Int): String? = error?.stackTraceToString()?.take(limit)
    }

    data class Chain(
        val lowerHeight: Long,
        val upperHeight: Long
    ) : DarkfiWalletError() {
        override fun causeMessage(): String = "$lowerHeight .. $upperHeight"

        override fun stackTraceSnippet(limit: Int): String? = null
    }
}
