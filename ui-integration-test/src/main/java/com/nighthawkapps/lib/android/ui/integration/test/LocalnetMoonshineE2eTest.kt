package com.nighthawkapps.lib.android.ui.integration.test

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatPreferences
import com.nighthawkapps.lib.android.sdk.net.PrivacyNetwork
import com.nighthawkapps.lib.android.sdk.uniffi.DarkfiMobileFfiApi
import com.nighthawkapps.lib.android.sdk.uniffi.DarkfiNativeProbe
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiEndpoint
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiNetwork
import com.nighthawkapps.lib.android.sdk.wallet.PersistableDarkfiWallet
import com.nighthawkapps.lib.android.sdk.wallet.WalletInitMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * On-device localnet run: Moonshine pays this app wallet with a UnifOMR clue,
 * then again with no clue. Strict sync must see only the first. Trial decrypt
 * after a rewind must see both. The DAO hub FFI then creates a group and adds funds.
 *
 * The phrase stays in app storage. This test logs the deposit address and tx ids only.
 */
@RunWith(AndroidJUnit4::class)
class LocalnetMoonshineE2eTest {
    private var savedPrivacy: PrivacyNetwork? = null

    @Before
    fun requireLocalnet() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("localnetE2e") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        savedPrivacy = DarkfiChatPreferences(context).privacyNetwork
    }

    @After
    fun restorePrivacyNetwork() {
        val saved = savedPrivacy ?: return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        DarkfiChatPreferences(context).privacyNetwork = saved
        savedPrivacy = null
    }

    @Test
    fun unifomr_trial_and_dao_hub_on_localnet() {
        val probe = DarkfiNativeProbe.run()
        if (probe !is DarkfiNativeProbe.Ok) {
            throw AssertionError("libdarkfi_mobile_ffi missing or broken: $probe")
        }

        val done = CountDownLatch(1)
        val error = AtomicReference<Throwable>()
        Thread(
            ThreadGroup("localnet-e2e"),
            {
                try {
                    runLocalnet()
                } catch (t: Throwable) {
                    error.set(t)
                } finally {
                    done.countDown()
                }
            },
            "localnet-e2e",
            32L * 1024L * 1024L,
        ).apply { isDaemon = true }.start()

        val inst = InstrumentationRegistry.getInstrumentation()
        while (!done.await(2, TimeUnit.SECONDS)) {
            inst.waitForIdleSync()
        }
        error.get()?.let { throw it }
    }

    /**
     * Strict mode already saw the clue payment and missed the clue-less one.
     * This wallet turns strict off before any of its own payments, then waits
     * for a Moonshine `--no-omr` send that lands after the scan cursor.
     */
    @Test
    fun trial_decrypt_existing_payments_and_dao() {
        val probe = DarkfiNativeProbe.run()
        if (probe !is DarkfiNativeProbe.Ok) {
            throw AssertionError("libdarkfi_mobile_ffi missing or broken: $probe")
        }
        val done = CountDownLatch(1)
        val error = AtomicReference<Throwable>()
        Thread(
            ThreadGroup("localnet-trial"),
            {
                try {
                    runTrialAndDao()
                } catch (t: Throwable) {
                    error.set(t)
                } finally {
                    done.countDown()
                }
            },
            "localnet-trial",
            32L * 1024L * 1024L,
        ).apply { isDaemon = true }.start()
        val inst = InstrumentationRegistry.getInstrumentation()
        while (!done.await(2, TimeUnit.SECONDS)) {
            inst.waitForIdleSync()
        }
        error.get()?.let { throw it }
    }

    private fun runTrialAndDao() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val args = InstrumentationRegistry.getArguments()
        val moonAddress = args.getString("e2e_moon_address").orEmpty()
        val trialAtomic = args.getString("e2e_trial_atomic")?.toLongOrNull() ?: 100_000_000L
        DarkfiChatPreferences(context).apply {
            privacyNetwork = PrivacyNetwork.Direct
            strictOmrOnly = false
        }
        val created =
            runBlocking {
                PersistableDarkfiWallet.create(
                    context,
                    DarkfiNetwork.Testnet,
                    WalletInitMode.NewWallet,
                )
            }.copyWithEndpoint(lightwalletdEndpoint())
        val handle = DarkfiMobileFfiApi.openWallet(context, created)
        try {
            handle.setStrictOmrOnly(false)
            val address = handle.primaryDepositAddress()
            note("TRIAL_ADDRESS $address strict=${handle.strictOmrOnly()}")
            val height = waitUntilSynced(handle)
            note("TRIAL_READY height=$height")
            val both = waitForBalanceAtLeast(handle, trialAtomic, 8 * 60)
            note("TRIAL_SAW_NOOMR balance=$both")
            if (moonAddress.startsWith("f")) {
                val txBytes = handle.buildTransfer(moonAddress, "0.05", null, "localnet-omr-return")
                val txHash = handle.broadcastTransfer(txBytes, "localnet-omr-return", moonAddress)
                note("RETURN_TX $txHash")
            }
            val daoName = "Hub${System.currentTimeMillis() % 100000}"
            val createdDao =
                handle.daoCreate(daoName, "1", "1", "1", 0.67, DRK_TOKEN)
            note("DAO_CREATED name=$daoName id=$createdDao")
            val names = handle.listDaos().map { it.name }
            if (!names.contains(daoName)) {
                throw AssertionError("DAO hub list missed $daoName; saw $names")
            }
            note("DAO_LISTED ${names.joinToString(",")}")
            note("DAO_FUNDED tx=${handle.daoFund(daoName, "0.05", null)}")
            note("DAO_TREASURY ${handle.daoTreasury(daoName)}")
            note("E2E_PASS")
        } finally {
            runCatching { handle.close() }
        }
    }

    private fun runLocalnet() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val args = InstrumentationRegistry.getArguments()
        val moonAddress = args.getString("e2e_moon_address").orEmpty()
        val omrAtomic = args.getString("e2e_omr_atomic")?.toLongOrNull() ?: 200_000_000L
        val trialAtomic = args.getString("e2e_trial_atomic")?.toLongOrNull() ?: 50_000_000L

        DarkfiChatPreferences(context).apply {
            privacyNetwork = PrivacyNetwork.Direct
            strictOmrOnly = true
        }

        val created =
            runBlocking {
                PersistableDarkfiWallet.create(
                    context,
                    DarkfiNetwork.Testnet,
                    WalletInitMode.NewWallet,
                )
            }.copyWithEndpoint(lightwalletdEndpoint())

        val handle = DarkfiMobileFfiApi.openWallet(context, created)
        try {
            val address = handle.primaryDepositAddress()
            note("ADDRESS $address")
            val readyHeight = waitUntilSynced(handle)
            note("READY height=$readyHeight")

            val afterOmr = waitForBalanceAtLeast(handle, omrAtomic, 8 * 60)
            note("STRICT_SAW_OMR balance=$afterOmr")

            val held = waitWhileBalanceBelow(handle, afterOmr + trialAtomic, 4 * 60)
            if (held >= afterOmr + trialAtomic) {
                throw AssertionError("strict sync received the clue-less payment: balance=$held")
            }
            note("STRICT_MISSED_NOOMR balance=$held")

            handle.setStrictOmrOnly(false)
            note("TRIAL_ON strict=${handle.strictOmrOnly()}")
            val rewindTo = readyHeight.coerceAtLeast(1L).toUInt()
            val reorg = handle.handleReorgRecovery(rewindTo)
            note("REWOUND to=$rewindTo invalidated=${reorg.blocksInvalidated}")

            val both = waitForBalanceAtLeast(handle, omrAtomic + trialAtomic - 1_000_000L, 8 * 60)
            note("TRIAL_SAW_BOTH balance=$both")

            if (moonAddress.startsWith("f")) {
                val txBytes = handle.buildTransfer(moonAddress, "0.05", null, "localnet-omr-return")
                val txHash = handle.broadcastTransfer(txBytes, "localnet-omr-return", moonAddress)
                note("RETURN_TX $txHash")
            } else {
                note("RETURN_SKIPPED")
            }

            val daoName = "Hub${System.currentTimeMillis() % 100000}"
            val createdDao =
                handle.daoCreate(
                    daoName,
                    "1",
                    "1",
                    "1",
                    0.67,
                    DRK_TOKEN,
                )
            note("DAO_CREATED name=$daoName id=$createdDao")
            val names = handle.listDaos().map { it.name }
            if (!names.contains(daoName)) {
                throw AssertionError("DAO hub list missed $daoName; saw $names")
            }
            note("DAO_LISTED ${names.joinToString(",")}")
            val funded = handle.daoFund(daoName, "0.05", null)
            note("DAO_FUNDED tx=$funded")
            note("DAO_TREASURY ${handle.daoTreasury(daoName)}")
            note("E2E_PASS")
        } finally {
            runCatching { handle.close() }
        }
    }

    private fun lightwalletdEndpoint(): DarkfiEndpoint {
        val raw =
            InstrumentationRegistry
                .getArguments()
                .getString("lightwalletd")
                .orEmpty()
                .trim()
        if (raw.isNotEmpty()) {
            val host = raw.substringBefore(':').ifBlank { "127.0.0.1" }
            val port = raw.substringAfter(':', "9067").toIntOrNull() ?: DEFAULT_PORT
            return DarkfiEndpoint(host, port, false)
        }
        return DarkfiEndpoint(defaultLightwalletdHost(), DEFAULT_PORT, false)
    }

    private fun defaultLightwalletdHost(): String {
        val fingerprint = android.os.Build.FINGERPRINT
        val model = android.os.Build.MODEL
        val emulator =
            fingerprint.startsWith("generic") ||
                fingerprint.startsWith("unknown") ||
                model.contains("google_sdk") ||
                model.contains("Emulator") ||
                model.contains("Android SDK built for")
        return if (emulator) "10.0.2.2" else "127.0.0.1"
    }

    private fun waitUntilSynced(handle: com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DarkfiWalletHandle): Long {
        val deadline = System.currentTimeMillis() + 4 * 60 * 1000
        var last = ""
        while (System.currentTimeMillis() < deadline) {
            val snap = handle.syncSnapshot()
            val line = "scanned=${snap.scannedBlocks} tip=${snap.chainTip}"
            if (line != last) {
                note("SYNC $line")
                last = line
            }
            if (snap.chainTip > 0 && snap.scannedBlocks >= snap.chainTip) {
                return snap.scannedBlocks
            }
            Thread.sleep(3_000)
        }
        throw AssertionError("wallet did not reach the localnet tip")
    }

    private fun waitForBalanceAtLeast(
        handle: com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DarkfiWalletHandle,
        target: Long,
        seconds: Int,
    ): Long {
        val deadline = System.currentTimeMillis() + seconds * 1000L
        var balance = 0L
        while (System.currentTimeMillis() < deadline) {
            balance = handle.confirmedBalanceAtomic()
            val snap = handle.syncSnapshot()
            note("POLL balance=$balance scanned=${snap.scannedBlocks} tip=${snap.chainTip}")
            if (balance >= target) return balance
            Thread.sleep(5_000)
        }
        throw AssertionError("balance $balance stayed below $target")
    }

    private fun waitWhileBalanceBelow(
        handle: com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DarkfiWalletHandle,
        ceiling: Long,
        seconds: Int,
    ): Long {
        val deadline = System.currentTimeMillis() + seconds * 1000L
        var balance = handle.confirmedBalanceAtomic()
        while (System.currentTimeMillis() < deadline) {
            balance = handle.confirmedBalanceAtomic()
            if (balance >= ceiling) return balance
            Thread.sleep(5_000)
        }
        return balance
    }

    private fun note(message: String) {
        Log.i(TAG, message)
        println("E2E_LOCAL $message")
    }

    private companion object {
        const val TAG = "E2E_LOCAL"
        const val DEFAULT_PORT = 9067
        const val DRK_TOKEN = "241vANigf1Cy3ytjM1KHXiVECxgxdK4yApddL8KcLssb"
    }
}
