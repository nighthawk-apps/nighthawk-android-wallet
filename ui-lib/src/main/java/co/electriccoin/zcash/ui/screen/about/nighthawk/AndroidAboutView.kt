package co.electriccoin.zcash.ui.screen.about.nighthawk

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.common.PRIVACY_POLICY_LINK
import co.electriccoin.zcash.ui.common.VIEW_SOURCE_URL
import co.electriccoin.zcash.ui.common.onLaunchUrl
import co.electriccoin.zcash.ui.screen.about.nighthawk.view.AboutView
import co.electriccoin.zcash.ui.screen.about.nighthawk.view.LicencesView
import co.electriccoin.zcash.ui.screen.home.viewmodel.HomeViewModel

@Composable
internal fun MainActivity.AndroidAboutView(onBack: () -> Unit) {
    WrapAboutView(activity = this, onBack = onBack)
}

@Composable
internal fun WrapAboutView(activity: ComponentActivity, onBack: () -> Unit) {
    val homeViewModel by activity.viewModels<HomeViewModel>()
    val onViewLicences = remember {
        mutableStateOf(false)
    }

    BackHandler(onViewLicences.value) {
        onViewLicences.value = false
    }

    DisposableEffect(key1 = Unit) {
        val previousVisibility = homeViewModel.isBottomNavBarVisible.value
        homeViewModel.onBottomNavBarVisibilityChanged(show = false)
        onDispose {
            homeViewModel.onBottomNavBarVisibilityChanged(show = previousVisibility)
        }
    }
    AboutView(
        onBack = {
            if (onViewLicences.value) {
                onViewLicences.value = false
            } else {
                onBack()
            }
        },
        onViewSource = {
            activity.onLaunchUrl(VIEW_SOURCE_URL)
        },
        onTermAndCondition = {
            activity.onLaunchUrl(PRIVACY_POLICY_LINK)
        },
        onViewLicence = {
            onViewLicences.value = true
        }
    )
    if (onViewLicences.value) {
        LicencesView()
    }
}
