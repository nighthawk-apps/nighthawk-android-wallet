package com.nighthawkapps.lib.android.global

import android.net.Uri
import android.util.Base64
import com.nighthawkapps.lib.android.sdk.wallet.DarkfiPaymentMemo
import com.nighthawkapps.lib.android.spackle.Twig
import com.nighthawkapps.lib.android.ui.common.AMOUNT_QUERY
import com.nighthawkapps.lib.android.ui.common.MEMO_QUERY
import com.nighthawkapps.lib.android.ui.common.toAtomicDrk

object DeepLinkUtil {
    private const val SCHEME = "drk"
    private const val MAX_ADDRESS_LENGTH = 256
    private const val MAX_AMOUNT_HUMAN = 21_000_000.0

    fun getSendDeepLinkData(uri: Uri): SendDeepLinkData? {
        // sample deep link: drk:<address>?amount=0.001&memo=c2RrZmp3cw
        return try {
            if (uri.scheme?.equals(SCHEME, ignoreCase = true) != true) return null

            val withoutScheme = uri.toString().removePrefix("${uri.scheme}:")
            val queryStart = withoutScheme.indexOf('?')
            val addressRaw =
                if (queryStart >= 0) {
                    withoutScheme.substring(0, queryStart)
                } else {
                    withoutScheme
                }
            val address =
                addressRaw
                    .removePrefix("//")
                    .trim()
                    .takeIf { it.isNotBlank() && it.length <= MAX_ADDRESS_LENGTH }
                    ?.takeIf { it.none { ch -> ch.isISOControl() || ch.isWhitespace() } }
                    ?: return null

            val queryString =
                when {
                    !uri.query.isNullOrBlank() -> uri.query
                    queryStart >= 0 -> withoutScheme.substring(queryStart + 1)
                    else -> null
                }
            val queryUri =
                if (queryString.isNullOrBlank()) {
                    null
                } else {
                    Uri.parse("https://local/?$queryString")
                }

            val amountParam = queryUri?.getQueryParameter(AMOUNT_QUERY)
            val amountAtomic =
                if (amountParam.isNullOrBlank()) {
                    null
                } else {
                    val amountHuman = amountParam.toDoubleOrNull() ?: return null
                    if (amountHuman <= 0.0 || amountHuman > MAX_AMOUNT_HUMAN) return null
                    amountHuman.toAtomicDrk().takeIf { it > 0L } ?: return null
                }

            val memoParam = queryUri?.getQueryParameter(MEMO_QUERY)
            val memo =
                if (memoParam.isNullOrBlank()) {
                    null
                } else {
                    runCatching {
                        String(Base64.decode(memoParam, Base64.DEFAULT), Charsets.UTF_8)
                    }.getOrNull()
                        ?.takeIf {
                            DarkfiPaymentMemo.utf8Size(it) <= DarkfiPaymentMemo.MAX_BYTES &&
                                it.none(Char::isISOControl)
                        }
                }

            SendDeepLinkData(address = address, amount = amountAtomic, memo = memo)
        } catch (e: Exception) {
            Twig.debug { "DeepLinkUtil: rejected deep link (${e.javaClass.simpleName})" }
            null
        }
    }

    data class SendDeepLinkData(
        val address: String,
        val amount: Long?,
        val memo: String?,
    )
}
