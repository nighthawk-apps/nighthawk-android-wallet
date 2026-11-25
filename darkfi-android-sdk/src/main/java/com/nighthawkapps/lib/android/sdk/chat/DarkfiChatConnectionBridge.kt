package com.nighthawkapps.lib.android.sdk.chat

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lets coordinators and [OutgoingChatReconnectWorker] request a reconnect when the chat UI has
 * registered a handler (typically the active [DarkfiChatController]'s
 * [DarkfiChatController.restartIfNetworkChanged]).
 *
 * Reconnect requests arriving while no handler is attached (for example during Activity recreation
 * or early proxy readiness) are coalesced and replayed once the next handler attaches.
 */
object DarkfiChatConnectionBridge {
    private val lock = Any()

    @Volatile
    private var reconnect: (() -> Unit)? = null

    @Volatile
    private var p2pReady: (() -> Unit)? = null

    private val pendingReconnect = AtomicBoolean(false)

    fun attach(handler: () -> Unit) {
        val replay =
            synchronized(lock) {
                reconnect = handler
                pendingReconnect.getAndSet(false)
            }
        if (replay) {
            handler()
        }
    }

    fun attachP2pReady(handler: () -> Unit) {
        synchronized(lock) {
            p2pReady = handler
        }
    }

    /** Detach unconditionally (tests / full teardown). */
    fun detach() {
        synchronized(lock) {
            reconnect = null
            p2pReady = null
            pendingReconnect.set(false)
        }
    }

    /**
     * Owner-scoped detach: clears the reconnect handler only if [handler] is still the active
     * instance, preventing a disposing Activity from clobbering a recreated Activity's handler.
     */
    fun detach(handler: () -> Unit) {
        synchronized(lock) {
            if (reconnect === handler) {
                reconnect = null
            }
        }
    }

    /** @return true if a handler ran immediately; false if queued for the next [attach]. */
    fun requestReconnect(): Boolean {
        val h =
            synchronized(lock) {
                val current = reconnect
                if (current == null) {
                    pendingReconnect.set(true)
                    null
                } else {
                    pendingReconnect.set(false)
                    current
                }
            } ?: return false
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
