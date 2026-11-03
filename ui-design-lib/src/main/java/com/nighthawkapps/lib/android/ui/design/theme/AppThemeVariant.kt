package com.nighthawkapps.lib.android.ui.design.theme

/**
 * User-selectable visual themes. **Cyberpunk** is the default for a light or
 * dark system theme. **Stealth** is the moonlit dark palette; **Light** is the
 * mist palette; **Paper** is a white reading theme; **Midnight** is a darker
 * violet-accent variant.
 */
enum class AppThemeVariant {
    STEALTH_DEFAULT,
    LIGHT,
    PAPER,
    MIDNIGHT,
    CYBERPUNK,
    ;

    companion object {
        fun fromStorage(raw: String?): AppThemeVariant? =
            when (raw?.trim()?.lowercase()) {
                "", null -> null
                "light" -> LIGHT
                "paper", "white" -> PAPER
                "midnight" -> MIDNIGHT
                "cyberpunk" -> CYBERPUNK
                "stealth_default", "default", "stealth", "dark" -> STEALTH_DEFAULT
                else -> STEALTH_DEFAULT
            }

        fun storageValue(variant: AppThemeVariant): String =
            when (variant) {
                STEALTH_DEFAULT -> "stealth_default"
                LIGHT -> "light"
                PAPER -> "paper"
                MIDNIGHT -> "midnight"
                CYBERPUNK -> "cyberpunk"
            }

        /**
         * Explicit [stored] wins. A legacy dark-theme flag wins when it was
         * actually written. With neither set, Cyberpunk is used for both a
         * light and a dark system theme.
         */
        @Suppress("UNUSED_PARAMETER")
        fun resolve(
            stored: String?,
            legacyDarkStored: String?,
            systemDark: Boolean,
        ): AppThemeVariant =
            fromStorage(stored)
                ?: when (legacyDarkStored?.trim()?.lowercase()) {
                    "false" -> PAPER
                    "true" -> STEALTH_DEFAULT
                    else -> CYBERPUNK
                }
    }
}
