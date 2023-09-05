package co.electriccoin.zcash.network

import co.electriccoin.zcash.network.models.PriceApiResponse
import co.electriccoin.zcash.network.util.Const
import retrofit2.http.GET
import retrofit2.http.Query

interface CoinMetricsApiService {

    @GET(Const.URL_GET_PRICE)
    suspend fun getZcashPrice(
        @Query("ids") id: String,
        @Query("vs_currencies") currency: String
    ): PriceApiResponse
}
