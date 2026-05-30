package com.nighthawkapps.lib.android.global

import android.net.Uri
import android.text.TextUtils
import android.util.Base64
import com.nighthawkapps.lib.android.spackle.Twig
import com.nighthawkapps.lib.android.ui.common.AMOUNT_QUERY
import com.nighthawkapps.lib.android.ui.common.MEMO_QUERY
import com.nighthawkapps.lib.android.ui.common.toAtomicDrk

object DeepLinkUtil {
    fun getSendDeepLinkData(uri: Uri): SendDeepLinkData? {
        // sample deep link: drk:<address>?amount=0.001&memo=c2RrZmp3cw
        try {
            if (TextUtils.isEmpty(uri.scheme)) return null
            if (TextUtils.isEmpty(uri.query)) return null // to check ?amount=
            val query = uri.query ?: ""
            val queryData = query.split("&") // to check memo
            if (queryData.isEmpty()) return null
            val amountString = queryData[0].replace("${AMOUNT_QUERY}=", "")
            val amountHuman = amountString.toDoubleOrNull() ?: return null
            val amountAtomic = amountHuman.toAtomicDrk()
            if (amountAtomic <= 0L) return null
            var memo: String? = null
            if (queryData.size > 1) {
                memo = queryData[1].replace("${MEMO_QUERY}=", "")
                memo = String(Base64.decode(memo, Base64.DEFAULT))
            }
            var uriString = uri.toString()
            uriString = uriString.removePrefix("${uri.scheme}:")
            uriString = uriString.replace("?$query", "") // address

            Twig.debug { "DeepLinkUtil: uri is: $uri address is $uriString amount is $amountAtomic memo is $memo" }

            return SendDeepLinkData(address = uriString, amount = amountAtomic, memo = memo)
        } catch (e: Exception) {
            Twig.debug { "DeepLinkUtil: Error in parsing deep link $uri and error is $e" }
            return null
        }
    }

    data class SendDeepLinkData(
        val address: String,
        val amount: Long?,
        val memo: String?
    )
}
