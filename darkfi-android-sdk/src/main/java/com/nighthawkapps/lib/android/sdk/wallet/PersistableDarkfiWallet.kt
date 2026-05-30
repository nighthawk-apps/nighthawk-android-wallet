@file:Suppress("UnusedParameter")

package com.nighthawkapps.lib.android.sdk.wallet

import android.content.Context
import com.nighthawkapps.lib.uniffi.darkfi_mobile_ffi.generateDarkfiMnemonic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class PersistableDarkfiWallet(
    val seedPhrase: List<String>,
    val network: DarkfiNetwork,
    val endpoint: DarkfiEndpoint,
    val birthdayHeight: Long?,
) {
    fun copyWithEndpoint(endpoint: DarkfiEndpoint): PersistableDarkfiWallet = copy(endpoint = endpoint)

    companion object {
        fun fromJsonObject(json: JSONObject): PersistableDarkfiWallet {
            val arr = json.getJSONArray("seedPhrase")
            val words =
                buildList {
                    for (i in 0 until arr.length()) {
                        add(arr.getString(i))
                    }
                }
            val network =
                when (json.getString("network")) {
                    "mainnet" -> DarkfiNetwork.Mainnet
                    else -> DarkfiNetwork.Testnet
                }
            val endpoint = DarkfiEndpoint.fromJson(json.getJSONObject("endpoint"))
            val birthday =
                if (json.has("birthdayHeight") && !json.isNull("birthdayHeight")) {
                    json.getLong("birthdayHeight")
                } else {
                    null
                }
            return PersistableDarkfiWallet(words, network, endpoint, birthday)
        }

        fun toJsonObject(wallet: PersistableDarkfiWallet): JSONObject =
            JSONObject().apply {
                put(
                    "seedPhrase",
                    JSONArray().apply { wallet.seedPhrase.forEach { put(it) } },
                )
                put(
                    "network",
                    when (wallet.network) {
                        DarkfiNetwork.Mainnet -> "mainnet"
                        DarkfiNetwork.Testnet -> "testnet"
                    },
                )
                put("endpoint", wallet.endpoint.toJson())
                wallet.birthdayHeight?.let { put("birthdayHeight", it) }
            }

        suspend fun create(
            context: Context,
            network: DarkfiNetwork,
            mode: WalletInitMode,
        ): PersistableDarkfiWallet {
            val endpoint = DarkfiEndpoint.defaultForNetwork(network)
            val phrase =
                when (mode) {
                    WalletInitMode.NewWallet -> {
                        withContext(Dispatchers.IO) {
                            generateDarkfiMnemonic()
                        }
                    }

                    is WalletInitMode.ImportWallet -> {
                        mode.seedPhraseWords
                    }
                }
            return PersistableDarkfiWallet(
                seedPhrase = phrase,
                network = network,
                endpoint = endpoint,
                birthdayHeight = null,
            )
        }
    }
}

/**
 * Default wallet network for new installs and UI that mirrors build flavor.
 *
 * Order:
 * 1. Merged resource `darkfi_is_testnet` when present (`darkfitestnet` / `darkfimainnet` overlays).
 * 2. `applicationId` suffix containing `.testnet` for side-by-side testnet installs.
 * 3. **Default Testnet** when neither applies (e.g. SDK tests without merged app resources).
 *
 * Persisted wallet JSON still wins wherever `PersistableDarkfiWallet.network` is loaded.
 */
fun darkfiNetworkFromPackage(context: Context): DarkfiNetwork {
    val boolId = context.resources.getIdentifier("darkfi_is_testnet", "bool", context.packageName)
    if (boolId != 0) {
        return if (context.resources.getBoolean(boolId)) {
            DarkfiNetwork.Testnet
        } else {
            DarkfiNetwork.Mainnet
        }
    }
    if (context.packageName.contains(".testnet")) {
        return DarkfiNetwork.Testnet
    }
    return DarkfiNetwork.Testnet
}
