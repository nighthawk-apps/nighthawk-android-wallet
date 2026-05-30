package com.nighthawkapps.lib.android.ui.screen.security

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.screen.security.view.Security
import com.nighthawkapps.lib.android.ui.screen.security.viewmodel.SecurityViewModel

@Composable
internal fun MainActivity.AndroidSecurity(
    onBack: () -> Unit,
    onSetPin: () -> Unit
) {
    WrapAndroidSecurity(activity = this, onBack = onBack, onSetPin = onSetPin)
}

@Composable
internal fun WrapAndroidSecurity(
    activity: ComponentActivity,
    onBack: () -> Unit,
    onSetPin: () -> Unit
) {
    val securityViewModel by activity.viewModels<SecurityViewModel>()

    val isPinEnabled =
        securityViewModel.lastEnteredPin
            .collectAsStateWithLifecycle()
            .value
            ?.isNotBlank()
    val isTouchIdOrFaceEnabled = securityViewModel.isTouchIdOrFaceIdEnabled.collectAsStateWithLifecycle().value

    var previousVisibility by remember {
        mutableStateOf(true)
    }

    BackHandler {
        previousVisibility = true
        onBack()
    }

    if (isPinEnabled != null && isTouchIdOrFaceEnabled != null) {
        Security(
            onBack = {
                previousVisibility = true
                onBack()
            },
            isPinEnabled = isPinEnabled,
            isTouchIdOrFaceEnabled = isTouchIdOrFaceEnabled,
            isBioMetricEnabledOnMobile = securityViewModel.isBioMetricEnabledOnMobile(),
            onDisablePin = securityViewModel::disablePin,
            onSetPin = {
                previousVisibility = false
                onSetPin()
            },
            onTouchIdToggleChanged = securityViewModel::updateTouchIdFaceIdStatus
        )
    } else {
        // We can show loading state if it takes time but It's a small data from preference so shouldn't take time
    }
}
