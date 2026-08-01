@file:Suppress("ktlint:standard:filename")

package com.nighthawkapps.lib.android.ui.common

import androidx.compose.ui.text.intl.Locale
import java.util.Locale as JavaLocale

fun Locale.toKotlinLocale(): JavaLocale {
    val builder = JavaLocale.Builder().setLanguage(language)
    if (region.isNotBlank()) {
        builder.setRegion(region)
    }
    if (script.isNotBlank()) {
        builder.setScript(script)
    }
    return builder.build()
}
