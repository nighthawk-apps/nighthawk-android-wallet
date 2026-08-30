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
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import com.nighthawkapps.lib.android.sdk.tor.AppTorCoordinator
import com.nighthawkapps.lib.android.sdk.tor.TorBootstrapUiState
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
        // Required at targetSdk 35+: Android forces edge-to-edge and API 36
        // removes the opt-out. Without this + safeDrawingPadding, chrome (PIN
        // pad, CTAs, nav) draws under system bars.
        enableEdgeToEdge()

        handleIntentData()

        configureSplashScreenKeepCondition(splashScreen)
        attachSplashBuiltOnDarkFiTagline()
        attachSplashTorStatus()

        setupUiContent()

        monitorForBackgroundSync()
        // Keep periodic "open wallet to sync" reminders registered from saved preference.
        WorkIds.ensureSyncNotificationScheduled(application)
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

            // Note this condition needs to be kept in sync with the condition in MainContent().
            // Tor bootstrap is NOT kept here — Compose must stay interactive for “Continue without Tor”.
            homeViewModel.configurationFlow.value == null ||
                SecretState.Loading == walletViewModel.secretState.value
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

    /**
     * Shows Tor bootstrap progress on the Android 12+ splash (iOS splash parity).
     * Kept in sync with [AppTorCoordinator.bootstrapState].
     */
    private fun attachSplashTorStatus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }
        window.decorView.post {
            val splashHost = findSplashScreenView(window.decorView) ?: return@post
            val existing = splashHost.findViewById<TextView>(R.id.splash_tor_status)
            val statusView =
                existing ?: TextView(this).apply {
                    id = R.id.splash_tor_status
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.splash_tagline_text))
                    textSize = 12f
                    letterSpacing = 0.02f
                    includeFontPadding = false
                    visibility = View.GONE
                    val bottomPx = (resources.displayMetrics.density * 58).toInt()
                    val lp =
                        FrameLayout
                            .LayoutParams(
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                FrameLayout.LayoutParams.WRAP_CONTENT,
                                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                            ).apply {
                                bottomMargin = bottomPx
                            }
                    splashHost.addView(this, lp)
                }
            fun applyState(state: TorBootstrapUiState) {
                val label =
                    when (state) {
                        TorBootstrapUiState.Bootstrapping,
                        TorBootstrapUiState.Idle,
                        -> getString(R.string.splash_tor_bootstrapping)
                        TorBootstrapUiState.Ready -> getString(R.string.splash_tor_ready)
                        TorBootstrapUiState.Failed -> getString(R.string.splash_tor_failed)
                        TorBootstrapUiState.Disabled -> null
                    }
                if (label == null) {
                    statusView.visibility = View.GONE
                } else {
                    statusView.visibility = View.VISIBLE
                    statusView.text = label
                }
            }
            applyState(AppTorCoordinator.bootstrapState.value)
            lifecycleScope.launch {
                AppTorCoordinator.bootstrapState.collectLatest { applyState(it) }
            }
        }
    }

    @Composable
    private fun SplashTorStatusOverlay(torState: TorBootstrapUiState) {
        val context = LocalContext.current
        val label =
            when (torState) {
                TorBootstrapUiState.Bootstrapping,
                TorBootstrapUiState.Idle,
                -> stringResource(R.string.splash_tor_bootstrapping)
                TorBootstrapUiState.Ready -> stringResource(R.string.splash_tor_ready)
                TorBootstrapUiState.Failed -> stringResource(R.string.splash_tor_failed)
                TorBootstrapUiState.Disabled -> null
            } ?: return
        val showDisable =
            torState == TorBootstrapUiState.Bootstrapping ||
                torState == TorBootstrapUiState.Idle ||
                torState == TorBootstrapUiState.Failed
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 48.dp, start = 24.dp, end = 24.dp),
            ) {
                Text(
                    text = label,
                    color = colorResource(R.color.splash_tagline_text),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
                if (showDisable) {
                    TextButton(
                        onClick = { AppTorCoordinator.disableTorFromSplash(context) },
                    ) {
                        Text(stringResource(R.string.splash_tor_continue_without))
                    }
                    Text(
                        text = stringResource(R.string.splash_tor_continue_without_hint),
                        color = colorResource(R.color.splash_tagline_text),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
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
                            .safeDrawingPadding()
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

            // Collect so an existing (backed-up) wallet can start sync. New-wallet
            // persist does not open DarkfiWalletHandle until backup is complete.
            walletViewModel.synchronizer.collectAsStateWithLifecycle()
        }
    }

    @Composable
    private fun MainContent() {
        val configuration = homeViewModel.configurationFlow.collectAsStateWithLifecycle().value
        val secretState = walletViewModel.secretState.collectAsStateWithLifecycle().value

        val torState by AppTorCoordinator.bootstrapState.collectAsStateWithLifecycle()

        // Note this condition needs to be kept in sync with the condition in setupSplashScreen()
        val waitingOnWallet = null == configuration || secretState == SecretState.Loading
        // Tor splash only when opening a backed-up wallet. Onboarding and seed
        // backup must stay on screen — Create Wallet used to persist the seed
        // then vanish behind this overlay while Arti bootstrapped.
        val blockReadyWalletOnTor =
            secretState is SecretState.Ready &&
                (
                    torState == TorBootstrapUiState.Bootstrapping ||
                        torState == TorBootstrapUiState.Idle ||
                        torState == TorBootstrapUiState.Failed
                )
        if (waitingOnWallet) {
            // Splash keep-on-screen until configuration / secret state resolve.
        } else if (blockReadyWalletOnTor) {
            SplashTorStatusOverlay(torState)
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
