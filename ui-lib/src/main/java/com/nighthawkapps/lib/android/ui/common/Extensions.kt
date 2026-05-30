@file:Suppress("TooManyFunctions")

package com.nighthawkapps.lib.android.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiAmountFormatter
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiNetwork
import com.nighthawkapps.lib.android.sdk.wallet.darkfiNetworkFromPackage
import com.nighthawkapps.lib.android.spackle.Twig
import com.nighthawkapps.lib.android.ui.R
import com.nighthawkapps.lib.android.ui.screen.fiatcurrency.model.FiatCurrency
import com.nighthawkapps.lib.android.ui.screen.fiatcurrency.model.FiatCurrencyUiState
import com.nighthawkapps.lib.android.ui.screen.wallet.model.BalanceUIModel
import com.nighthawkapps.lib.android.ui.screen.wallet.model.BalanceValuesModel
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private const val DRK_DECIMAL_SCALE = 8

internal fun ComponentActivity.onLaunchUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (t: Throwable) {
        print("Warning: failed to open browser due to $t")
    }
}

@Composable
internal fun TextFieldDefaults.customColors(): TextFieldColors =
    this.colors(
        focusedTextColor = Color.White,
        focusedIndicatorColor = Color.White,
        unfocusedIndicatorColor = Color.White,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
    )

internal fun Context.isBioMetricEnabledOnMobile(): Boolean =
    BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
        BiometricManager.BIOMETRIC_SUCCESS

internal fun Context.showMessage(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

internal fun ByteArray.toFormattedString(): String {
    var txId = ""
    for (i in (this.size - 1) downTo 0) {
        txId += String.format("%02x", this[i])
    }
    return txId
}

internal fun FragmentActivity.authenticate(
    description: String,
    title: String,
    block: () -> Unit
) {
    val callback =
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                block()
            }

            override fun onAuthenticationFailed() {
                showMessage("Authentication failed :(")
            }

            override fun onAuthenticationError(
                errorCode: Int,
                errString: CharSequence
            ) {
                fun doNothing(message: String) {
                    showMessage(message)
                }
                when (errorCode) {
                    BiometricPrompt.ERROR_HW_NOT_PRESENT, BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    BiometricPrompt.ERROR_NO_BIOMETRICS, BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                    -> {
                        showMessage("Please enable screen lock on this device to add security here!")
                        block()
                    }

                    BiometricPrompt.ERROR_LOCKOUT -> {
                        doNothing("Too many attempts. Try again in 30s.")
                    }

                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> {
                        doNothing("Whoa. Waaaay too many attempts!")
                    }

                    BiometricPrompt.ERROR_CANCELED -> {
                        doNothing("I just can't right now. Please try again.")
                    }

                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> {
                        doNothing("Authentication cancelled")
                    }

                    BiometricPrompt.ERROR_USER_CANCELED -> {
                        doNothing("Face/Touch ID Authentication Cancelled")
                    }

                    BiometricPrompt.ERROR_NO_SPACE -> {
                        doNothing("Not enough storage space!")
                    }

                    BiometricPrompt.ERROR_TIMEOUT -> {
                        doNothing("Oops. It timed out.")
                    }

                    BiometricPrompt.ERROR_UNABLE_TO_PROCESS -> {
                        doNothing(".")
                    }

                    BiometricPrompt.ERROR_VENDOR -> {
                        doNothing("We got some weird error and you should report this.")
                    }

                    else -> {
                        doNothing("Authentication failed with error code $errorCode")
                    }
                }
            }
        }

    BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback).apply {
        authenticate(
            BiometricPrompt.PromptInfo
                .Builder()
                .setTitle(title)
                .setConfirmationRequired(false)
                .setDescription(description)
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                ).build(),
        )
    }
}

internal fun Context.darkfiBlockExplorerUrlStringId(): Int =
    when (darkfiNetworkFromPackage(this)) {
        DarkfiNetwork.Mainnet -> R.string.ns_block_explorer_url_main_net
        DarkfiNetwork.Testnet -> R.string.ns_block_explorer_url_testnet
    }

internal fun DarkfiNetwork?.darkfiExplorerUrlStringId(): Int =
    when (this) {
        DarkfiNetwork.Mainnet -> R.string.ns_block_explorer_url_main_net
        DarkfiNetwork.Testnet -> R.string.ns_block_explorer_url_testnet
        null -> R.string.ns_block_explorer_url_testnet
    }

internal fun String.addressTypeNameId(): Int {
    val v = this.lowercase()
    return when {
        v.startsWith("drk_u_") || v.startsWith("drk") -> R.string.ns_address_kind_confidential
        v.startsWith("u") -> R.string.ns_address_kind_confidential
        else -> R.string.ns_address_kind_public_receive
    }
}

internal fun Double.toFiatAtomicDrk(
    fiatCurrencyUiState: FiatCurrencyUiState,
    isFiatCurrencyPreferredOverDrk: Boolean
): Long? {
    if (isFiatCurrencyPreferredOverDrk) {
        if (fiatCurrencyUiState.price == null || fiatCurrencyUiState.price == 0.0) return null
        val humanDrk = this / fiatCurrencyUiState.price
        return humanDrk.toAtomicDrk()
    }
    return this.toAtomicDrk()
}

internal fun Double.toAtomicDrk(): Long =
    BigDecimal
        .valueOf(this)
        .movePointRight(DRK_DECIMAL_SCALE)
        .setScale(0, RoundingMode.DOWN)
        .longValueExact()

internal fun Long.toFiatPriceDisplay(fiatCurrencyUiState: FiatCurrencyUiState): String {
    if (fiatCurrencyUiState.fiatCurrency != FiatCurrency.OFF) {
        fiatCurrencyUiState.price?.let { price ->
            val human = BigDecimal(this).movePointLeft(DRK_DECIMAL_SCALE)
            return human.multiply(BigDecimal(price)).setScale(2, RoundingMode.HALF_UP).toPlainString()
        }
    }
    return ""
}

internal fun Long.formatAtomicDrkForEntry(): String = DarkfiAmountFormatter.formatAtomic(this, DRK_DECIMAL_SCALE)

internal fun Long.toBalanceValueModel(
    fiatCurrencyUiState: FiatCurrencyUiState,
    isFiatCurrencyPreferred: Boolean,
    selectedDenomination: String = "DRK",
): BalanceValuesModel {
    val isLocalCurrencySelectedAsPrimary =
        isFiatCurrencyPreferred && fiatCurrencyUiState.fiatCurrency != FiatCurrency.OFF
    val balance: String
    val balanceUnit: String
    val fiatBalance: String
    val fiatUnit: String
    if (isLocalCurrencySelectedAsPrimary) {
        balance = this.toFiatPriceDisplay(fiatCurrencyUiState).removeTrailingZero()
        balanceUnit = fiatCurrencyUiState.fiatCurrency.currencyName
        fiatBalance = this.formatAtomicDrkForEntry().removeTrailingZero().replace(",", ".")
        fiatUnit = selectedDenomination
    } else {
        balance = this.formatAtomicDrkForEntry().removeTrailingZero().replace(",", ".")
        balanceUnit = selectedDenomination
        fiatBalance = this.toFiatPriceDisplay(fiatCurrencyUiState).removeTrailingZero()
        fiatUnit = fiatCurrencyUiState.fiatCurrency.currencyName
    }
    return BalanceValuesModel(
        balance = balance,
        balanceUnit = balanceUnit,
        fiatBalance = fiatBalance,
        fiatUnit = fiatUnit,
    )
}

internal fun BalanceValuesModel.toBalanceUiModel(context: Context): BalanceUIModel {
    val fiatValue =
        context
            .getString(
                R.string.ns_around,
                fiatBalance,
            ).takeIf { fiatBalance.isNotBlank() } ?: ""
    return BalanceUIModel(
        balance = balance,
        balanceUnit = balanceUnit,
        fiatBalance = fiatValue,
        fiatUnit = fiatUnit,
    )
}

internal fun String.removeTrailingZero(): String =
    try {
        this.toDoubleOrNull()?.let {
            DecimalFormat("0.########", DecimalFormatSymbols.getInstance(Locale.US)).format(it)
        } ?: this
    } catch (e: Exception) {
        Twig.error { "Exception in formatting value $this" }
        this
    }
