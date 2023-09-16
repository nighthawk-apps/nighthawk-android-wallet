package co.electriccoin.zcash.network.util

import co.electriccoin.zcash.network.CoinMetricsApiService
import co.electriccoin.zcash.network.util.Const.COIN_METRICS_BASE_URL
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitHelper {

    private fun getOkhttpClient(): OkHttpClient {
        return OkHttpClient.Builder().addInterceptor(
            HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)
        ).build()
    }

    private fun getRetrofitIBuilder(okHttpClient: OkHttpClient = getOkhttpClient()): Retrofit.Builder {
        return Retrofit.Builder()
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
    }

    private fun getCoinMetricsRetrofitInstance(): Retrofit {
        return getRetrofitIBuilder()
            .baseUrl(COIN_METRICS_BASE_URL)
            .build()
    }

    fun getCoinMetricsApiService(): CoinMetricsApiService {
        return getCoinMetricsRetrofitInstance().create(CoinMetricsApiService::class.java)
    }

}
