package co.electriccoin.zcash.network.repository

import co.electriccoin.zcash.network.CoinMetricsApiService
import co.electriccoin.zcash.network.models.PriceApiResponse
import co.electriccoin.zcash.network.util.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class CoinMetricsRepositoryImpl(private val coinMetricsApiService: CoinMetricsApiService): CoinMetricsRepository {
    override suspend fun getZecMarketData(
        currency: String,
        id: String
    ): Flow<Resource<PriceApiResponse>> {
        return withContext(Dispatchers.IO) {
            flow {
                emit(Resource.Loading())
                try {
                    val response = coinMetricsApiService.getZcashPrice(id, currency)
                    emit(Resource.Success(response))
                } catch (e: Exception) {
                    emit(
                        Resource.Error(
                            e.message ?: "Error while getting coin metrics market data"
                        )
                    )
                }
            }
        }
    }
}