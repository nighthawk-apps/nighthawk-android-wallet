package co.electriccoin.zcash.ui.screen.backup.model

import co.electriccoin.zcash.spackle.model.Index
import co.electriccoin.zcash.spackle.model.Progress

sealed class BackupStage(internal val order: Int) {

    /*
     * While the backup/restore UX is mostly linear, there are a few branches such as during the
     * test and at the very end.
     *
     * We do not allow the user to back after completing the onboarding, because we don't want to
     * give users the option to delete their seed from Google Credential Manager (once that feature
     * is added).  Instead, users should go into the app settings to delete a vaulted credential.
     *
     * We made the final seed review screen separate from the original seed screen. Although some
     * code is duplicated, it makes each screen much easier to individually build/test and reduces
     * risk of bugs being introduced.
     */

    companion object

    object EducationOverview : BackupStage(EDUCATION_OVERVIEW_ORDER)
    object EducationRecoveryPhrase : BackupStage(EDUCATION_RECOVERY_PHRASE_ORDER)
    object Seed : BackupStage(SEED_ORDER)
    object Test : BackupStage(TEST_ORDER) {
        // To bypass the Failure state
        override fun getNext(): BackupStage {
            return values[COMPLETE_ORDER]
        }
    }

    object Failure : BackupStage(FAILURE_ORDER) {
        // To let user preview the seed again after test failure
        override fun getPrevious(): BackupStage {
            return values[SEED_ORDER]
        }
    }

    object Complete : BackupStage(COMPLETE_ORDER) {
        // To disable back navigation after successful test
        override fun hasPrevious(): Boolean {
            return false
        }

        override fun getPrevious(): BackupStage {
            error("Cannot go back once the onboarding is complete") // $NON-NLS-1$
        }
    }

    object ReviewSeed : BackupStage(REVIEW_SEED_ORDER)

    /**
     * @see getPrevious
     */
    open fun hasPrevious() = order > 0

    /**
     * @see getNext
     */
    open fun hasNext() = order < values.size - 1

    /**
     * @return Previous item in ordinal order.  Returns the first item when it cannot go further back.
     */
    open fun getPrevious() = values[maxOf(0, order - 1)]

    /**
     * @return Last item in ordinal order.  Returns the last item when it cannot go further forward.
     */
    open fun getNext() = values[minOf(values.size - 1, order + 1)]

    /**
     * @return Returns current progression through stages.
     */
    fun getProgress() = Progress(Index(order), Index(values.size - 1))
}

// Note: the indexes are used to manage progression through each stage
// so be careful if changing these
private const val EDUCATION_OVERVIEW_ORDER = 0
private const val EDUCATION_RECOVERY_PHRASE_ORDER = 1
private const val SEED_ORDER = 2
private const val TEST_ORDER = 3
private const val FAILURE_ORDER = 4
private const val COMPLETE_ORDER = 5
private const val REVIEW_SEED_ORDER = 6

private val sealedClassValues: List<BackupStage> = buildList<BackupStage>() {
    add(EDUCATION_OVERVIEW_ORDER, BackupStage.EducationOverview)
    add(EDUCATION_RECOVERY_PHRASE_ORDER, BackupStage.EducationRecoveryPhrase)
    add(SEED_ORDER, BackupStage.Seed)
    add(TEST_ORDER, BackupStage.Test)
    add(FAILURE_ORDER, BackupStage.Failure)
    add(COMPLETE_ORDER, BackupStage.Complete)
    add(REVIEW_SEED_ORDER, BackupStage.ReviewSeed)
}

// https://youtrack.jetbrains.com/issue/KT-8970/Object-is-uninitialized-null-when-accessed-from-static-context-ex.-companion-object-with-initialization-loop
val BackupStage.Companion.values: List<BackupStage>
    get() = sealedClassValues
