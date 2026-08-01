package com.nighthawkapps.lib.android.ui.screen.transfer

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import com.nighthawkapps.lib.android.spackle.Twig
import com.nighthawkapps.lib.android.ui.MainActivity
import com.nighthawkapps.lib.android.ui.screen.transfer.view.TransferMainView

@Composable
internal fun MainActivity.AndroidTransfer(
    onSendMoney: () -> Unit,
    onReceiveMoney: () -> Unit,
    onTopUp: () -> Unit,
    onDaoHub: () -> Unit,
) {
    WrapTransfer(
        activity = this,
        onSendMoney = onSendMoney,
        onReceiveMoney = onReceiveMoney,
        onTopUp = onTopUp,
        onDaoHub = onDaoHub,
    )
}

@Composable
internal fun WrapTransfer(
    activity: ComponentActivity,
    onSendMoney: () -> Unit,
    onReceiveMoney: () -> Unit,
    onTopUp: () -> Unit,
    onDaoHub: () -> Unit,
) {
    Twig.debug { "Just for initial run $activity" }
    TransferMainView(
        onSendMoney = onSendMoney,
        onReceiveMoney = onReceiveMoney,
        onTopUp = onTopUp,
        onDaoHub = onDaoHub,
    )
}
