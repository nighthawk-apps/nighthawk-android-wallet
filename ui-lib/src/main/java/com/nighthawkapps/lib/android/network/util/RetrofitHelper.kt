package com.nighthawkapps.lib.android.network.util

import android.content.Context
import com.nighthawkapps.lib.android.network.CoinMetricsApiService
import com.nighthawkapps.lib.android.sdk.net.TorOutboundSocks
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitHelper {
    private fun buildOkHttpClient(context: Context): OkHttpClient {
        val app = context.applicationContext
        val loggingLevel =
            if (com.nighthawkapps.lib.android.ui.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }

        val builder =
            OkHttpClient
                .Builder()
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
