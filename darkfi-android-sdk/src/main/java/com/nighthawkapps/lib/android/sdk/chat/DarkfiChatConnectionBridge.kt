package com.nighthawkapps.lib.android.sdk.chat

/**
 * Lets [OutgoingChatReconnectWorker] request a reconnect when the chat UI has registered a handler
 * (typically the active [DarkfiChatController]'s [DarkfiChatController.connectOrRetry]).
 */
object DarkfiChatConnectionBridge {
    @Volatile
    private var reconnect: (() -> Unit)? = null

    fun attach(handler: () -> Unit) {
        reconnect = handler
    }

    fun detach() {
        reconnect = null
    }

    /** @return true if a handler ran */
    fun requestReconnect(): Boolean {
        val h = reconnect ?: return false
        h()
        return true
    }
}
