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
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.nighthawkapps.lib.android.global.AppWalletCoordinator
import com.nighthawkapps.lib.android.sdk.mesh.MeshCoordinator
import com.nighthawkapps.lib.android.sdk.net.PrivacyNetwork
import com.nighthawkapps.lib.android.sdk.nym.AppNymCoordinator
import com.nighthawkapps.lib.android.sdk.tor.AppTorCoordinator
import com.nighthawkapps.lib.android.sdk.tor.TorBootstrapUiState
import com.nighthawkapps.lib.android.spackle.Twig
import com.nighthawkapps.lib.android.ui.common.BindCompLocalProvider
import com.nighthawkapps.lib.android.ui.common.NighthawkBrandingHeader
import com.nighthawkapps.lib.android.ui.common.ShortcutAction
import com.nighthawkapps.lib.android.ui.configuration.RemoteConfig
import com.nighthawkapps.lib.android.ui.design.component.Body
import com.nighthawkapps.lib.android.ui.design.component.BodySmall
import com.nighthawkapps.lib.android.ui.design.component.ConfigurationOverride
import com.nighthawkapps.lib.android.ui.design.component.Override
import com.nighthawkapps.lib.android.ui.design.theme.AppThemeVariant
import com.nighthawkapps.lib.android.ui.design.theme.WalletTheme
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceKeys
import com.nighthawkapps.lib.android.ui.preference.StandardPreferenceSingleton
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.HomeViewModel
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.SecretState
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.WalletViewModel
import com.nighthawkapps.lib.android.ui.screen.onboarding.nighthawk.WrapOnBoarding
import com.nighthawkapps.lib.android.ui.screen.onboarding.nighthawk.view.OnboardingCarousel
import com.nighthawkapps.lib.android.ui.screen.onboarding.nighthawk.view.SeedBackup
import com.nighthawkapps.lib.android.ui.screen.pin.AndroidPin
import com.nighthawkapps.lib.android.ui.screen.warning.WrapNotEnoughSpace
import com.nighthawkapps.lib.android.ui.screen.warning.viewmodel.StorageCheckViewModel
import com.nighthawkapps.lib.android.ui.splash.NymSplashProgress
import com.nighthawkapps.lib.android.work.WorkIds
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import android.graphics.Color as AndroidColor

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
        // Required at targetSdk 35+ (Android 17 still enforces it). Without
        // this + safeDrawingPadding, chrome (PIN pad, CTAs, nav) draws under
        // system bars. API 36 removed the edge-to-edge opt-out.
        enableEdgeToEdge()

        handleIntentData()

        configureSplashScreenKeepCondition(splashScreen)
        attachSplashBuiltOnDarkFiTagline()
        attachSplashAppVersion()
        attachSplashTorStatus()

        setupUiContent()

        monitorForBackgroundSync()
        restoreMeshOnResume()
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
            val bottomPx = (resources.displayMetrics.density * 48).toInt()
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
     * App version at bottom-center of the Android 12+ splash host (N-U1).
     */
    private fun attachSplashAppVersion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }
        window.decorView.post {
            val splashHost = findSplashScreenView(window.decorView) ?: return@post
            if (splashHost.findViewById<TextView>(R.id.splash_app_version) != null) {
                return@post
            }
            val versionName =
                runCatching {
                    packageManager.getPackageInfo(packageName, 0).versionName
                }.getOrNull()
                    .orEmpty()
                    .ifBlank { return@post }
            val versionView =
                TextView(this).apply {
                    id = R.id.splash_app_version
                    text = getString(R.string.splash_app_version, versionName)
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.splash_tagline_text))
                    textSize = 12f
                    letterSpacing = 0.02f
                    includeFontPadding = false
                }
            val bottomPx = (resources.displayMetrics.density * 24).toInt()
            val lp =
                FrameLayout
                    .LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                    ).apply {
                        bottomMargin = bottomPx
                    }
            splashHost.addView(versionView, lp)
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
                    val bottomPx = (resources.displayMetrics.density * 72).toInt()
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

            fun applyState(splash: SplashNetworkState) {
                val label = splashLabelRes(splash)?.let(::getString)
                if (label == null) {
                    statusView.visibility = View.GONE
                } else {
                    statusView.visibility = View.VISIBLE
                    statusView.text = label
                }
            }
            applyState(
                SplashNetworkState.of(
                    AppNymCoordinator.bootstrapState.value,
                    AppTorCoordinator.bootstrapState.value,
                ),
            )
            lifecycleScope.launch {
                combine(
                    AppNymCoordinator.bootstrapState,
                    AppTorCoordinator.bootstrapState,
                    SplashNetworkState::of,
                ).collectLatest { applyState(it) }
            }
        }
    }

    @Composable
    private fun SplashVersionFooter() {
        val context = LocalContext.current
        val versionName =
            remember {
                runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull().orEmpty()
            }
        if (versionName.isBlank()) {
            return
        }
        Text(
            text = stringResource(R.string.splash_app_version, versionName),
            color = colorResource(R.color.splash_tagline_text),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    /** Bootstrap state of the network the splash waits on: Nym when it carries the wallet, else Tor. */
    private data class SplashNetworkState(
        val state: TorBootstrapUiState,
        val isNym: Boolean,
    ) {
        val isPending: Boolean
            get() =
                state == TorBootstrapUiState.Bootstrapping ||
                    state == TorBootstrapUiState.Idle ||
                    state == TorBootstrapUiState.Failed

        companion object {
            fun of(
                nym: TorBootstrapUiState,
                tor: TorBootstrapUiState,
            ): SplashNetworkState =
                if (nym != TorBootstrapUiState.Disabled) {
                    SplashNetworkState(nym, isNym = true)
                } else {
                    SplashNetworkState(tor, isNym = false)
                }
        }
    }

    private fun splashLabelRes(splash: SplashNetworkState): Int? =
        when (splash.state) {
            TorBootstrapUiState.Bootstrapping,
            TorBootstrapUiState.Idle,
            -> if (splash.isNym) R.string.splash_nym_bootstrapping else R.string.splash_tor_bootstrapping

            TorBootstrapUiState.Ready -> if (splash.isNym) R.string.splash_nym_ready else R.string.splash_tor_ready

            TorBootstrapUiState.Failed -> if (splash.isNym) R.string.splash_nym_failed else R.string.splash_tor_failed

            TorBootstrapUiState.Disabled -> null
        }

    @Composable
    private fun SplashConnectingScreen(splash: SplashNetworkState) {
        val context = LocalContext.current
        val label = splashLabelRes(splash)?.let { stringResource(it) }
        val nymStartedAt by AppNymCoordinator.bootstrapStartedAtMs.collectAsStateWithLifecycle()
        var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(splash.isNym, splash.state) {
            while (splash.isNym && splash.state == TorBootstrapUiState.Bootstrapping) {
                nowMs = System.currentTimeMillis()
                delay(SPLASH_TICK_MS)
            }
        }
        val elapsedSec = NymSplashProgress.elapsedSeconds(nymStartedAt, nowMs)
        val failed = splash.state == TorBootstrapUiState.Failed
        val nymStuck = splash.isNym && !failed && NymSplashProgress.isStuck(elapsedSec * 1_000L)
        val showDisable =
            if (splash.isNym) {
                splash.isPending && NymSplashProgress.showContinueWithout(elapsedSec * 1_000L, failed)
            } else {
                splash.isPending
            }
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                NighthawkBrandingHeader(bottomSpacing = 10.dp)
                Body(
                    text = stringResource(R.string.splash_subtitle),
                    textAlign = TextAlign.Center,
                )
                if (label != null) {
                    BodySmall(
                        text = label,
                        textAlign = TextAlign.Center,
                        color = colorResource(R.color.splash_tagline_text),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                if (splash.isNym && splash.state != TorBootstrapUiState.Disabled && !failed) {
                    val ready = splash.state == TorBootstrapUiState.Ready
                    LinearProgressIndicator(
                        progress = { NymSplashProgress.fraction(elapsedSec * 1_000L, ready) },
                        modifier =
                            Modifier
                                .padding(top = 12.dp)
                                .width(220.dp),
                    )
                    if (!ready) {
                        BodySmall(
                            text = stringResource(R.string.splash_nym_elapsed, elapsedSec),
                            textAlign = TextAlign.Center,
                            color = colorResource(R.color.splash_tagline_text),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    if (nymStuck) {
                        BodySmall(
                            text = stringResource(R.string.splash_nym_slow),
                            textAlign = TextAlign.Center,
                            color = colorResource(R.color.splash_tagline_text),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                if (splash.state == TorBootstrapUiState.Failed) {
                    TextButton(
                        onClick = {
                            if (splash.isNym) {
                                AppNymCoordinator.warmStartIfEnabled(context)
                            } else {
                                AppTorCoordinator.warmStartIfEnabled(context)
                            }
                        },
                    ) {
                        Text(stringResource(R.string.splash_tor_retry))
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp, start = 24.dp, end = 24.dp),
            ) {
                if (showDisable) {
                    TextButton(
                        onClick = {
                            if (splash.isNym) {
                                AppNymCoordinator.disableFromSplash(context)
                                lifecycleScope.launch {
                                    AppNymCoordinator.applyPrivacyNetwork(
                                        context,
                                        PrivacyNetwork.Direct,
                                        AppWalletCoordinator.get(context),
                                    )
                                }
                            } else {
                                AppTorCoordinator.disableTorFromSplash(context)
                            }
                        },
                    ) {
                        Text(
                            stringResource(
                                if (splash.isNym) {
                                    R.string.splash_nym_continue_without
                                } else {
                                    R.string.splash_tor_continue_without
                                },
                            ),
                        )
                    }
                    Text(
                        text =
                            stringResource(
                                if (splash.isNym) {
                                    R.string.splash_nym_continue_without_hint
                                } else {
                                    R.string.splash_tor_continue_without_hint
                                },
                            ),
                        color = colorResource(R.color.splash_tagline_text),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                SplashVersionFooter()
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
                val themeVariant = homeViewModel.appThemeVariant.collectAsStateWithLifecycle().value
                val lightChrome =
                    themeVariant == AppThemeVariant.PAPER || themeVariant == AppThemeVariant.LIGHT
                SideEffect {
                    enableEdgeToEdge(
                        statusBarStyle =
                            if (lightChrome) {
                                SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                            } else {
                                SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                            },
                        navigationBarStyle =
                            if (lightChrome) {
                                SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
                            } else {
                                SystemBarStyle.dark(AndroidColor.TRANSPARENT)
                            },
                    )
                }
                WalletTheme(themeVariant = themeVariant) {
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .safeDrawingPadding(),
                        color = MaterialTheme.colorScheme.background,
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
        val nymState by AppNymCoordinator.bootstrapState.collectAsStateWithLifecycle()
        val splash = SplashNetworkState.of(nymState, torState)
        val onboardingPrefs =
            remember {
                getSharedPreferences("nighthawk_onboarding", MODE_PRIVATE)
            }
        var onboardingDone by remember {
            mutableStateOf(onboardingPrefs.getBoolean("has_completed_onboarding", false))
        }

        // Note this condition needs to be kept in sync with the condition in setupSplashScreen()
        val waitingOnWallet = null == configuration || secretState == SecretState.Loading
        // Hold GetStarted (None) and Home (Ready after onboarding) until Tor is ready
        // or the user continues without it. Seed backup and the post-backup carousel
        // stay interactive — Create Wallet used to vanish behind Arti bootstrap.
        val holdForTor =
            (
                secretState == SecretState.None ||
                    (secretState is SecretState.Ready && onboardingDone)
            ) &&
                splash.isPending
        if (waitingOnWallet) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Column(modifier = Modifier.padding(bottom = 24.dp)) {
                    SplashVersionFooter()
                }
            }
        } else if (holdForTor) {
            SplashConnectingScreen(splash)
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
                        if (!onboardingDone) {
                            OnboardingCarousel(
                                onComplete = {
                                    onboardingPrefs.edit().putBoolean("has_completed_onboarding", true).apply()
                                    onboardingDone = true
                                }
                            )
                        } else {
                            NavigationMainContent()
                        }
                    }
                }
            }
        }
    }

    private fun restoreMeshOnResume() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                MeshCoordinator.bind(this@MainActivity)
                val prefs = StandardPreferenceSingleton.getInstance(this@MainActivity)
                val meshOn = StandardPreferenceKeys.IS_NIGHTHAWK_MESH_ENABLED.getValue(prefs)
                val alwaysOn = StandardPreferenceKeys.IS_NIGHTHAWK_MESH_ALWAYS_ON.getValue(prefs)
                MeshCoordinator.setAlwaysOn(alwaysOn)
                MeshCoordinator.setGatewayOptIn(false)
                if (meshOn) {
                    MeshCoordinator.restoreOnResume(this@MainActivity)
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

        private const val SPLASH_TICK_MS = 500L
    }
}
