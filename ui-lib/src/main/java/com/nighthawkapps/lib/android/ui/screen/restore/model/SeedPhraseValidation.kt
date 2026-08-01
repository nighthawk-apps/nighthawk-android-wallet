package com.nighthawkapps.lib.android.ui.screen.restore.model

sealed class SeedPhraseValidation {
    data object BadCount : SeedPhraseValidation()

    data object BadWord : SeedPhraseValidation()

    data object FailedChecksum : SeedPhraseValidation()

    data class Valid(
        val words: List<String>
    ) : SeedPhraseValidation()
}
