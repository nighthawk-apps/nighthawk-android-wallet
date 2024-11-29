package co.electriccoin.zcash.ui.common

import android.content.Context
import cash.z.ecc.android.sdk.ext.convertZatoshiToZec
import cash.z.ecc.android.sdk.model.Zatoshi
import co.electriccoin.zcash.spackle.Twig
import co.electriccoin.zcash.ui.BuildConfig
import com.flexa.core.Flexa
import com.flexa.core.shared.AssetAccount
import com.flexa.core.shared.AvailableAsset
import com.flexa.core.shared.CustodyModel
import com.flexa.core.shared.FlexaClientConfiguration
import com.flexa.core.theme.FlexaTheme
import com.flexa.identity.toSha256

object FlexaHelper {
    fun initFlexaSdk(context: Context) {
        Flexa.init(
            FlexaClientConfiguration(
                context = context.applicationContext,
                publishableKey = BuildConfig.FEXA_PUBLISHABLE_KEY,
                theme = FlexaTheme(
                    useDynamicColorScheme = true,
                ),
                assetAccounts = arrayListOf(
                    createFlexaAccount(0.0)
                ),
                webViewThemeConfig = "{\n" +
                        "    \"android\": {\n" +
                        "        \"light\": {\n" +
                        "            \"backgroundColor\": \"#100e29\",\n" +
                        "            \"sortTextColor\": \"#ed7f60\",\n" +
                        "            \"titleColor\": \"#ffffff\",\n" +
                        "            \"cardColor\": \"#2a254e\",\n" +
                        "            \"borderRadius\": \"15px\",\n" +
                        "            \"textColor\": \"#ffffff\"\n" +
                        "        },\n" +
                        "        \"dark\": {\n" +
                        "            \"backgroundColor\": \"#100e29\",\n" +
                        "            \"sortTextColor\": \"#ed7f60\",\n" +
                        "            \"titleColor\": \"#ffffff\",\n" +
                        "            \"cardColor\": \"#2a254e\",\n" +
                        "            \"borderRadius\": \"15px\",\n" +
                        "            \"textColor\": \"#ffffff\"\n" +
                        "        }\n" +
                        "    }\n" +
                        "}"
            )
        )
    }

    fun updateFlexaAccount(totalBalance: Zatoshi) {
        Flexa.updateAssetAccounts(
            arrayListOf(
                createFlexaAccount(
                    zecBalance = totalBalance.convertZatoshiToZec().toDouble()
                )
            )
        )
        Twig.debug {
            "Flexa Account updated with zec = ${
                totalBalance.convertZatoshiToZec().toDouble()
            }"
        }
    }

    private fun createFlexaAccount(zecBalance: Double) =
        AssetAccount(
            assetAccountHash = "1".toSha256(),
            displayName = "My Wallet",
            icon = "https://flexa.network/static/4bbb1733b3ef41240ca0f0675502c4f7/d8419/flexa-logo%403x.png",
            availableAssets =
            listOf(
                AvailableAsset(
                    assetId = "bip122:00040fe8ec8471911baa1db1266ea15d/slip44:133",
                    balance = zecBalance,
                    symbol = "ZEC",
                )
            ),
            custodyModel = CustodyModel.LOCAL
        )

}
