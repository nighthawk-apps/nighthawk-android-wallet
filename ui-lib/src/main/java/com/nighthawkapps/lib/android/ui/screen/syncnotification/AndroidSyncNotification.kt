package com.nighthawkapps.lib.android.ui.screen.syncnotification

import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.common.showMessage
import com.nighthawkapps.lib.android.ui.screen.scan.util.SettingsUtil
import com.nighthawkapps.lib.android.ui.screen.syncnotification.view.SyncNotification
import com.nighthawkapps.lib.android.ui.screen.syncnotification.viewmodel.SyncNotificationViewModel

@Composable
internal fun MainActivity.AndroidSyncNotification(onBack: () -> Unit) {
    WrapSyncNotification(activity = this, onBack = onBack)
}

@Composable
internal fun WrapSyncNotification(
    activity: ComponentActivity,
    onBack: () -> Unit
) {
    val syncNotificationViewModel by activity.viewModels<SyncNotificationViewModel>()

    val syncIntervalOption = syncNotificationViewModel.syncIntervalOption.collectAsStateWithLifecycle().value

    if (syncIntervalOption != null) {
        SyncNotification(
            selectedSyncOption = syncIntervalOption,
            onBack = onBack,
            onSyncOptionSelected = syncNotificationViewModel::updateSyncIntervalOption,
            openSettings = {
                runCatching {
                    activity.startActivity(SettingsUtil.newSettingsIntent(activity.packageName))
                }.onFailure {
                    activity.showMessage(activity.getString(R.string.scan_settings_open_failed))
                }
            }
        )
    }
}
