package com.nighthawkapps.lib.android.ui.screen.receiveqrcodes

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color

sealed class QRAddressPagerItem(
    val title: String,
    val body: String,
    val buttonText: String,
    val logoId: Int,
    val backgroundColor: Color
) {
    data class PRIVATE_ADDRESS(
        val addressType: String,
        val address: String,
        val btnText: String,
        @DrawableRes val id: Int,
        val backGroundColor: Color
    ) : QRAddressPagerItem(addressType, address, btnText, id, backGroundColor)

    data class CREATE_NEW_ADDRESS(
        val titleText: String,
        val bodyText: String,
        val btnText: String,
        @DrawableRes val id: Int,
        val backGroundColor: Color
    ) : QRAddressPagerItem(titleText, bodyText, btnText, id, backGroundColor)

    data class TOP_UP(
        val titleText: String,
        val bodyText: String,
        val btnText: String,
        @DrawableRes val id: Int,
        val backGroundColor: Color
    ) : QRAddressPagerItem(titleText, bodyText, btnText, id, backGroundColor)
}
