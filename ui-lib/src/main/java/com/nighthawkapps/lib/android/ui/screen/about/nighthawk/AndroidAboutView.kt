package com.nighthawkapps.lib.android.ui.screen.about.nighthawk

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.onLaunchUrl
import com.nighthawkapps.lib.android.ui.screen.about.nighthawk.view.AboutView
import com.nighthawkapps.lib.android.ui.screen.about.nighthawk.view.LicencesView
import com.nighthawkapps.lib.android.ui.screen.home.viewmodel.HomeViewModel

@Composable
internal fun MainActivity.AndroidAboutView(onBack: () -> Unit) {
    WrapAboutView(activity = this, onBack = onBack)
}

@Composable
internal fun WrapAboutView(
    activity: ComponentActivity,
    onBack: () -> Unit
) {
    val onViewLicences =
        remember {
            mutableStateOf(false)
        }

    BackHandler(onViewLicences.value) {
        onViewLicences.value = false
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
            activity.onLaunchUrl(activity.getString(R.string.ns_source_code_link))
        },
        onCredits = {
            activity.onLaunchUrl(activity.getString(R.string.ns_credits_link))
        },
        onTermAndCondition = {
            activity.onLaunchUrl(activity.getString(R.string.ns_privacy_policy_link))
        },
        onViewLicence = {
            onViewLicences.value = true
        }
    )
    if (onViewLicences.value) {
        LicencesView()
    }
}
