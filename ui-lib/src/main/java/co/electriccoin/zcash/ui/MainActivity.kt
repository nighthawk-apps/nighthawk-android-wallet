package co.electriccoin.zcash.ui

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import cash.z.ecc.android.sdk.type.ZcashNetwork
import cash.z.ecc.sdk.fixture.SeedPhraseFixture
import cash.z.ecc.sdk.model.PersistableWallet
import cash.z.ecc.sdk.model.SeedPhrase
import cash.z.ecc.sdk.model.ZecRequest
import cash.z.ecc.sdk.send
import cash.z.ecc.sdk.type.fromResources
import co.electriccoin.zcash.spackle.EmulatorWtfUtil
import co.electriccoin.zcash.spackle.FirebaseTestLabUtil
import co.electriccoin.zcash.ui.design.compat.FontCompat
import co.electriccoin.zcash.ui.design.component.GradientSurface
import co.electriccoin.zcash.ui.design.theme.ZcashTheme
import co.electriccoin.zcash.ui.screen.about.WrapAbout
import co.electriccoin.zcash.ui.screen.backup.WrapBackup
import co.electriccoin.zcash.ui.screen.backup.copyToClipboard
import co.electriccoin.zcash.ui.screen.home.model.spendableBalance
import co.electriccoin.zcash.ui.screen.home.view.Home
import co.electriccoin.zcash.ui.screen.home.viewmodel.CheckUpdateViewModel
import co.electriccoin.zcash.ui.screen.home.viewmodel.SecretState
import co.electriccoin.zcash.ui.screen.home.viewmodel.WalletViewModel
import co.electriccoin.zcash.ui.screen.onboarding.view.Onboarding
import co.electriccoin.zcash.ui.screen.onboarding.viewmodel.OnboardingViewModel
import co.electriccoin.zcash.ui.screen.profile.WrapProfile
import co.electriccoin.zcash.ui.screen.request.view.Request
import co.electriccoin.zcash.ui.screen.restore.view.RestoreWallet
import co.electriccoin.zcash.ui.screen.restore.viewmodel.CompleteWordSetState
import co.electriccoin.zcash.ui.screen.restore.viewmodel.RestoreViewModel
import co.electriccoin.zcash.ui.screen.scan.WrapScan
import co.electriccoin.zcash.ui.screen.seed.view.Seed
import co.electriccoin.zcash.ui.screen.send.view.Send
import co.electriccoin.zcash.ui.screen.settings.view.Settings
import co.electriccoin.zcash.ui.screen.support.WrapSupport
import co.electriccoin.zcash.ui.screen.update.AppUpdateCheckerImp
import co.electriccoin.zcash.ui.screen.update.WrapUpdate
import co.electriccoin.zcash.ui.screen.update.model.UpdateState
import co.electriccoin.zcash.ui.screen.wallet_address.view.WalletAddresses
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Suppress("TooManyFunctions")
class MainActivity : ComponentActivity() {

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val walletViewModel by viewModels<WalletViewModel>()

    // TODO [#382]: https://github.com/zcash/secant-android-wallet/issues/382
    // TODO [#403]: https://github.com/zcash/secant-android-wallet/issues/403
    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    val checkUpdateViewModel by viewModels<CheckUpdateViewModel> {
        CheckUpdateViewModel.CheckUpdateViewModelFactory(
            application,
            AppUpdateCheckerImp.new()
        )
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    lateinit var navControllerForTesting: NavHostController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupSplashScreen()

        if (FontCompat.isFontPrefetchNeeded()) {
            lifecycleScope.launch {
                FontCompat.prefetchFontsLegacy(applicationContext)

                setupUiContent()
            }
        } else {
            setupUiContent()
        }
    }

    private fun setupSplashScreen() {
        val splashScreen = installSplashScreen()
        val start = SystemClock.elapsedRealtime().milliseconds

        splashScreen.setKeepOnScreenCondition {
            if (SPLASH_SCREEN_DELAY > Duration.ZERO) {
                val now = SystemClock.elapsedRealtime().milliseconds

                // This delay is for debug purposes only; do not enable for production usage.
                if (now - start < SPLASH_SCREEN_DELAY) {
                    return@setKeepOnScreenCondition true
                }
            }

            SecretState.Loading == walletViewModel.secretState.value
        }
    }

    private fun setupUiContent() {
        setContent {
            ZcashTheme {
                GradientSurface(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                ) {
                    when (val secretState = walletViewModel.secretState.collectAsState().value) {
                        SecretState.Loading -> {
                            // For now, keep displaying splash screen using condition above.
                            // In the future, we might consider displaying something different here.
                        }
                        SecretState.None -> {
                            WrapOnboarding()
                        }
                        is SecretState.NeedsBackup -> WrapBackup(
                            secretState.persistableWallet,
                            onBackupComplete = { walletViewModel.persistBackupComplete() }
                        )
                        is SecretState.Ready -> Navigation()
                    }
                }
            }
        }

        // Force collection to improve performance; sync can start happening while
        // the user is going through the backup flow. Don't use eager collection in the view model,
        // so that the collection is still tied to UI lifecycle.
        lifecycleScope.launch {
            walletViewModel.synchronizer.collect {
            }
        }
    }

    @Composable
    private fun WrapOnboarding() {
        val onboardingViewModel by viewModels<OnboardingViewModel>()

        // TODO [#383]: https://github.com/zcash/secant-android-wallet/issues/383
        if (!onboardingViewModel.isImporting.collectAsState().value) {
            Onboarding(
                onboardingState = onboardingViewModel.onboardingState,
                onImportWallet = {
                    // In the case of the app currently being messed with by the robo test runner on
                    // Firebase Test Lab or Google Play pre-launch report, we want to skip creating
                    // a new or restoring an existing wallet screens by persisting an existing wallet
                    // with a mock seed.
                    if (FirebaseTestLabUtil.isFirebaseTestLab(applicationContext)) {
                        persistExistingWalletWithSeedPhrase(SeedPhraseFixture.new())
                        return@Onboarding
                    }

                    onboardingViewModel.isImporting.value = true
                },
                onCreateWallet = {
                    if (FirebaseTestLabUtil.isFirebaseTestLab(applicationContext)) {
                        persistExistingWalletWithSeedPhrase(SeedPhraseFixture.new())
                        return@Onboarding
                    }

                    walletViewModel.persistNewWallet()
                }
            )

            reportFullyDrawn()
        } else {
            WrapRestore()
        }
    }

    @Composable
    private fun WrapRestore() {
        val onboardingViewModel by viewModels<OnboardingViewModel>()
        val restoreViewModel by viewModels<RestoreViewModel>()

        when (val completeWordList = restoreViewModel.completeWordList.collectAsState().value) {
            CompleteWordSetState.Loading -> {
                // Although it might perform IO, it should be relatively fast.
                // Consider whether to display indeterminate progress here.
                // Another option would be to go straight to the restore screen with autocomplete
                // disabled for a few milliseconds.  Users would probably never notice due to the
                // time it takes to re-orient on the new screen, unless users were doing this
                // on a daily basis and become very proficient at our UI.  The Therac-25 has
                // historical precedent on how that could cause problems.
            }
            is CompleteWordSetState.Loaded -> {
                RestoreWallet(
                    completeWordList.list,
                    restoreViewModel.userWordList,
                    onBack = { onboardingViewModel.isImporting.value = false },
                    paste = {
                        val clipboardManager = getSystemService(ClipboardManager::class.java)
                        return@RestoreWallet clipboardManager?.primaryClip?.toString()
                    },
                    onFinished = {
                        persistExistingWalletWithSeedPhrase(
                            SeedPhrase(restoreViewModel.userWordList.current.value)
                        )
                    }
                )
            }
        }
    }

    /**
     * Persists existing wallet together with the backup complete flag to disk. Be aware of that, it
     * triggers navigation changes, as we observe the WalletViewModel.secretState.
     *
     * Write the backup complete flag first, then the seed phrase. That avoids the UI flickering to
     * the backup screen. Assume if a user is restoring from a backup, then the user has a valid backup.
     *
     * @param seedPhrase to be persisted along with the wallet object
     */
    private fun persistExistingWalletWithSeedPhrase(seedPhrase: SeedPhrase) {
        walletViewModel.persistBackupComplete()

        val network = ZcashNetwork.fromResources(application)
        val restoredWallet = PersistableWallet(
            network,
            null,
            seedPhrase
        )
        walletViewModel.persistExistingWallet(restoredWallet)
    }

    @Suppress("LongMethod")
    @Composable
    @SuppressWarnings("LongMethod")
    private fun Navigation() {
        val navController = rememberNavController().also {
            // This suppress is necessary, as this is how we set up the nav controller for tests.
            @SuppressLint("RestrictedApi")
            navControllerForTesting = it
        }

        NavHost(navController = navController, startDestination = NAV_HOME) {
            composable(NAV_HOME) {
                WrapHome(
                    goScan = { navController.navigate(NAV_SCAN) },
                    goProfile = { navController.navigate(NAV_PROFILE) },
                    goSend = { navController.navigate(NAV_SEND) },
                    goRequest = { navController.navigate(NAV_REQUEST) }
                )
            }
            composable(NAV_PROFILE) {
                WrapProfile(
                    onBack = { navController.popBackStack() },
                    onAddressDetails = { navController.navigate(NAV_WALLET_ADDRESS_DETAILS) },
                    onAddressBook = { },
                    onSettings = { navController.navigate(NAV_SETTINGS) },
                    onCoinholderVote = { },
                    onSupport = { navController.navigate(NAV_SUPPORT) },
                    onAbout = { navController.navigate(NAV_ABOUT) }
                )
            }
            composable(NAV_WALLET_ADDRESS_DETAILS) {
                WrapWalletAddresses(
                    goBack = {
                        navController.popBackStack()
                    }
                )
            }
            composable(NAV_SETTINGS) {
                WrapSettings(
                    goBack = {
                        navController.popBackStack()
                    },
                    goWalletBackup = {
                        navController.navigate(NAV_SEED)
                    }
                )
            }
            composable(NAV_SEED) {
                WrapSeed(
                    goBack = {
                        navController.popBackStack()
                    }
                )
            }
            composable(NAV_REQUEST) {
                WrapRequest(goBack = { navController.popBackStack() })
            }
            composable(NAV_SEND) {
                WrapSend(goBack = { navController.popBackStack() })
            }
            composable(NAV_SUPPORT) {
                // Pop back stack won't be right if we deep link into support
                WrapSupport(goBack = { navController.popBackStack() })
            }
            composable(NAV_ABOUT) {
                WrapAbout(goBack = { navController.popBackStack() })
            }
            composable(NAV_SCAN) {
                WrapScan(goBack = { navController.popBackStack() })
            }
        }
    }

    @Composable
    private fun WrapHome(
        goScan: () -> Unit,
        goProfile: () -> Unit,
        goSend: () -> Unit,
        goRequest: () -> Unit
    ) {
        val walletSnapshot = walletViewModel.walletSnapshot.collectAsState().value
        if (null == walletSnapshot) {
            // Display loading indicator
        } else {
            val context = LocalContext.current

            // We might eventually want to check the debuggable property of the manifest instead
            // of relying on BuildConfig.
            val isDebugMenuEnabled = BuildConfig.DEBUG &&
                !FirebaseTestLabUtil.isFirebaseTestLab(context) &&
                !EmulatorWtfUtil.isEmulatorWtf(context)

            Home(
                walletSnapshot,
                walletViewModel.transactionSnapshot.collectAsState().value,
                goScan = goScan,
                goRequest = goRequest,
                goSend = goSend,
                goProfile = goProfile,
                isDebugMenuEnabled = isDebugMenuEnabled
            )

            reportFullyDrawn()

            WrapCheckForUpdate()
        }
    }

    @Composable
    private fun WrapCheckForUpdate() {
        // and then check for an app update asynchronously
        checkUpdateViewModel.checkForAppUpdate()
        val updateInfo = checkUpdateViewModel.updateInfo.collectAsState().value

        updateInfo?.let {
            if (it.appUpdateInfo != null && it.state == UpdateState.Prepared) {
                WrapUpdate(updateInfo)
            }
        }
    }

    @Composable
    private fun WrapWalletAddresses(
        goBack: () -> Unit,
    ) {
        val walletAddresses = walletViewModel.addresses.collectAsState().value
        if (null == walletAddresses) {
            // Display loading indicator
        } else {
            WalletAddresses(
                walletAddresses,
                goBack
            )
        }
    }

    @Composable
    private fun WrapSettings(
        goBack: () -> Unit,
        goWalletBackup: () -> Unit
    ) {
        val synchronizer = walletViewModel.synchronizer.collectAsState().value
        if (null == synchronizer) {
            // Display loading indicator
        } else {
            Settings(
                onBack = goBack,
                onBackupWallet = goWalletBackup,
                onRescanWallet = {
                    walletViewModel.rescanBlockchain()
                }, onWipeWallet = {
                    walletViewModel.wipeWallet()

                    // If wipe ever becomes an operation to also delete the seed, then we'll also need
                    // to do the following to clear any retained state from onboarding (only happens if
                    // occurring during same session as onboarding)
                    // onboardingViewModel.onboardingState.goToBeginning()
                    // onboardingViewModel.isImporting.value = false
                }
            )
        }
    }

    @Composable
    private fun WrapSeed(
        goBack: () -> Unit
    ) {
        val persistableWallet = run {
            val secretState = walletViewModel.secretState.collectAsState().value
            if (secretState is SecretState.Ready) {
                secretState.persistableWallet
            } else {
                null
            }
        }
        val synchronizer = walletViewModel.synchronizer.collectAsState().value
        if (null == synchronizer || null == persistableWallet) {
            // Display loading indicator
        } else {
            Seed(
                persistableWallet = persistableWallet,
                onBack = goBack,
                onCopyToClipboard = {
                    copyToClipboard(applicationContext, persistableWallet)
                }
            )
        }
    }

    @Composable
    private fun WrapRequest(
        goBack: () -> Unit
    ) {
        val walletAddresses = walletViewModel.addresses.collectAsState().value
        if (null == walletAddresses) {
            // Display loading indicator
        } else {
            Request(
                walletAddresses.unified,
                goBack = goBack,
                onCreateAndSend = {
                    val chooserIntent = Intent.createChooser(it.newShareIntent(applicationContext), null)

                    startActivity(chooserIntent)

                    goBack()
                },
            )
        }
    }

    @Composable
    private fun WrapSend(
        goBack: () -> Unit
    ) {
        val synchronizer = walletViewModel.synchronizer.collectAsState().value
        val spendableBalance = walletViewModel.walletSnapshot.collectAsState().value?.spendableBalance()
        val spendingKey = walletViewModel.spendingKey.collectAsState().value
        if (null == synchronizer || null == spendableBalance || null == spendingKey) {
            // Display loading indicator
        } else {
            Send(
                mySpendableBalance = spendableBalance,
                goBack = goBack,
                onCreateAndSend = {
                    synchronizer.send(spendingKey, it)

                    goBack()
                },
            )
        }
    }

    companion object {
        @VisibleForTesting
        internal val SPLASH_SCREEN_DELAY = 0.seconds

        @VisibleForTesting
        const val NAV_HOME = "home"

        @VisibleForTesting
        const val NAV_PROFILE = "profile"

        @VisibleForTesting
        const val NAV_WALLET_ADDRESS_DETAILS = "wallet_address_details"

        @VisibleForTesting
        const val NAV_SETTINGS = "settings"

        @VisibleForTesting
        const val NAV_SEED = "seed"

        @VisibleForTesting
        const val NAV_REQUEST = "request"

        @VisibleForTesting
        const val NAV_SEND = "send"

        @VisibleForTesting
        const val NAV_SUPPORT = "support"

        @VisibleForTesting
        const val NAV_ABOUT = "about"

        @VisibleForTesting
        const val NAV_SCAN = "scan"
    }
}

private fun ZecRequest.newShareIntent(context: Context) = runBlocking {
    Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.request_template_format, toUri()))
        type = "text/plain"
    }
}