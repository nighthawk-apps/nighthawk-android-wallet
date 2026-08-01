package com.nighthawkapps.lib.android.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.filters.MediumTest
import com.nighthawkapps.lib.android.test.UiTestPrerequisites
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ScreenBrightnessTest : UiTestPrerequisites() {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @MediumTest
    fun acquireAndReleaseScreenBrightness() =
        runTest {
            val testSetup = TestSetup(composeTestRule)

            assertEquals(1, testSetup.getSecureBrightnessCount())

            testSetup.mutableScreenBrightnessFlag.update { false }
            composeTestRule.awaitIdle()
            assertEquals(0, testSetup.getSecureBrightnessCount())
        }

    private class TestSetup(
        composeTestRule: ComposeContentTestRule
    ) {
        val mutableScreenBrightnessFlag = MutableStateFlow(true)

        private val screenBrightness = ScreenBrightness()

        fun getSecureBrightnessCount() = screenBrightness.referenceCount.value

        init {
            runTest {
                composeTestRule.setContent {
                    CompositionLocalProvider(LocalScreenBrightness provides screenBrightness) {
                        WalletTheme {
                            val secureScreen by mutableScreenBrightnessFlag.collectAsState()

                            TestView(secureScreen)
                        }
                    }
                }
            }
        }

        @Composable
        private fun TestView(brightenScreen: Boolean) {
            if (brightenScreen) {
                BrightenScreen()
            }
        }
    }
}
