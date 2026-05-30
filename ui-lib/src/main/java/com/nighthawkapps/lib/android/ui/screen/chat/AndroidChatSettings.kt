package com.nighthawkapps.lib.android.ui.screen.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatIdentity
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.screen.chat.view.ChatSettingsScreen

@Composable
internal fun MainActivity.AndroidChatSettings(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val controller = LocalDarkfiChatController.current
    val identity =
        remember {
            DarkfiChatIdentity(this)
        }
    if (controller == null) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = stringResource(R.string.ns_chat_unavailable))
        }
        return
    }
    ChatSettingsScreen(
        controller = controller,
        identity = identity,
        onBack = onBack,
    )
}
