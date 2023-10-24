package co.electriccoin.zcash.ui.screen.settings.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import co.electriccoin.zcash.preference.model.entry.BooleanPreferenceDefault
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.common.ANDROID_STATE_FLOW_TIMEOUT
import co.electriccoin.zcash.ui.preference.StandardPreferenceKeys
import co.electriccoin.zcash.ui.preference.StandardPreferenceSingleton
import co.electriccoin.zcash.ui.screen.advancesetting.model.AvailableLogo
import co.electriccoin.zcash.ui.screen.advancesetting.model.OneLauncherAlias
import co.electriccoin.zcash.ui.screen.advancesetting.model.TwoLauncherAlias
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val mutex = Mutex()

    val isAnalyticsEnabled: StateFlow<Boolean?> =
        booleanStateFlow(StandardPreferenceKeys.IS_ANALYTICS_ENABLED)

    val isBackgroundSync: StateFlow<Boolean?> =
        booleanStateFlow(StandardPreferenceKeys.IS_BACKGROUND_SYNC_ENABLED)

    val isKeepScreenOnWhileSyncing: StateFlow<Boolean?> =
        booleanStateFlow(StandardPreferenceKeys.IS_KEEP_SCREEN_ON_DURING_SYNC)

    val isBanditAvailable = flow {
        val preferenceProvider = StandardPreferenceSingleton.getInstance(getApplication())
        emit(StandardPreferenceKeys.IS_BANDIT_AVAILABLE.getValue(preferenceProvider))
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
        initialValue = false
    )

    val preferredLogo = flow {
        val preferenceProvider = StandardPreferenceSingleton.getInstance(getApplication())
        emitAll(
            StandardPreferenceKeys.PREFERRED_LOGO.observe(preferenceProvider)
                .map {
                    AvailableLogo.getAvailableLogo(it.toIntOrNull())
                }
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT),
        AvailableLogo.DEFAULT
    )

    private fun booleanStateFlow(default: BooleanPreferenceDefault): StateFlow<Boolean?> =
        flow<Boolean?> {
            val preferenceProvider = StandardPreferenceSingleton.getInstance(getApplication())
            emitAll(default.observe(preferenceProvider))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(ANDROID_STATE_FLOW_TIMEOUT), null)

    fun setAnalyticsEnabled(enabled: Boolean) {
        setBooleanPreference(StandardPreferenceKeys.IS_ANALYTICS_ENABLED, enabled)
    }

    fun setBackgroundSyncEnabled(enabled: Boolean) {
        setBooleanPreference(StandardPreferenceKeys.IS_BACKGROUND_SYNC_ENABLED, enabled)
    }

    fun setKeepScreenOnWhileSyncing(enabled: Boolean) {
        setBooleanPreference(StandardPreferenceKeys.IS_KEEP_SCREEN_ON_DURING_SYNC, enabled)
    }

    fun setBanditStatus(enabled: Boolean) {
        setBooleanPreference(StandardPreferenceKeys.IS_BANDIT_AVAILABLE, enabled)
        if (enabled.not()) { // reset logo
            setPreferredLogo(availableLogo = AvailableLogo.DEFAULT)
        }
    }

    fun setPreferredLogo(availableLogo: AvailableLogo) {
        // save preference to local db
        viewModelScope.launch {
            val prefs = StandardPreferenceSingleton.getInstance(getApplication())
            mutex.withLock {
                StandardPreferenceKeys.PREFERRED_LOGO.putValue(prefs, "${availableLogo.logoNo}")
            }
        }

        // change app logo
        try {
            val packageManager = getApplication<Application>().packageManager
            val enableClassId: Class<*>
            val disableClassId: Class<*>
            when (availableLogo) {
                AvailableLogo.DEFAULT -> {
                    enableClassId = OneLauncherAlias::class.java
                    disableClassId = TwoLauncherAlias::class.java
                }

                AvailableLogo.LEGACY -> {
                    enableClassId = TwoLauncherAlias::class.java
                    disableClassId = OneLauncherAlias::class.java
                }
            }
            packageManager.setComponentEnabledSetting(
                ComponentName(getApplication(), enableClassId),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            packageManager.setComponentEnabledSetting(
                ComponentName(getApplication(), disableClassId),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            Twig.error { "Exception in setting dynamic icons $e" }
        }
    }

    private fun setBooleanPreference(default: BooleanPreferenceDefault, newState: Boolean) {
        viewModelScope.launch {
            val prefs = StandardPreferenceSingleton.getInstance(getApplication())
            mutex.withLock {
                default.putValue(prefs, newState)
            }
        }
    }
}
