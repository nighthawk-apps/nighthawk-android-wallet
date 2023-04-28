package co.electriccoin.zcash.ui.common

import kotlin.time.Duration.Companion.seconds

// Recommended timeout for Android configuration changes to keep Kotlin Flow from restarting
val ANDROID_STATE_FLOW_TIMEOUT = 5.seconds

// Our sdk keep checking for new blocks in bg and for a very short time state changes. For better UX we are adding this debounce
val TRANSFER_TAB_ENABLE_DEBOUNCE_TIMEOUT = 2.seconds
