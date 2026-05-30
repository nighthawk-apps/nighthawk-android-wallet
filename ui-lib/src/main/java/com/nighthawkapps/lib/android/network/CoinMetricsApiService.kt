package com.nighthawkapps.lib.android.network

import com.nighthawkapps.lib.android.network.models.PriceApiResponse
import com.nighthawkapps.lib.android.network.util.Const
import retrofit2.http.GET
import retrofit2.http.Query

interface CoinMetricsApiService {
    @GET(Const.URL_GET_PRICE)
    suspend fun getSimplePrice(
        @Query("ids") assetId: String,
        @Query("vs_currencies") currency: String,
    ): PriceApiResponse
}
