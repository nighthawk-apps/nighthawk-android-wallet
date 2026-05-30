package com.nighthawkapps.lib.android.ui.screen.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.screen.onboarding.nighthawk.view.GetStarted
import java.util.concurrent.atomic.AtomicInteger

class ShortOnboardingTestSetup(
    private val composeTestRule: ComposeContentTestRule,
) {
    private val onCreateWalletCallbackCount = AtomicInteger(0)
    private val onImportWalletCallbackCount = AtomicInteger(0)

    fun getOnCreateWalletCallbackCount(): Int {
        composeTestRule.waitForIdle()
        return onCreateWalletCallbackCount.get()
    }

    fun getOnImportWalletCallbackCount(): Int {
        composeTestRule.waitForIdle()
        return onImportWalletCallbackCount.get()
    }

    @Composable
    @Suppress("TestFunctionName")
    fun DefaultContent() {
        WalletTheme {
            GetStarted(
                onCreateWallet = { onCreateWalletCallbackCount.incrementAndGet() },
                onRestore = { onImportWalletCallbackCount.incrementAndGet() },
                onReference = {}
            )
        }
    }

    fun setDefaultContent() {
        composeTestRule.setContent {
            DefaultContent()
        }
    }
}
