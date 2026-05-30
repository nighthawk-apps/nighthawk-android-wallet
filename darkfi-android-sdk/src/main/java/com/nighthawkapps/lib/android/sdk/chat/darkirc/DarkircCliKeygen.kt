@file:Suppress("ReturnCount", "MagicNumber")

package com.nighthawkapps.lib.android.sdk.chat.darkirc

import android.content.Context
import com.nighthawkapps.lib.android.spackle.Twig
import java.util.concurrent.TimeUnit

/** Wraps packaged **`darkirc_exec`** one-shot crypto CLI flags (ChaChaBox key material). */
object DarkircCliKeygen {
    data class DmKeypair(
        val myDmChachaSecretBase58: String,
        val myDmChachaPublicBase58: String,
    )

    /** Runs `--gen-chacha-keypair` and parses `my_dm_chacha_secret` / commented public line. */
    fun genChachaKeypair(context: Context): DmKeypair? {
        val stdout = runCli(context, listOf("--gen-chacha-keypair")) ?: return null
        val secret =
            Regex("""my_dm_chacha_secret\s*=\s*"([^"]+)"""")
                .find(stdout)
                ?.groupValues
                ?.get(1)
                ?.trim()
        val public =
            Regex("""#my_dm_chacha_public\s*=\s*"([^"]+)"""")
                .find(stdout)
                ?.groupValues
                ?.get(1)
                ?.trim()
        if (secret == null || public == null || !DarkircBs58.isValidSecret32(secret)) {
            Twig.warn { "Embedded darkirc: could not parse --gen-chacha-keypair output" }
            return null
        }
        return DmKeypair(myDmChachaSecretBase58 = secret, myDmChachaPublicBase58 = public)
    }

    /** Runs `--gen-channel-secret` and parses the `secret = "…"` line. */
    fun genChannelSecret(context: Context): String? {
        val stdout = runCli(context, listOf("--gen-channel-secret")) ?: return null
        val secret =
            Regex("""secret\s*=\s*"([^"]+)"""")
                .find(stdout)
                ?.groupValues
                ?.get(1)
                ?.trim()
        if (secret == null || !DarkircBs58.isValidSecret32(secret)) {
            Twig.warn { "Embedded darkirc: could not parse --gen-channel-secret output" }
            return null
        }
        return secret
    }

    /** Runs `--get-chacha-pubkey <secret>` and returns the derived public key (bs58). */
    fun publicKeyFromSecret(
        context: Context,
        secretBase58: String,
    ): String? {
        if (!DarkircBs58.isValidSecret32(secretBase58)) {
            return null
        }
        val stdout =
            runCli(context, listOf("--get-chacha-pubkey", secretBase58))
                ?.trim()
                ?.lines()
                ?.lastOrNull()
                ?.trim()
        return stdout?.takeIf { DarkircBs58.isValidSecret32(it) }
    }

    private fun runCli(
        context: Context,
        args: List<String>,
    ): String? {
        val exe = DarkircEmbeddedRunner.ensureExecutable(context.applicationContext) ?: return null
        return try {
            val pb = ProcessBuilder(listOf(exe.absolutePath) + args)
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val finished = proc.waitFor(15, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                Twig.warn { "Embedded darkirc CLI timed out: ${args.firstOrNull()}" }
                return null
            }
            if (proc.exitValue() != 0) {
                Twig.warn { "Embedded darkirc CLI exit ${proc.exitValue()}: ${args.firstOrNull()}" }
                return null
            }
            proc.inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            Twig.error(e) { "Embedded darkirc CLI failed: ${args.firstOrNull()}" }
            null
        }
    }
}
