package com.nighthawkapps.lib.android.sdk.tor.embedded

import android.content.Context
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatPreferences
import com.nighthawkapps.lib.android.sdk.chat.TorIntegrationHelper
import com.nighthawkapps.lib.android.sdk.chat.TorSocksReadiness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * Starts Guardian [tor-android] inside the app and waits until the SOCKS port answers.
 */
object EmbeddedTorController {
    private val mutex = Mutex()
    private val activeOperator = AtomicReference<EmbeddedTorOperator?>(null)

    /**
     * When [DarkfiChatPreferences.useEmbeddedTor] is true, installs/starts embedded Tor and blocks
     * until [TorSocksReadiness] sees a reachable SOCKS port (or returns null on failure).
     */
    suspend fun awaitEmbeddedSocksIfEnabled(
        context: Context,
        prefs: DarkfiChatPreferences,
    ): Int? {
        if (!prefs.routeOutboundThroughTor || !prefs.useEmbeddedTor) {
            return null
        }
        val app = context.applicationContext
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                var op = activeOperator.get()
                if (op == null) {
                    val settings = EmbeddedTorDomain.Settings(app)
                    op =
                        EmbeddedTorOperator(settings) { _ ->
                        }
                    activeOperator.set(op)
                    op.start()
                }
                val socksHost =
                    prefs.socksHost.trim().ifBlank { TorIntegrationHelper.DEFAULT_SOCKS_HOST }
                val ports =
                    buildList {
                        add(prefs.socksPort)
                        if (prefs.socksPort != TorIntegrationHelper.DEFAULT_SOCKS_PORT) {
                            add(TorIntegrationHelper.DEFAULT_SOCKS_PORT)
                        }
                        if (prefs.socksPort != TorIntegrationHelper.ALT_SOCKS_PORT) {
                            add(TorIntegrationHelper.ALT_SOCKS_PORT)
                        }
                    }.distinct()
                TorSocksReadiness.awaitFirstReachablePort(
                    socksHost,
                    ports,
                    deadlineMs = EMBEDDED_SOCKS_DEADLINE_MS,
                )
            }
        }
    }

    /** Best-effort stop of the in-process Tor daemon (e.g. when turning Tor off app-wide). */
    suspend fun stopEmbeddedTor() {
        mutex.withLock {
            withContext(Dispatchers.IO) {
                activeOperator.getAndSet(null)?.stop()
            }
        }
    }

    private const val EMBEDDED_SOCKS_DEADLINE_MS = 120_000L
}
