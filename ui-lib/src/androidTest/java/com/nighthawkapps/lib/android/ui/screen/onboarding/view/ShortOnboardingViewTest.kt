package com.nighthawkapps.lib.android.ui.screen.onboarding.view

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.filters.MediumTest
import com.nighthawkapps.lib.android.test.UiTestPrerequisites
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.screen.onboarding.ShortOnboardingTestSetup
import com.nighthawkapps.lib.android.ui.test.getStringResource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ShortOnboardingViewTest : UiTestPrerequisites() {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun newTestSetup(): ShortOnboardingTestSetup =
        ShortOnboardingTestSetup(composeTestRule).apply {
            setDefaultContent()
        }

    @Test
    @MediumTest
    fun layout() {
        newTestSetup()

        composeTestRule.onNodeWithText(getStringResource(R.string.ns_restore_from_backup).uppercase()).also {
            it.assertExists()
            it.assertIsEnabled()
            it.assertHasClickAction()
        }

        composeTestRule.onNodeWithText(getStringResource(R.string.ns_create_wallet).uppercase()).also {
            it.assertExists()
            it.assertIsEnabled()
            it.assertHasClickAction()
        }
    }

    @Test
    @MediumTest
    fun click_create_wallet() {
        val testSetup = newTestSetup()

        val newWalletButton =
            composeTestRule.onNodeWithText(
                getStringResource(R.string.ns_create_wallet).uppercase()
            )
        newWalletButton.performClick()

        assertEquals(1, testSetup.getOnCreateWalletCallbackCount())
        assertEquals(0, testSetup.getOnImportWalletCallbackCount())
    }

    @Test
    @MediumTest
    fun click_import_wallet() {
        val testSetup = newTestSetup()

        val importWalletButton =
            composeTestRule.onNodeWithText(
                getStringResource(R.string.ns_restore_from_backup).uppercase()
            )
        importWalletButton.performClick()

        // After clicking "Restore from backup", a dialog should appear.
        // We need to click "Restore" in that dialog.
        composeTestRule.onNodeWithText(getStringResource(R.string.ns_restore_dialog_primary_action).uppercase()).performClick()

        assertEquals(1, testSetup.getOnImportWalletCallbackCount())
        assertEquals(0, testSetup.getOnCreateWalletCallbackCount())
    }
}
