package com.nighthawkapps.lib.android.sdk.uniffi

import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatPreferences
import com.nighthawkapps.lib.android.sdk.net.LightwalletTlsPin
import com.nighthawkapps.lib.android.sdk.net.PrivacyNetwork
import com.nighthawkapps.lib.android.sdk.net.TorDarkfidEndpoint
import com.nighthawkapps.lib.android.sdk.nym.PlatformTls
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiNetwork
import com.nighthawkapps.lib.android.sdk.wallet.DrkWalletPassStore
import com.nighthawkapps.lib.android.sdk.wallet.DrkWalletPaths
import com.nighthawkapps.lib.android.sdk.wallet.PersistableDarkfiWallet
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DarkfiWalletHandle
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DarkfiWalletNativeException
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DrkBootstrapConfig
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.bridgePing as ffiBridgePing
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.bridgeVersion as ffiBridgeVersion

/** Outcome when probing UniFFI + `libdarkfi_mobile_ffi` at runtime (JNA load). */
sealed interface DarkfiNativeProbe {
    /** Library loaded and a trivial FFI round-trip succeeded. */
    data object Ok : DarkfiNativeProbe

    /** `UnsatisfiedLinkError` — no `.so` in `jniLibs` or ABI mismatch. */
    data object MissingLibrary : DarkfiNativeProbe

    /** Linking worked but FFI failed (checksum / contract / unexpected error). */
    data class Broken(
        val throwable: Throwable
    ) : DarkfiNativeProbe

    companion object {
        fun run(): DarkfiNativeProbe =
            try {
                if (ffiBridgePing() == "pong") Ok else Broken(IllegalStateException("unexpected ping response"))
            } catch (e: NoClassDefFoundError) {
                Broken(e)
            } catch (e: UnsatisfiedLinkError) {
                MissingLibrary
            } catch (e: Throwable) {
                Broken(e)
            }
    }
}

/**
 * Kotlin façade over `com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi` (generated UniFFI).
 * Application code should depend on this object instead of generated types.
 *
 * Bootstrap fields for future **`Drk::new`** wiring are carried in [PersistableDarkfiWallet] until the
 * Rust crate links `bin/drk` (network, mnemonic, `darkfid` endpoint URL).
 */
object DarkfiMobileFfiApi {
    /** Serializes `Drk::new` so two callers cannot hold the same Fjall cache lock. */
    private val openLock = Any()

    /**
     * One native session per process. A second `Drk::new` hits `FjallError: Locked`,
     * and wiping then deletes the database the first session still has open.
     * [releaseWallet] drops it only after the last synchronizer closes.
     */
    private var liveHandle: DarkfiWalletHandle? = null
    private var liveUsers: Int = 0

    /** Semver of the Rust `darkfi-mobile-ffi` crate, or null if the native library is unavailable. */
    fun nativeCrateSemver(): String? = runCatching { ffiBridgeVersion() }.getOrNull()

    /** Legacy no-arg constructor removed; use [openWallet]. */
    fun newWalletHandleOrNull(): DarkfiWalletHandle? = null

    /**
     * Builds the UniFFI bootstrap record for upstream **`Drk::new`** without opening a session.
     * Useful for diagnostics and unit tests (no native library required).
     */
    fun buildBootstrapConfig(
        context: android.content.Context,
        wallet: PersistableDarkfiWallet,
    ): DrkBootstrapConfig {
        DrkWalletPaths.ensureDirectories(context)
        val prefs = DarkfiChatPreferences(context.applicationContext)
        val network = prefs.privacyNetwork
        return DrkBootstrapConfig(
            network =
                when (wallet.network) {
                    DarkfiNetwork.Mainnet -> "mainnet"
                    DarkfiNetwork.Testnet -> "testnet"
                },
            mnemonic = wallet.seedPhrase,
            walletDbPath = DrkWalletPaths.walletDb(context).absolutePath,
            cachePath = DrkWalletPaths.cacheDir(context).absolutePath,
            walletPass = DrkWalletPassStore.forMnemonic(wallet.seedPhrase),
            lightwalletServerUrl = TorDarkfidEndpoint.displayUrlForWallet(context, wallet.endpoint),
            birthdayHeight = wallet.birthdayHeight ?: -1L, // null → full history; 0 → tip seed

            lightwalletTlsPinSha256 = LightwalletTlsPin.pinBytesOrNull(context),
            useTor = network == PrivacyNetwork.Tor,
            torSocksPort = prefs.socksPort.toUShort(),
            useNym = network == PrivacyNetwork.Nym,
            nymSocksPort = prefs.nymSocksPort.toUShort(),
            darkfidRpcUrl = null, // LWD-only; never hardcode a darkfid testnet port
            strictOmrOnly = prefs.strictOmrOnly,
        )
    }

    /**
     * Opens a native wallet session from persisted bootstrap fields (mirrors upstream **`Drk::new`**).
     *
     * Stale turso/kvdb files (passphrase rotation, schema upgrade, leftover flock) are wiped
     * once and retried — same recovery as iOS `WalletHandleManager.prepare`.
     *
     * @throws DarkfiWalletNativeException when bootstrap validation fails or the library is broken.
     */
    fun openWallet(
        context: android.content.Context,
        wallet: PersistableDarkfiWallet,
    ): DarkfiWalletHandle {
        val app = context.applicationContext
        synchronized(openLock) {
            val existing = liveHandle
            if (existing != null && !existing.uniffiIsDestroyed) {
                liveUsers += 1
                return existing
            }
            liveHandle = null
            liveUsers = 0

            var last: DarkfiWalletNativeException.NativeDrkUnavailable? = null
            repeat(3) { attempt ->
                try {
                    val config = buildBootstrapConfig(app, wallet)
                    if (config.useNym) {
                        PlatformTls.ensureInitialized(app)
                    }
                    val handle = DarkfiWalletHandle(config)
                    liveHandle = handle
                    liveUsers = 1
                    return handle
                } catch (e: DarkfiWalletNativeException.NativeDrkUnavailable) {
                    if (!isRecoverableWalletOpenFailure(e.message)) {
                        throw e
                    }
                    last = e
                    if (shouldWipeBeforeRetry(e.message)) {
                        android.util.Log.w(
                            "DarkfiMobileFfiApi",
                            "native wallet open failed (attempt ${attempt + 1}/3); wiping local DB: ${e.message}",
                        )
                        DrkWalletPaths.wipeLocalState(app)
                    } else {
                        android.util.Log.w(
                            "DarkfiMobileFfiApi",
                            "native wallet open failed (attempt ${attempt + 1}/3); lock held, retrying without wipe: ${e.message}",
                        )
                    }
                    // Fjall Drop / exclusive lock can lag behind UniFFI destroy().
                    try {
                        Thread.sleep(200L * (attempt + 1))
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
            }
            throw last
                ?: DarkfiWalletNativeException.NativeDrkUnavailable("native wallet open failed")
        }
    }

    /**
     * Drops one user of the process-wide session. The native handle is destroyed
     * only when the last synchronizer closes, so a reload cannot hand out a dead object.
     */
    fun releaseWallet(handle: DarkfiWalletHandle) {
        synchronized(openLock) {
            if (liveHandle !== handle) {
                if (!handle.uniffiIsDestroyed) {
                    handle.close()
                }
                return
            }
            liveUsers = (liveUsers - 1).coerceAtLeast(0)
            if (liveUsers > 0) {
                return
            }
            liveHandle = null
            liveUsers = 0
            if (!handle.uniffiIsDestroyed) {
                handle.close()
            }
        }
    }

    /** Lock conflicts must be retried. Deleting the DB while it is open destroys the live handle. */
    internal fun shouldWipeBeforeRetry(message: String?): Boolean {
        val lower = message?.lowercase() ?: return false
        return !lower.contains("locked") &&
            !lower.contains("could not acquire lock") &&
            !lower.contains("resource temporarily unavailable")
    }

    /**
     * True when [message] from `Drk::new` / `WalletDb` is safe to recover by deleting local
     * cache + `wallet.db` and re-opening (keys are re-imported from the mnemonic).
     */
    internal fun isRecoverableWalletOpenFailure(message: String?): Boolean {
        val lower = message?.lowercase() ?: return false
        return lower.contains("could not acquire lock") ||
            lower.contains("resource temporarily unavailable") ||
            lower.contains("walletdb") ||
            lower.contains("pragma") ||
            lower.contains("file is not a database") ||
            lower.contains("sqlite") ||
            lower.contains("sqlcipher") ||
            lower.contains("turso") ||
            lower.contains("fjall") ||
            lower.contains("initializationfailed") ||
            lower.contains("connectionfailed") ||
            lower.contains("initialize_wallet") ||
            lower.contains("databaseerror") ||
            lower.contains("queryexecution")
    }

    /**
     * Maps persisted wallet JSON to the bootstrap shape expected by upstream **`Drk::new`**
     * (network label, mnemonic words, lightwallet server URL).
     */
    fun drkBootstrapSummary(wallet: PersistableDarkfiWallet): DrkBootstrapSummary =
        DrkBootstrapSummary(
            network =
                when (wallet.network) {
                    DarkfiNetwork.Mainnet -> "mainnet"
                    DarkfiNetwork.Testnet -> "testnet"
                },
            mnemonicWordCount = wallet.seedPhrase.size,
            lightwalletServerUrl = wallet.endpoint.toDisplayString(),
        )
}

/** Non-secret summary for diagnostics / future UniFFI record (no seed words in logs). */
data class DrkBootstrapSummary(
    val network: String,
    val mnemonicWordCount: Int,
    val lightwalletServerUrl: String,
)
