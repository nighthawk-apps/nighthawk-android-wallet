package com.nighthawkapps.lib.android.sdk.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nighthawkapps.lib.android.sdk.net.LightwalletTlsPin
import com.nighthawkapps.lib.android.sdk.uniffi.DarkfiNativeProbe
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DarkfiWalletHandle
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.DrkBootstrapConfig
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Fail-closed live DarkFi testnet e2e on the Android emulator.
 *
 * Mnemonic is read from `/data/local/tmp/e2e_mnemonic.txt` (adb push) or the
 * `e2e_mnemonic` instrumentation extra. Never log the phrase.
 */
@RunWith(AndroidJUnit4::class)
class DarkfiTransferInstrumentedTest {
    @Test
    fun full_transaction_sync_and_transfer_live_testnet() {
        val probe = DarkfiNativeProbe.run()
        assertTrue(probe is DarkfiNativeProbe.Ok, "libdarkfi_mobile_ffi missing or broken: $probe")

        val args = InstrumentationRegistry.getArguments()
        val seedWords = loadMnemonic(args)
        assertEquals(22, seedWords.size, "Restore phrase must be 22 words")

        val recipient =
            args.getString("e2e_recipient")
                ?: readOptionalFile("/data/local/tmp/e2e_recipient.txt")
                ?: "fTh3ZcaehgSEx7Hk2EKLRThygj28Mt29RHhD9RDrN6ePx7jPuKyvp7Pf"
        val amount = args.getString("e2e_amount") ?: "0.05"
        val lwd =
            args.getString("e2e_lwd_url")
                ?: "https://epidermis-sandbox-marshland.ngrok-free.dev"
        val darkfid = args.getString("e2e_darkfid_rpc") ?: "tcp://127.0.0.1:18345"
        val birthday = args.getString("e2e_birthday")?.toLongOrNull() ?: 53200L
        val tlsPinHex =
            args.getString("e2e_tls_pin")
                ?: "9f8f3877f312cb48e4d8d050b5c7b70f6144f1c31812d7ec299c32793a274985"

        // AndroidJUnit4 runs on the main thread; UnifOMR + sleeps would ANR/kill
        // the instrumentation process after ~60s. ZK proof gen also overflows
        // the default ~1MB native stack (SIGSEGV SEGV_ACCERR). Run off-thread
        // with a 32MB stack and pump the looper so the runner stays alive.
        val error = AtomicReference<Throwable>()
        val done = CountDownLatch(1)
        Thread(
            null,
            {
                try {
                    runLiveTransfer(
                        seedWords = seedWords,
                        recipient = recipient,
                        amount = amount,
                        lwd = lwd,
                        darkfid = darkfid,
                        birthday = birthday,
                        tlsPinHex = tlsPinHex,
                    )
                } catch (t: Throwable) {
                    error.set(t)
                } finally {
                    done.countDown()
                }
            },
            "unifomr-live-e2e",
            32L * 1024L * 1024L,
        ).apply { isDaemon = true }.start()

        val inst = InstrumentationRegistry.getInstrumentation()
        while (!done.await(2, TimeUnit.SECONDS)) {
            inst.waitForIdleSync()
        }
        error.get()?.let { throw it }
    }

    private fun runLiveTransfer(
        seedWords: List<String>,
        recipient: String,
        amount: String,
        lwd: String,
        darkfid: String,
        birthday: Long,
        tlsPinHex: String,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val stamp = System.currentTimeMillis()
        val root = File(context.cacheDir, "live_e2e_$stamp").apply { mkdirs() }
        val walletDb = File(root, "wallet.db")
        val cache = File(root, "cache").apply { mkdirs() }

        println("E2E_OPENING_WALLET birthday=$birthday lwd=$lwd")
        val handle =
            DarkfiWalletHandle(
                DrkBootstrapConfig(
                    network = "testnet",
                    mnemonic = seedWords,
                    walletDbPath = walletDb.absolutePath,
                    cachePath = cache.absolutePath,
                    walletPass = "live_e2e_wallet_pass",
                    lightwalletServerUrl = lwd,
                    birthdayHeight = birthday,
                    lightwalletTlsPinSha256 = LightwalletTlsPin.parseHexPin(tlsPinHex),
                    useTor = false,
                    torSocksPort = 0u,
                    darkfidRpcUrl = darkfid,
                    strictOmrOnly = false,
                ),
            )

        val address = handle.primaryDepositAddress()
        println("WALLET_ADDRESS_DUMP: $address")

        waitUntilSynced(handle)
        val balance = waitForSpendableBalance(handle, address)
        println("E2E_BALANCE_ATOMIC: $balance")

        println("E2E_BUILDING_TRANSFER to $recipient amount=$amount")
        val txBytes = handle.buildTransfer(recipient, amount, null, null)
        val txHash = handle.broadcastTransfer(txBytes, null, recipient)
        println("TXID_DUMP: $txHash")
        assertEquals(64, txHash.length, "Expected 32-byte hex tx hash, got $txHash")
        assertTrue(txHash.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' })

        waitForExplorer(txHash)
        println("E2E_EXPLORER: https://explorer.testnet.dark.fi/tx/$txHash")
        handle.close()
    }

    private fun loadMnemonic(args: android.os.Bundle): List<String> {
        val fromArgs = args.getString("e2e_mnemonic")?.trim().orEmpty()
        val raw =
            fromArgs.ifEmpty {
                readOptionalFile("/sdcard/e2e_mnemonic.txt")
                    ?: readOptionalFile("/data/local/tmp/e2e_mnemonic.txt")
                    ?: ""
            }
        check(raw.isNotEmpty()) {
            "Push 22-word phrase to /data/local/tmp/e2e_mnemonic.txt or pass e2e_mnemonic extra"
        }
        return raw.split(Regex("\\s+")).filter { it.isNotBlank() }
    }

    private fun readOptionalFile(path: String): String? =
        runCatching { File(path).takeIf { it.isFile }?.readText()?.trim() }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }

    private fun waitUntilSynced(handle: DarkfiWalletHandle) {
        val deadline = System.currentTimeMillis() + 1_500_000
        var lastLog = 0L
        while (System.currentTimeMillis() < deadline) {
            val snap = handle.lightSyncSnapshot()
            val now = System.currentTimeMillis()
            if (now - lastLog > 15_000) {
                println(
                    "E2E_SYNC status=${snap.status} type=${snap.syncType} scanned=${snap.scannedHeight} tip=${snap.chainTip} omr=${snap.omrAvailable} msg=${snap.statusMessage}",
                )
                lastLog = now
            }
            if (snap.status == "Error") {
                fail("UnifOMR sync error: ${snap.statusMessage} fallback=${snap.fallbackUserMessage}")
            }
            // Instant Sync / UnifOMR can report Synced at tip while scannedHeight
            // stays at the last trial-decrypt window start (not the tip).
            val caughtUp = snap.chainTip > 0 &&
                (snap.scannedHeight + 2 >= snap.chainTip ||
                    (snap.status == "Synced" && snap.omrAvailable))
            if (caughtUp && (snap.status == "Synced" || snap.status == "Degraded")) {
                println(
                    "E2E_SYNC_DONE status=${snap.status} scanned=${snap.scannedHeight} tip=${snap.chainTip} omr=${snap.omrAvailable}",
                )
                return
            }
            Thread.sleep(5_000)
        }
        val snap = handle.lightSyncSnapshot()
        fail(
            "Timed out waiting for UnifOMR sync. status=${snap.status} scanned=${snap.scannedHeight} tip=${snap.chainTip} msg=${snap.statusMessage}",
        )
    }

    private fun waitForSpendableBalance(handle: DarkfiWalletHandle, address: String): Long {
        val deadline = System.currentTimeMillis() + 1_200_000
        var lastLog = 0L
        while (System.currentTimeMillis() < deadline) {
            val balance = handle.confirmedBalanceAtomic()
            val now = System.currentTimeMillis()
            if (now - lastLog > 10_000) {
                println("E2E_BALANCE_WAIT atomic=$balance address=$address")
                lastLog = now
            }
            if (balance > 8_000_000L) return balance
            Thread.sleep(5_000)
        }
        val balance = handle.confirmedBalanceAtomic()
        fail("Android wallet has no spendable testnet funds after UnifOMR sync. Address: $address balance=$balance")
        return balance
    }

    private fun waitForExplorer(txHash: String) {
        val url = URL("https://explorer.testnet.dark.fi/tx/$txHash")
        val sidecar = URL("http://127.0.0.1:18765/tx/$txHash")
        val deadline = System.currentTimeMillis() + 3_600_000
        var attempt = 0
        var minedLogged = false
        while (System.currentTimeMillis() < deadline) {
            attempt++
            println("E2E_EXPLORER_CHECK attempt=$attempt $url")
            if (isRealExplorerTxPage(url, txHash) || sidecarConfirmed(sidecar, txHash)) {
                return
            }
            if (!minedLogged && darkfidHasTx(txHash)) {
                println("E2E_DARKFID_MINED $txHash waiting_for_explorer")
                minedLogged = true
            }
            Thread.sleep(15_000)
        }
        fail("Transaction $txHash did not appear on the public DarkFi explorer")
    }

    private fun isRealExplorerTxPage(url: URL, txHash: String): Boolean =
        runCatching {
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 20_000
            connection.readTimeout = 20_000
            val code = connection.responseCode
            val body =
                (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()
                    ?.readText()
                    .orEmpty()
            isRealExplorerBody(body, txHash)
        }.getOrDefault(false)

    private fun sidecarConfirmed(url: URL, txHash: String): Boolean =
        runCatching {
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            val code = connection.responseCode
            val body =
                (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()
                    ?.readText()
                    .orEmpty()
            code == 200 && body.contains("CONFIRMED") && body.contains(txHash, ignoreCase = true)
        }.getOrDefault(false)

    private fun isRealExplorerBody(body: String, txHash: String): Boolean {
        if (body.isEmpty()) return false
        val anubis =
            body.contains("anubis_challenge", ignoreCase = true) ||
                body.contains("Making sure you're not a bot", ignoreCase = true) ||
                body.contains("Making sure you&#39;re not a bot", ignoreCase = true)
        if (anubis) return false
        if (body.contains("Page not found", ignoreCase = true)) return false
        if (!body.contains(txHash, ignoreCase = true)) return false
        return body.contains("Transaction Info") ||
            body.contains("From Block") ||
            body.contains("Raw Transaction")
    }

    private fun darkfidHasTx(txHash: String): Boolean =
        darkfidRpc("""{"jsonrpc":"2.0","method":"blockchain.get_tx","params":["$txHash"],"id":1}""")
            .let { it.contains("\"result\"") && !it.contains("\"error\"") }

    private fun darkfidPendingHasTx(txHash: String): Boolean =
        darkfidRpc("""{"jsonrpc":"2.0","method":"tx.pending","params":[],"id":1}""")
            .contains(txHash)

    private fun darkfidRpc(req: String): String =
        runCatching {
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress("127.0.0.1", 18345), 5_000)
            socket.soTimeout = 8_000
            socket.getOutputStream().write((req + "\n").toByteArray())
            val resp = socket.getInputStream().bufferedReader().readLine().orEmpty()
            socket.close()
            resp
        }.getOrDefault("")
}
