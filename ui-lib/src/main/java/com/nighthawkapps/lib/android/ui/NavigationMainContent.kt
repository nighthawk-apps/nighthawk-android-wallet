package com.nighthawkapps.lib.android.ui

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatConnectionBridge
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatConnectionState
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatController
import com.nighthawkapps.lib.android.ui.screen.chat.LocalDarkfiChatController
import com.nighthawkapps.lib.android.ui.screen.navigation.BottomNavigation
import com.nighthawkapps.lib.android.ui.screen.navigation.MainNavigation

@Composable
internal fun MainActivity.NavigationMainContent() {
    val appContext = applicationContext
    // Application-scoped singleton — must not use Activity lifecycleScope or the
    // chat read/connect jobs are cancelled when MainActivity is destroyed.
    val chatController = remember(appContext) { DarkfiChatController.getOrCreate(appContext) }

    DisposableEffect(chatController) {
        DarkfiChatConnectionBridge.attach { chatController.connectOrRetry() }
        onDispose {
            DarkfiChatConnectionBridge.detach()
        }
    }

    LaunchedEffect(chatController) {
        chatController.connectOrRetry()
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, chatController) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        // After backgrounding the daemon may have died, lost P2P peers,
                        // or Android OS may have recycled the process. Re-check state.
                        val state = chatController.connectionState.value
                        val ffiStatus =
                            com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi
                                .darkircStatus()
                        if (state != DarkfiChatConnectionState.ConnectedDirect &&
                            state != DarkfiChatConnectionState.ConnectedViaTor
                        ) {
                            chatController.connectOrRetry()
                        } else if (ffiStatus == "not_running" || ffiStatus == "failed") {
                            chatController.connectOrRetry()
                        }
                    }

                    // Let the daemon + FGS survive backgrounding. Do NOT tear down on
                    // ON_STOP or Compose dispose — user expects instant resume.
                    Lifecycle.Event.ON_STOP -> { /* no-op */ }

                    else -> { /* no-op */ }
                }
            }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    val navController = rememberNavController()
    CompositionLocalProvider(LocalDarkfiChatController provides chatController) {
        Scaffold(
            bottomBar = {
                BottomNavigation(navController = navController)
            },
        ) {
            MainNavigation(navHostController = navController, paddingValues = it)
        }
    }
}
