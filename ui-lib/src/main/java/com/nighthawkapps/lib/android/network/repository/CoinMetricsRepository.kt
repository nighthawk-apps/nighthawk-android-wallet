package com.nighthawkapps.lib.android.network.repository

import com.nighthawkapps.lib.android.network.models.PriceApiResponse
import com.nighthawkapps.lib.android.network.util.Const
import com.nighthawkapps.lib.android.network.util.Resource
import kotlinx.coroutines.flow.Flow

interface CoinMetricsRepository {
    suspend fun observeSpotPrice(
        currency: String,
        assetId: String = Const.COIN_GECKO_ASSET_ID,
    ): Flow<Resource<PriceApiResponse>>
}
