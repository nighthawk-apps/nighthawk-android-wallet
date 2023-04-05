package co.electriccoin.zcash.ui.screen.onboarding.nighthawk.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.screen.onboarding.nighthawk.navigation.NavigationTargets.GET_STARTED
import co.electriccoin.zcash.ui.screen.onboarding.nighthawk.navigation.NavigationTargets.RESTORE
import co.electriccoin.zcash.ui.screen.onboarding.nighthawk.navigation.NavigationTargets.SEED_BACKUP
import co.electriccoin.zcash.ui.screen.onboarding.nighthawk.view.GetStarted
import co.electriccoin.zcash.ui.screen.onboarding.nighthawk.view.SeedBackup

@Composable
internal fun MainActivity.NavigateOnboard() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = GET_STARTED) {
        composable(GET_STARTED) {
            GetStarted(
                goSeedBackUp = { navController.navigateJustOnce(SEED_BACKUP) },
                goRestore = { navController.navigateJustOnce(RESTORE) },
                onReference = {
                    onLaunchUrl(url = this@NavigateOnboard.getString(R.string.ns_privacy_policy_link))
                }
            )
        }

        composable(SEED_BACKUP) {
            SeedBackup(
                onBack = {},
                onExportAsPdf = {},
                onContinue = {}
            )
        }
    }
}

internal fun MainActivity.onLaunchUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (t: Throwable) {
        print("Warning: failed to open browser due to $t")
    }
}

private fun NavHostController.navigateJustOnce(
    route: String,
    navOptionsBuilder: (NavOptionsBuilder.() -> Unit)? = null
) {
    if (currentDestination?.route == route) {
        return
    }

    if (navOptionsBuilder != null) {
        navigate(route, navOptionsBuilder)
    } else {
        navigate(route)
    }
}

object NavigationTargets {
    const val GET_STARTED = "get_started"
    const val SEED_BACKUP = "seed_backup"
    const val RESTORE = "restore"
}