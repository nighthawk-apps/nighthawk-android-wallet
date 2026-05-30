package com.nighthawkapps.lib.android.ui.screen.shield

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.nighthawkapps.lib.android.ui.MainActivity

@Composable
internal fun MainActivity.AndroidShield(onBack: () -> Unit) {
    WrapShield(activity = this, onBack = onBack)
}

@Composable
internal fun WrapShield(
    activity: ComponentActivity,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    LaunchedEffect(Unit) {
        Toast
            .makeText(
                activity,
                "Shielding is not available until the DarkFi transaction pipeline is connected.",
                Toast.LENGTH_SHORT,
            ).show()
        onBack()
    }
}
