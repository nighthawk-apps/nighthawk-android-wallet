package com.nighthawkapps.lib.android.ui.screen.support.model

import android.content.Context
import com.nighthawkapps.lib.android.global.StorageChecker
import java.text.DecimalFormatSymbols
import java.util.Locale

data class MonetarySeparators(
    val grouping: Char,
    val decimal: Char,
) {
    companion object {
        fun current(): MonetarySeparators {
            val symbols = DecimalFormatSymbols.getInstance()
            return MonetarySeparators(symbols.groupingSeparator, symbols.decimalSeparator)
        }
    }
}

data class EnvironmentInfo(
    val locale: Locale,
    val monetarySeparators: MonetarySeparators,
    val usableStorageMegabytes: Int
) {
    fun toSupportString() =
        buildString {
            appendLine("Locale: ${locale.androidResName()}")
            appendLine("Currency grouping separator: ${monetarySeparators.grouping}")
            appendLine("Currency decimal separator: ${monetarySeparators.decimal}")
            appendLine("Usable storage: $usableStorageMegabytes MB")
        }

    companion object {
        suspend fun new(context: Context): EnvironmentInfo {
            val usableStorage = StorageChecker.checkAvailableStorageMegabytes()

            return EnvironmentInfo(
                context.resources.configuration.locales[0],
                MonetarySeparators.current(),
                usableStorage
            )
        }
    }
}

private fun Locale.androidResName() = "$language-$country"
