package com.nighthawkapps.lib.android.ui

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatConnectionBridge
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatConnectionState
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatController
import com.nighthawkapps.lib.android.ui.screen.chat.LocalDarkfiChatController
import com.nighthawkapps.lib.android.ui.screen.navigation.BottomNavigation
import com.nighthawkapps.lib.android.ui.screen.navigation.MainNavigation

@Composable
internal fun MainActivity.NavigationMainContent() {
    val coroutineScope = lifecycleScope
    val appContext = applicationContext
    val chatController =
        remember(coroutineScope, appContext) {
            DarkfiChatController(
                scope = coroutineScope,
                appContext = appContext,
            )
        }

    DisposableEffect(chatController) {
        DarkfiChatConnectionBridge.attach { chatController.connectOrRetry() }
        onDispose {
            DarkfiChatConnectionBridge.detach()
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, chatController) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    val state = chatController.connectionState.value
                    if (state != DarkfiChatConnectionState.ConnectedDirect &&
                        state != DarkfiChatConnectionState.ConnectedViaTor
                    ) {
                        chatController.connectOrRetry()
                    }
                }
            }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            chatController.stop()
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
