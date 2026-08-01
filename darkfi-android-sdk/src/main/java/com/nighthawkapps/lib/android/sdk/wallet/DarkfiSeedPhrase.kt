package com.nighthawkapps.lib.android.sdk.wallet

/** DarkFi phrase constants used by onboarding / restore UI (22-word seed). */
object DarkfiSeedPhrase {
    const val WORD_COUNT: Int = 22

    const val DELIMITER: String = " "
}

/** Conservative restore-height bound until full-node integration lands (mock pipeline). */
object DarkfiChainBounds {
    const val MIN_WALLET_BIRTHDAY_HEIGHT: Long = 1L
}
