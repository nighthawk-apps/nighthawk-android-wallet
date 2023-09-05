package co.electriccoin.zcash.network.repository

import co.electriccoin.zcash.network.models.PriceApiResponse
import co.electriccoin.zcash.network.util.Const
import co.electriccoin.zcash.network.util.Resource
import kotlinx.coroutines.flow.Flow

interface CoinMetricsRepository {
    suspend fun getZecMarketData(
        currency: String,
        id: String = Const.ZCASH_ID
    ): Flow<Resource<PriceApiResponse>>
}
