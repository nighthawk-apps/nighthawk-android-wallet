package com.nighthawkapps.lib.android.ui.screen.onboarding.nighthawk.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedBackupChallengeTest {
    private val sampleSeed =
        List(22) { index -> "word${index + 1}" }

    @Test
    fun pickSeedChallengeIndices_returnsThreeDistinctSortedIndices() {
        val picked = pickSeedChallengeIndices(sampleSeed.size)
        assertEquals(3, picked.size)
        assertEquals(picked.distinct().sorted(), picked)
        assertTrue(picked.all { it in sampleSeed.indices })
    }

    @Test
    fun isSeedChallengeSatisfied_rejectsMissingOrWrongWordsAndAcceptsTrimmedMatch() {
        val indices = listOf(0, 7, 21)
        assertFalse(isSeedChallengeSatisfied(sampleSeed, indices, emptyMap()))
        assertFalse(
            isSeedChallengeSatisfied(
                sampleSeed,
                indices,
                mapOf(0 to "word1", 7 to "wrong", 21 to "word22"),
            ),
        )
        assertTrue(
            isSeedChallengeSatisfied(
                sampleSeed,
                indices,
                mapOf(0 to " word1 ", 7 to "WORD8", 21 to "word22"),
            ),
        )
    }
}
