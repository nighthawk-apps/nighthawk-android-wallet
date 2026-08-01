package com.nighthawkapps.lib.android.network.models

import com.google.gson.annotations.SerializedName

/** CoinGecko `simple/price` maps asset id → { fiat_code → price }. */
data class PriceApiResponse(
    @SerializedName("darkfi")
    val vsPrices: Map<String, Double>? = null,
)
