package co.electriccoin.zcash.ui.screen.settings.nighthawk

import androidx.compose.runtime.Composable
import androidx.core.app.ComponentActivity
import co.electriccoin.zcash.ui.MainActivity
import co.electriccoin.zcash.ui.screen.settings.nighthawk.view.SettingsView

@Composable
internal fun MainActivity.AndroidSettings() {
    WrapSettings(activity = this)
}

@Composable
internal fun WrapSettings(activity: ComponentActivity) {
    println("Just for initial run $activity")
    SettingsView()
}