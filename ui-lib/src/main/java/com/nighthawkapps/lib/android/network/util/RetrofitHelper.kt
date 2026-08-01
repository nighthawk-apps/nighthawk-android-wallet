package com.nighthawkapps.lib.android.network.util

import android.content.Context
import com.nighthawkapps.lib.android.network.CoinMetricsApiService
import com.nighthawkapps.lib.android.sdk.net.TorOutboundSocks
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitHelper {
    private const val COINGECKO_HOST = "api.coingecko.com"
    private const val COINGECKO_PIN = "sha256/BhM1gGE+L4fCC9ER5xj4P1/deHgoXOjL9TsSj7Q5B9o="

    private fun buildOkHttpClient(context: Context): OkHttpClient {
        val app = context.applicationContext
        val loggingLevel =
            if (com.nighthawkapps.lib.android.ui.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }

        val certificatePinner =
            CertificatePinner
                .Builder()
                .add(COINGECKO_HOST, COINGECKO_PIN)
                .build()

        val builder =
            OkHttpClient
                .Builder()
                .certificatePinner(certificatePinner)
                .addInterceptor(
                    HttpLoggingInterceptor().setLevel(loggingLevel),
                )
        TorOutboundSocks.proxyForClearnetHttp(app)?.let(builder::proxy)
        return builder.build()
    }

    private fun retrofitBuilder(context: Context): Retrofit.Builder =
        Retrofit
            .Builder()
            .client(buildOkHttpClient(context))
            .addConverterFactory(GsonConverterFactory.create())

    fun getCoinMetricsApiService(context: Context): CoinMetricsApiService =
        retrofitBuilder(context)
            .baseUrl(Const.COIN_METRICS_BASE_URL)
            .build()
            .create(CoinMetricsApiService::class.java)
}
