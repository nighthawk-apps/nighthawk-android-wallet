package com.nighthawkapps.lib.android.ui.design.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.nighthawkapps.lib.android.ui.design.theme.internal.DarkColorPalette
import com.nighthawkapps.lib.android.ui.design.theme.internal.DarkExtendedColorPalette
import com.nighthawkapps.lib.android.ui.design.theme.internal.ExtendedTypography
import com.nighthawkapps.lib.android.ui.design.theme.internal.LightColorPalette
import com.nighthawkapps.lib.android.ui.design.theme.internal.LightExtendedColorPalette
import com.nighthawkapps.lib.android.ui.design.theme.internal.LocalExtendedColors
import com.nighthawkapps.lib.android.ui.design.theme.internal.LocalExtendedTypography
import com.nighthawkapps.lib.android.ui.design.theme.internal.MidnightColorPalette
import com.nighthawkapps.lib.android.ui.design.theme.internal.MidnightExtendedColorPalette
import com.nighthawkapps.lib.android.ui.design.theme.internal.Typography

/**
 * Root Compose theme: **Stealth** (dark, default) uses moonlit neutrals + cool slate accent;
 * light mode uses the same accent family on mist neutrals; **Midnight** uses a deeper base with violet accent.
 */
@Composable
fun WalletTheme(
    themeVariant: AppThemeVariant = AppThemeVariant.STEALTH_DEFAULT,
    content: @Composable () -> Unit
) {
    val baseColors =
        when (themeVariant) {
            AppThemeVariant.LIGHT -> LightColorPalette
            AppThemeVariant.STEALTH_DEFAULT -> DarkColorPalette
            AppThemeVariant.MIDNIGHT -> MidnightColorPalette
        }

    val extendedColors =
        when (themeVariant) {
            AppThemeVariant.LIGHT -> LightExtendedColorPalette
            AppThemeVariant.STEALTH_DEFAULT -> DarkExtendedColorPalette
            AppThemeVariant.MIDNIGHT -> MidnightExtendedColorPalette
        }

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        ProvideDimens {
            MaterialTheme(
                colorScheme = baseColors,
                typography = Typography,
                content = content
            )
        }
    }
}

@Composable
fun WalletTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    WalletTheme(
        themeVariant =
            if (darkTheme) {
                AppThemeVariant.STEALTH_DEFAULT
            } else {
                AppThemeVariant.LIGHT
            },
        content = content,
    )
}

// Use with eg. WalletTheme.colors.tertiary
object WalletTheme {
    val colors: ExtendedColors
        @Composable
        get() = LocalExtendedColors.current

    val typography: ExtendedTypography
        @Composable
        get() = LocalExtendedTypography.current

    val dimens: Dimens
        @Composable
        get() = LocalDimens.current
}
