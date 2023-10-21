package co.electriccoin.zcash.ui.screen.advancesetting.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import co.electriccoin.zcash.ui.R

enum class AvailableLogo(val logoNo: Int, @DrawableRes val logoId: Int, @StringRes val nameId: Int) {
   DEFAULT(logoNo = 0, logoId = R.drawable.ic_nighthawk_logo_white, nameId = R.string.default_name),
   LEGACY(logoNo = 1, logoId = R.drawable.legacy_logo, nameId = R.string.retro);

    companion object {
        fun getAvailableLogo(logoNo: Int?): AvailableLogo {
            return when(logoNo) {
                LEGACY.logoNo -> LEGACY
                else -> DEFAULT
            }
        }
    }
}
