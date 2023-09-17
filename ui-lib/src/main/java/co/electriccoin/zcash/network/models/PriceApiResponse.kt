package co.electriccoin.zcash.network.models

import com.google.gson.annotations.SerializedName

data class PriceApiResponse(
    @SerializedName("zcash")
    val data: Map<String, Double>
)
