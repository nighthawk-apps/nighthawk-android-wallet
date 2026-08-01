package com.nighthawkapps.lib.android.sdk.uniffi

import com.nighthawkapps.lib.android.sdk.net.LightwalletTlsPin
import com.nighthawkapps.lib.android.sdk.net.TorDarkfidEndpoint
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatPreferences
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
        val useTor = prefs.routeOutboundThroughTor
        return DrkBootstrapConfig(
            network =
                when (wallet.network) {
                    DarkfiNetwork.Mainnet -> "mainnet"
                    DarkfiNetwork.Testnet -> "testnet"
                },
            mnemonic = wallet.seedPhrase,
            walletDbPath = DrkWalletPaths.walletDb(context).absolutePath,
            cachePath = DrkWalletPaths.cacheDir(context).absolutePath,
            walletPass = DrkWalletPassStore.getOrCreate(context),
            lightwalletServerUrl = TorDarkfidEndpoint.displayUrlForWallet(context, wallet.endpoint),
            birthdayHeight = wallet.birthdayHeight ?: -1L, // null → full history; 0 → tip seed

            lightwalletTlsPinSha256 = LightwalletTlsPin.pinBytesOrNull(context),
            useTor = useTor,
            torSocksPort = prefs.socksPort.toUShort(),
            darkfidRpcUrl = null, // LWD-only; never hardcode a darkfid testnet port
        )
    }

    /**
     * Opens a native wallet session from persisted bootstrap fields (mirrors upstream **`Drk::new`**).
     *
     * @throws DarkfiWalletNativeException when bootstrap validation fails or the library is broken.
     */
    fun openWallet(
        context: android.content.Context,
        wallet: PersistableDarkfiWallet,
    ): DarkfiWalletHandle = DarkfiWalletHandle(buildBootstrapConfig(context, wallet))

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
