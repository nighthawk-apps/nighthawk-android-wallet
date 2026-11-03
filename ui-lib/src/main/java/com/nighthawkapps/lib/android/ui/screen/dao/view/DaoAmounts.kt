package com.nighthawkapps.lib.android.ui.screen.dao.view

import java.math.BigDecimal

internal const val DAO_AMOUNT_DECIMALS = 8

internal fun positiveDaoAmount(raw: String): BigDecimal? {
    val normalized =
        raw
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.let { runCatching { BigDecimal(it) }.getOrNull() }
            ?.stripTrailingZeros()
    return normalized?.takeIf { it.signum() > 0 && it.scale() <= DAO_AMOUNT_DECIMALS }
}

internal fun yesShareRatio(raw: String): BigDecimal? {
    val value =
        raw
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.let { runCatching { BigDecimal(it) }.getOrNull() }
    return value?.takeIf { it.signum() > 0 && it <= BigDecimal.ONE }
}

internal fun BigDecimal.plainAmount(): String = stripTrailingZeros().toPlainString()
