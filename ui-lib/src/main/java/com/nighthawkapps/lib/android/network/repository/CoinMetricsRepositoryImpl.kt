package com.nighthawkapps.lib.android.network.repository

import com.nighthawkapps.lib.android.network.CoinMetricsApiService
import com.nighthawkapps.lib.android.network.models.PriceApiResponse
import com.nighthawkapps.lib.android.network.util.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class CoinMetricsRepositoryImpl(
    private val coinMetricsApiService: CoinMetricsApiService
) : CoinMetricsRepository {
    override suspend fun observeSpotPrice(
        currency: String,
        assetId: String,
    ): Flow<Resource<PriceApiResponse>> =
        withContext(Dispatchers.IO) {
            flow {
                emit(Resource.Loading())
                try {
                    val response = coinMetricsApiService.getSimplePrice(assetId, currency)
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
