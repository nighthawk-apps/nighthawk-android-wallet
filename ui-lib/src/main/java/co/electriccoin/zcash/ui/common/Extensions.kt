package co.electriccoin.zcash.ui.common

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
import cash.z.ecc.android.sdk.ext.convertZatoshiToZec
import cash.z.ecc.android.sdk.ext.isShielded
import cash.z.ecc.android.sdk.ext.toZecString
import cash.z.ecc.android.sdk.model.Zatoshi
import cash.z.ecc.android.sdk.model.ZcashNetwork
import cash.z.ecc.android.sdk.model.toZecString
import co.electriccoin.zcash.ui.R
import co.electriccoin.zcash.ui.screen.fiatcurrency.model.FiatCurrency
import co.electriccoin.zcash.ui.screen.fiatcurrency.model.FiatCurrencyUiState
import co.electriccoin.zcash.ui.screen.wallet.model.BalanceUIModel
import co.electriccoin.zcash.ui.screen.wallet.model.BalanceValuesModel
import java.math.BigDecimal

internal fun ComponentActivity.onLaunchUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (t: Throwable) {
        print("Warning: failed to open browser due to $t")
    }
}

@Composable
internal fun TextFieldDefaults.customColors(): TextFieldColors {
    return this.colors(
        focusedTextColor = Color.White,
        focusedIndicatorColor = Color.White,
        unfocusedIndicatorColor = Color.White,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent
    )
}

internal fun Context.isBioMetricEnabledOnMobile(): Boolean {
    return BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
}

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

internal fun FragmentActivity.authenticate(description: String, title: String, block: () -> Unit) {
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            block()
        }
        override fun onAuthenticationFailed() {
            showMessage("Authentication failed :(")
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            fun doNothing(message: String) {
                showMessage(message)
            }
            when (errorCode) {
                BiometricPrompt.ERROR_HW_NOT_PRESENT, BiometricPrompt.ERROR_HW_UNAVAILABLE,
                BiometricPrompt.ERROR_NO_BIOMETRICS, BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL -> {
                    showMessage("Please enable screen lock on this device to add security here!")
                    block()
                }
                BiometricPrompt.ERROR_LOCKOUT -> doNothing("Too many attempts. Try again in 30s.")
                BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> doNothing("Whoa. Waaaay too many attempts!")
                BiometricPrompt.ERROR_CANCELED -> doNothing("I just can't right now. Please try again.")
                BiometricPrompt.ERROR_NEGATIVE_BUTTON -> doNothing("Authentication cancelled")
                BiometricPrompt.ERROR_USER_CANCELED -> doNothing("Face/Touch ID Authentication Cancelled")
                BiometricPrompt.ERROR_NO_SPACE -> doNothing("Not enough storage space!")
                BiometricPrompt.ERROR_TIMEOUT -> doNothing("Oops. It timed out.")
                BiometricPrompt.ERROR_UNABLE_TO_PROCESS -> doNothing(".")
                BiometricPrompt.ERROR_VENDOR -> doNothing("We got some weird error and you should report this.")
                else -> {
                    doNothing("Authentication failed with error code $errorCode")
                }
            }
        }
    }

    BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback).apply {
        authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setConfirmationRequired(false)
                .setDescription(description)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
        )
    }
}

internal fun ZcashNetwork?.blockExplorerUrlStringId(): Int {
    return when (this) {
        ZcashNetwork.Mainnet -> R.string.ns_block_explorer_url_main_net
        else -> R.string.ns_block_explorer_url_testnet
    }
}

internal fun String.addressTypeNameId(): Int {
    return if (this.isShielded()) {
        R.string.ns_legacy_shielded_sapling
    } else if (this.startsWith("u")) {
        R.string.ns_shielded
    } else {
        R.string.ns_transparent
    }
}

internal fun Zatoshi.toFiatPriceWithCurrencyUnit(fiatCurrencyUiState: FiatCurrencyUiState): String {
    if (fiatCurrencyUiState.fiatCurrency != FiatCurrency.OFF) {
        fiatCurrencyUiState.price?.let {
            return this.toFiatPrice(fiatCurrencyUiState) + " ${fiatCurrencyUiState.fiatCurrency.currencyName}"
        }
    }
    return ""
}

internal fun Zatoshi.toFiatPrice(fiatCurrencyUiState: FiatCurrencyUiState): String {
    if (fiatCurrencyUiState.fiatCurrency != FiatCurrency.OFF) {
        fiatCurrencyUiState.price?.let {
            return this.convertZatoshiToZec().multiply(BigDecimal(it)).toZecString(maxDecimals = 2)
        }
    }
    return ""
}

internal fun Zatoshi.toBalanceValueModel(
    fiatCurrencyUiState: FiatCurrencyUiState,
    isFiatCurrencyPreferred: Boolean,
    selectedDenomination: String = "ZEC"): BalanceValuesModel {
    val isLocalCurrencySelectedAsPrimary = isFiatCurrencyPreferred && fiatCurrencyUiState.fiatCurrency != FiatCurrency.OFF
    val availableBalance = this
    val balance: String
    val balanceUnit: String
    val fiatBalance: String
    val fiatUnit: String
    if (isLocalCurrencySelectedAsPrimary) {
        balance = availableBalance.toFiatPrice(fiatCurrencyUiState)
        balanceUnit = fiatCurrencyUiState.fiatCurrency.currencyName
        fiatBalance = availableBalance.toZecString()
        fiatUnit = selectedDenomination
    } else {
        balance = availableBalance.toZecString()
        balanceUnit = selectedDenomination
        fiatBalance = availableBalance.toFiatPrice(fiatCurrencyUiState)
        fiatUnit = fiatCurrencyUiState.fiatCurrency.currencyName
    }
    return BalanceValuesModel(balance = balance, balanceUnit = balanceUnit, fiatBalance = fiatBalance, fiatUnit = fiatUnit)
}

internal fun BalanceValuesModel.toBalanceUiModel(context: Context): BalanceUIModel {
    val fiatValue = context.getString(
        R.string.ns_around,
        fiatBalance
    ).takeIf { fiatBalance.isNotBlank() } ?: ""
    return BalanceUIModel(
        balance = balance,
        balanceUnit = balanceUnit,
        fiatBalance = fiatValue,
        fiatUnit = fiatUnit
    )
}
