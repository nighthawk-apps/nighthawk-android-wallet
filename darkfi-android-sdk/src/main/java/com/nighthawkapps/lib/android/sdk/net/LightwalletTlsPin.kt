package com.nighthawkapps.lib.android.sdk.net

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle

/**
 * Resolves the SHA-256 of the lightwalletd leaf certificate DER for TLS pinning (S8).
 *
 * Lookup order:
 * 1. SharedPreferences key `lightwallet_tls_pin_sha256` (64 hex chars) — runtime override
 * 2. Manifest meta-data `com.nighthawkapps.lightwallet_tls_pin_sha256` (64 hex chars)
 *
 * Empty / missing → null (loopback cleartext still allowed; remote HTTPS fails closed
 * until a pin is configured).
 */
object LightwalletTlsPin {
    const val PREFS_NAME = "darkfi_lightwallet_security"
    const val PREFS_KEY = "lightwallet_tls_pin_sha256"
    const val META_DATA_KEY = "com.nighthawkapps.lightwallet_tls_pin_sha256"

    fun pinBytesOrNull(context: Context): List<UByte>? {
        val app = context.applicationContext
        val fromPrefs =
            app
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREFS_KEY, null)
        parseHexPin(fromPrefs)?.let {
            return it
        }
        return parseHexPin(readManifestMeta(app))
    }

    fun parseHexPin(hex: String?): List<UByte>? {
        val cleaned =
            hex
                ?.trim()
                ?.removePrefix("0x")
                ?.removePrefix("0X")
                ?.filter { !it.isWhitespace() && it != ':' }
                ?.lowercase()
                ?: return null
        if (cleaned.isEmpty()) return null
        if (cleaned.length != 64 || cleaned.any { it !in "0123456789abcdef" }) {
            return null
        }
        return cleaned.chunked(2).map { it.toInt(16).toUByte() }
    }

    private fun readManifestMeta(context: Context): String? {
        return try {
            val ai: ApplicationInfo =
                context.packageManager.getApplicationInfo(
                    context.packageName,
                    PackageManager.GET_META_DATA,
                )
            val bundle: Bundle = ai.metaData ?: return null
            bundle.getString(META_DATA_KEY)
        } catch (_: Exception) {
            null
        }
    }
}
