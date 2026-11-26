package com.nighthawkapps.lib.android.network.util

import android.content.Context
import com.nighthawkapps.lib.android.network.CoinMetricsApiService
import com.nighthawkapps.lib.android.sdk.chat.DarkfiChatPreferences
import com.nighthawkapps.lib.android.sdk.net.PrivacyNetwork
import com.nighthawkapps.lib.android.sdk.net.SessionTransport
import com.nighthawkapps.lib.android.sdk.net.TorOutboundSocks
import com.nighthawkapps.lib.android.sdk.net.TransportNative
import okhttp3.CertificatePinner
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetAddress
import java.net.UnknownHostException

object RetrofitHelper {
    private const val COINGECKO_HOST = "api.coingecko.com"

    /**
     * SPKI pins for `api.coingecko.com`: current leaf pin plus Google Trust
     * Services intermediate (`WE1`, valid to 2029-02-20) and root (`GTS Root R4`)
     * backup pins so 90-day leaf rotations do not break fiat price fetching
     * while still rejecting arbitrary CAs. The date 2028-01-28 is the GlobalSign
     * cross-sign of GTS Root R4, not the self-signed root (valid to 2036).
     *
     * Verify full chain with:
     * `openssl s_client -showcerts -servername api.coingecko.com -connect api.coingecko.com:443`
     */
    val coingeckoPins: List<String> =
        listOf(
            // Leaf: CN=api.coingecko.com (valid 2026-10-29 to 2027-01-27)
            "sha256/Sft1rLmODMaEPrqCVSoxXcU6zm6EsQO1COz7Bk7r4r4=",
            // Backup Intermediate: CN=WE1, O=Google Trust Services (valid to 2029-02-20)
            "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=",
            // Backup Root: CN=GTS Root R4, O=Google Trust Services LLC
            // (self-signed root valid to 2036; 2028-01-28 is the GlobalSign cross-sign)
            "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=",
        )

    /**
     * When SOCKS (Nym/Tor) is active, never call [Dns.SYSTEM] — that leaks the
     * real IP/DNS. Return a placeholder address so the SOCKS proxy resolves
     * the hostname remotely.
     */
    private val socksRemoteDns =
        Dns { hostname ->
            try {
                listOf(InetAddress.getByAddress(hostname, byteArrayOf(0, 0, 0, 0)))
            } catch (e: UnknownHostException) {
                throw UnknownHostException("SOCKS remote DNS placeholder for $hostname").initCause(e)
                    as UnknownHostException
            }
        }

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
                .apply { coingeckoPins.forEach { add(COINGECKO_HOST, it) } }
                .build()

        val builder =
            OkHttpClient
                .Builder()
                .certificatePinner(certificatePinner)
                .addInterceptor(
                    HttpLoggingInterceptor().setLevel(loggingLevel),
                )
        val prefs = DarkfiChatPreferences(app)
        val socksEndpoint = TorOutboundSocks.walletSocksEndpoint(prefs)
        if (socksEndpoint != null) {
            val effectiveNetwork = SessionTransport.effective(prefs)
            val isInProcessProxy =
                effectiveNetwork == PrivacyNetwork.Nym ||
                    (effectiveNetwork == PrivacyNetwork.Tor && prefs.useEmbeddedTor)
            builder.dns(socksRemoteDns)
            builder.socketFactory(
                Socks5AuthSocketFactory(
                    proxyAddress = socksEndpoint,
                    credentialsProvider = {
                        TransportNative.socksAuth()?.let { auth ->
                            Socks5Credentials(auth.username, auth.password)
                        }
                    },
                    requireAuth = isInProcessProxy,
                ),
            )
        }
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
