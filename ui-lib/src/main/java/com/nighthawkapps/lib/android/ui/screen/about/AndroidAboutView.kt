@file:Suppress("ktlint:standard:filename")

package com.nighthawkapps.lib.android.ui.screen.about

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.nighthawkapps.lib.android.configuration.AndroidConfigurationFactory
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.common.VersionInfo
import com.nighthawkapps.lib.android.ui.screen.about.view.About
import com.nighthawkapps.lib.android.ui.screen.support.model.ConfigInfo

@Composable
internal fun MainActivity.WrapAbout(goBack: () -> Unit) {
    WrapAbout(this, goBack)
}

@Composable
internal fun WrapAbout(
    activity: ComponentActivity,
    goBack: () -> Unit
) {
    val configurationProvider = AndroidConfigurationFactory.getInstance(activity.applicationContext)

    About(VersionInfo.new(activity), ConfigInfo.new(configurationProvider), goBack)

    // Allows an implicit way to force configuration refresh by simply visiting the About screen
    LaunchedEffect(key1 = true) {
        AndroidConfigurationFactory.getInstance(activity.applicationContext).hintToRefresh()
    }
}
