package com.nighthawkapps.lib.android.ui.preference

import com.nighthawkapps.lib.android.preference.api.PreferenceProvider
import com.nighthawkapps.lib.android.preference.model.entry.PreferenceDefault
import com.nighthawkapps.lib.android.preference.model.entry.PreferenceKey
import com.nighthawkapps.lib.android.ui.screen.syncnotification.viewmodel.SyncNotificationViewModel

data class SyncIntervalOptionPreferenceDefault(
    override val key: PreferenceKey
) : PreferenceDefault<SyncNotificationViewModel.SyncIntervalOption> {
    override suspend fun getValue(preferenceProvider: PreferenceProvider): SyncNotificationViewModel.SyncIntervalOption =
        preferenceProvider.getString(key)?.let { SyncNotificationViewModel.SyncIntervalOption.getSyncIntervalByText(it) }
            ?: SyncNotificationViewModel.SyncIntervalOption.OFF

    override suspend fun putValue(
        preferenceProvider: PreferenceProvider,
        newValue: SyncNotificationViewModel.SyncIntervalOption
    ) {
        preferenceProvider.putString(key, newValue.text)
    }
}
