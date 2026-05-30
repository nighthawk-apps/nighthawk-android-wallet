package com.nighthawkapps.lib.android.ui.design.theme

/**
 * User-selectable visual themes. **Stealth** is the default dark branding;
 * **Light** is the mist palette; **Midnight** is a darker violet-accent variant.
 */
enum class AppThemeVariant {
    STEALTH_DEFAULT,
    LIGHT,
    MIDNIGHT,
    ;

    companion object {
        fun fromStorage(raw: String?): AppThemeVariant? =
            when (raw?.trim()?.lowercase()) {
                "", null -> null
                "light" -> LIGHT
                "midnight" -> MIDNIGHT
                "stealth_default", "default", "stealth", "dark" -> STEALTH_DEFAULT
                else -> STEALTH_DEFAULT
            }

        fun storageValue(variant: AppThemeVariant): String =
            when (variant) {
                STEALTH_DEFAULT -> "stealth_default"
                LIGHT -> "light"
                MIDNIGHT -> "midnight"
            }

        fun resolve(
            stored: String,
            legacyDark: Boolean?
        ): AppThemeVariant =
            fromStorage(stored)
                ?: when (legacyDark) {
                    false -> LIGHT
                    else -> STEALTH_DEFAULT
                }
    }
}
