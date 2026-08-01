package com.nighthawkapps.lib.android.sdk.chat

/**
 * Lets [OutgoingChatReconnectWorker] request a reconnect when the chat UI has registered a handler
 * (typically the active [DarkfiChatController]'s [DarkfiChatController.connectOrRetry]).
 */
object DarkfiChatConnectionBridge {
    @Volatile
    private var reconnect: (() -> Unit)? = null

    @Volatile
    private var p2pReady: (() -> Unit)? = null

    fun attach(handler: () -> Unit) {
        reconnect = handler
    }

    fun attachP2pReady(handler: () -> Unit) {
        p2pReady = handler
    }

    fun detach() {
        reconnect = null
        p2pReady = null
    }

    /** @return true if a handler ran */
    fun requestReconnect(): Boolean {
        val h = reconnect ?: return false
        h()
        return true
    }

    /** @return true if a handler ran */
    fun notifyP2pReady(): Boolean {
        val h = p2pReady ?: return false
        h()
        return true
    }
}
