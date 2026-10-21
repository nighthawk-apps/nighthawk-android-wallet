package com.nighthawkapps.lib.android.sdk.nym

import android.content.Context
import com.nighthawkapps.lib.android.spackle.Twig

/**
 * Hands the JVM and app context to the native TLS verifier used by the Nym client.
 *
 * Without this, the first HTTPS request Nym makes (topology fetch) panics inside Rust and the
 * mixnet never comes up. Must run before [AppNymCoordinator] starts the proxy.
 */
object PlatformTls {
    @Volatile
    private var initialized = false

    @Synchronized
    fun ensureInitialized(context: Context): Boolean {
        if (initialized) return true
        initialized =
            runCatching {
                // JNA loads the library for UniFFI calls; JNI exports need System.loadLibrary.
                System.loadLibrary("darkfi_mobile_ffi")
                nativeInit(context.applicationContext)
            }.onFailure { Twig.warn { "PlatformTls: native TLS verifier init failed: $it" } }
                .isSuccess
        return initialized
    }

    @JvmStatic
    private external fun nativeInit(context: Context)
}
