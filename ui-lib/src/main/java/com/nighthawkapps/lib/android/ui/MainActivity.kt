package com.nighthawkapps.lib.android.ui

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.window.SplashScreenView
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import com.nighthawkapps.lib.android.spackle.Twig
import com.nighthawkapps.lib.android.ui.common.BindCompLocalProvider
import com.nighthawkapps.lib.android.ui.common.ShortcutAction
import com.nighthawkapps.lib.android.ui.configuration.RemoteConfig
import com.nighthawkapps.lib.android.ui.design.component.ConfigurationOverride
import com.nighthawkapps.lib.android.ui.design.component.Override
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.HomeViewModel
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.SecretState
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel
import com.nighthawkapps.lib.android.ui.screen.onboarding.nighthawk.WrapOnBoarding
import com.nighthawkapps.lib.android.ui.screen.onboarding.nighthawk.view.SeedBackup
import com.nighthawkapps.lib.android.ui.screen.pin.AndroidPin
import com.nighthawkapps.lib.android.ui.screen.warning.WrapNotEnoughSpace
import com.nighthawkapps.lib.android.ui.screen.warning.viewmodel.StorageCheckViewModel
import com.nighthawkapps.lib.android.work.WorkIds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class MainActivity : FragmentActivity() {
    private val homeViewModel by viewModels<HomeViewModel>()

    val walletViewModel by viewModels<WalletViewModel>()

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val storageCheckViewModel by viewModels<StorageCheckViewModel>()

    lateinit var navControllerForTesting: NavHostController

    val configurationOverrideFlow = MutableStateFlow<ConfigurationOverride?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        handleIntentData()

        configureSplashScreenKeepCondition(splashScreen)
        attachSplashBuiltOnDarkFiTagline()

        setupUiContent()

        monitorForBackgroundSync()
    }

    private fun handleIntentData() {
        homeViewModel.intentDataUriForDeepLink = intent?.data
        homeViewModel.shortcutAction = ShortcutAction.getShortcutAction(intent?.getStringExtra(ShortcutAction.KEY_SHORT_CUT_CLICK))
    }

    private fun configureSplashScreenKeepCondition(splashScreen: SplashScreen) {
        val start = SystemClock.elapsedRealtime().milliseconds

        splashScreen.setKeepOnScreenCondition {
            if (SPLASH_SCREEN_DELAY > Duration.ZERO) {
                val now = SystemClock.elapsedRealtime().milliseconds

                // This delay is for debug purposes only; do not enable for production usage.
                if (now - start < SPLASH_SCREEN_DELAY) {
                    return@setKeepOnScreenCondition true
                }
            }

            // Note this condition needs to be kept in sync with the condition in MainContent()
            homeViewModel.configurationFlow.value == null || SecretState.Loading == walletViewModel.secretState.value
        }
    }

    /**
     * Adds localized tagline on the Android 12+ splash host; legacy splash has icon-only branding.
     */
    private fun attachSplashBuiltOnDarkFiTagline() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }
        window.decorView.post {
            val splashHost = findSplashScreenView(window.decorView) ?: return@post
            if (splashHost.getTag(R.id.splash_tagline_attached) != null) {
                return@post
            }
            splashHost.setTag(R.id.splash_tagline_attached, true)
            val tagline =
                TextView(this).apply {
                    text = getString(R.string.splash_built_on_darkfi)
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.splash_tagline_text))
                    textSize = 13f
                    letterSpacing = 0.03f
                    includeFontPadding = false
                }
            val bottomPx = (resources.displayMetrics.density * 36).toInt()
            val lp =
                FrameLayout
                    .LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                    ).apply {
                        bottomMargin = bottomPx
                    }
            splashHost.addView(tagline, lp)
        }
    }

    @SuppressLint("NewApi")
    private fun findSplashScreenView(view: View): SplashScreenView? {
        if (view is SplashScreenView) {
            return view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findSplashScreenView(view.getChildAt(i))?.let {
                    return it
                }
            }
        }
        return null
    }

    private fun setupUiContent() {
        setContent {
            Override(configurationOverrideFlow) {
                WalletTheme(themeVariant = homeViewModel.appThemeVariant.collectAsStateWithLifecycle().value) {
                    Surface(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                    ) {
                        BindCompLocalProvider {
                            val isEnoughSpace by storageCheckViewModel.isEnoughSpace.collectAsStateWithLifecycle()
                            if (isEnoughSpace == false) {
                                WrapNotEnoughSpace()
                            } else {
                                MainContent()
                            }
                        }
                    }
                }
            }

            // Force collection to improve performance; sync can start happening while
            // the user is going through the backup flow.
            walletViewModel.synchronizer.collectAsStateWithLifecycle()
        }
    }

    @Composable
    private fun MainContent() {
        val configuration = homeViewModel.configurationFlow.collectAsStateWithLifecycle().value
        val secretState = walletViewModel.secretState.collectAsStateWithLifecycle().value

        // Note this condition needs to be kept in sync with the condition in setupSplashScreen()
        if (null == configuration || secretState == SecretState.Loading) {
            // For now, keep displaying splash screen using condition above.
            // In the future, we might consider displaying something different here.
        } else {
            // Note that the deeply nested child views will probably receive arguments derived from
            // the configuration.  The CompositionLocalProvider is helpful for passing the configuration
            // to the "platform" layer, which is where the arguments will be derived from.
            CompositionLocalProvider(RemoteConfig provides configuration) {
                when (secretState) {
                    SecretState.Loading -> {
                        // Splash / keep-on-screen condition covers Loading until wallet flow resolves
                    }

                    SecretState.NeedAuthentication -> {
                        AndroidPin(onBack = { this.finish() })
                    }

                    SecretState.None -> {
                        WrapOnBoarding()
                    }

                    is SecretState.NeedsBackup -> {
                        SeedBackup(
                            persistableWallet = secretState.persistableWallet,
                            onBackupComplete = { walletViewModel.persistBackupComplete() }
                        )
                    }

                    is SecretState.Ready -> {
                        Twig.info { "EndPoint ${secretState.persistableWallet.endpoint}" }
                        NavigationMainContent()
                    }
                }
            }
        }
    }

    private fun monitorForBackgroundSync() {
        val isEnableBackgroundSyncFlow =
            run {
                val homeViewModel by viewModels<HomeViewModel>()
                val isSecretReadyFlow = walletViewModel.secretState.map { it is SecretState.Ready }
                val isBackgroundSyncEnabledFlow = homeViewModel.isBackgroundSyncEnabled.filterNotNull()

                isSecretReadyFlow.combine(isBackgroundSyncEnabledFlow) { isSecretReady, isBackgroundSyncEnabled ->
                    isSecretReady && isBackgroundSyncEnabled
                }
            }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                isEnableBackgroundSyncFlow.collect { isEnableBackgroundSync ->
                    if (isEnableBackgroundSync) {
                        WorkIds.enableBackgroundSynchronization(application)
                    } else {
                        WorkIds.disableBackgroundSynchronization(application)
                    }
                }
            }
        }
    }

    companion object {
        @VisibleForTesting
        internal val SPLASH_SCREEN_DELAY = 0.seconds
    }
}
