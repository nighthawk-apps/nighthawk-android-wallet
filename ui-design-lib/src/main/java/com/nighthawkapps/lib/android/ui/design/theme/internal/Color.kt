@file:Suppress("MagicNumber")

package com.nighthawkapps.lib.android.ui.design.theme.internal

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.nighthawkapps.lib.android.ui.design.theme.ExtendedColors

/**
 * Single source for stealth / infrastructure-first hues (Compose).
 * XML colors under `ui-design-lib` should stay aligned for legacy `colorResource` call sites.
 */
private object StealthTokens {
    val moonlit = Color(0xFF0E1012)
    val charcoalDeep = Color(0xFF12161B)
    val charcoalRaised = Color(0xFF171C22)
    val elevated = Color(0xFF1F252D)
    val steelBorder = Color(0xFF343D47)
    val steelBorderMuted = Color(0xFF283038)

    val accent = Color(0xFF5E9BAF)
    val accentPressed = Color(0xFF4F8799)
    val accentDeep = Color(0xFF3F6F80)
    val accentMuted = Color(0xFF7DADB9)

    /** Near-black on accent fills for crisp contrast without neon white glare */
    val onAccent = Color(0xFF081018)

    val secondaryFill = Color(0xFF252D36)
    val secondaryFillHighlight = Color(0xFF323B46)

    val inkPanel = Color(0xFF15191E)
    val inkPanelLift = Color(0xFF1E252C)

    val textHeader = Color(0xFFE8EBEF)
    val textBody = Color(0xFFC4CBD4)
    val textMuted = Color(0xFF8F98A3)
    val navigationMuted = Color(0xFFA8B2BD)

    val trackMuted = Color(0xFF2F3740)
    val calloutFill = Color(0xFF232A32)
    val calloutOn = Color(0xFFB7C0CA)
    val accentSubtleContainer = Color(0xFF243038)
}

internal object Dark {
    val backgroundStart = StealthTokens.moonlit
    val backgroundEnd = StealthTokens.charcoalDeep

    val surface = StealthTokens.charcoalRaised
    val surfaceVariant = StealthTokens.elevated
    val outline = StealthTokens.steelBorder
    val outlineVariant = StealthTokens.steelBorderMuted

    val textHeaderOnBackground = StealthTokens.textHeader
    val textBodyOnBackground = StealthTokens.textBody
    val textPrimaryButton = StealthTokens.onAccent
    val textSecondaryButton = StealthTokens.textBody
    val textTertiaryButton = StealthTokens.textHeader
    val textNavigationButton = StealthTokens.textBody
    val textCaption = StealthTokens.textMuted
    val textChipIndex = StealthTokens.textMuted

    val primaryButton = StealthTokens.accent
    val primaryButtonPressed = StealthTokens.accentPressed
    val primaryButtonDisabled = StealthTokens.accentDeep

    val primaryContainer = StealthTokens.accentSubtleContainer
    val onPrimaryContainer = StealthTokens.textHeader

    val secondaryButton = StealthTokens.secondaryFill
    val secondaryButtonPressed = StealthTokens.secondaryFillHighlight
    val secondaryButtonDisabled = StealthTokens.secondaryFill.copy(alpha = 0.45f)

    val secondaryContainer = StealthTokens.secondaryFillHighlight
    val onSecondaryContainer = StealthTokens.textBody

    val tertiaryButton = StealthTokens.inkPanel
    val tertiaryButtonPressed = StealthTokens.inkPanelLift

    val navigationButton = StealthTokens.secondaryFill
    val navigationButtonPressed = StealthTokens.secondaryFillHighlight
    val navigationIcon = StealthTokens.navigationMuted

    val progressStart = StealthTokens.accent
    val progressEnd = StealthTokens.accentDeep
    val progressBackground = StealthTokens.trackMuted

    val callout = StealthTokens.calloutFill
    val onCallout = StealthTokens.calloutOn

    val overlay = Color(0x66000000)
    val highlight = Color(0x14FFFFFF)

    val addressHighlightBorder = StealthTokens.steelBorder
    val addressHighlightUnified = StealthTokens.textHeader
    val addressHighlightConfidential = StealthTokens.textMuted
    val addressHighlightPublic = StealthTokens.accentMuted

    val dangerous = Color(0xFFCF6679)
    val onDangerous = Color(0xFF1C1B1F)

    val reference = StealthTokens.accentMuted
    val divider = StealthTokens.steelBorder
    val navigationContainer = StealthTokens.moonlit
    val selectedPageIndicator = StealthTokens.accent
    val secondaryTitleText = StealthTokens.textMuted
}

private object LightStealthTokens {
    val mist = Color(0xFFE8EAED)
    val mistEnd = Color(0xFFDDE2E8)
    val surface = Color(0xFFF7F8FA)
    val surfaceVariant = Color(0xFFEEF1F5)
    val outline = Color(0xFFC5CCD4)
    val outlineVariant = Color(0xFFADB6BF)

    val accent = Color(0xFF4F8FA5)
    val accentPressed = Color(0xFF3F7A8F)
    val accentDeep = Color(0xFF356578)
    val onAccent = Color(0xFFF8FAFB)

    val ink = Color(0xFF1F2832)
    val inkMuted = Color(0xFF5C6570)
    val inkSoft = Color(0xFF7A8490)

    val secondaryFill = Color(0xFFD7DEE6)
    val secondaryFillHighlight = Color(0xFFC9D2DC)

    val calloutFill = Color(0xFFE4E9EF)
    val calloutOn = inkMuted

    val tertiaryPanel = Color(0xFF2B3440)
}

internal object Light {
    val backgroundStart = LightStealthTokens.mist
    val backgroundEnd = LightStealthTokens.mistEnd

    val surface = LightStealthTokens.surface
    val surfaceVariant = LightStealthTokens.surfaceVariant
    val outline = LightStealthTokens.outline
    val outlineVariant = LightStealthTokens.outlineVariant

    val textHeaderOnBackground = LightStealthTokens.accent
    val textBodyOnBackground = LightStealthTokens.ink
    val textNavigationButton = LightStealthTokens.inkMuted
    val textPrimaryButton = LightStealthTokens.onAccent
    val textSecondaryButton = LightStealthTokens.ink
    val textTertiaryButton = LightStealthTokens.accent
    val textCaption = LightStealthTokens.inkSoft
    val textChipIndex = LightStealthTokens.accentDeep

    val primaryButton = LightStealthTokens.accent
    val primaryButtonPressed = LightStealthTokens.accentPressed
    val primaryButtonDisabled = LightStealthTokens.accentDeep.copy(alpha = 0.45f)

    val primaryContainer = LightStealthTokens.surfaceVariant
    val onPrimaryContainer = LightStealthTokens.ink

    val secondaryButton = LightStealthTokens.secondaryFill
    val secondaryButtonPressed = LightStealthTokens.secondaryFillHighlight
    val secondaryButtonDisabled = LightStealthTokens.secondaryFill.copy(alpha = 0.45f)

    val secondaryContainer = LightStealthTokens.secondaryFillHighlight
    val onSecondaryContainer = LightStealthTokens.ink

    val tertiaryButton = LightStealthTokens.tertiaryPanel
    val tertiaryButtonPressed = LightStealthTokens.ink

    val navigationButton = LightStealthTokens.secondaryFill
    val navigationButtonPressed = LightStealthTokens.secondaryFillHighlight
    val navigationIcon = LightStealthTokens.ink

    val progressStart = LightStealthTokens.accent
    val progressEnd = LightStealthTokens.accentDeep
    val progressBackground = LightStealthTokens.outlineVariant

    val callout = LightStealthTokens.calloutFill
    val onCallout = LightStealthTokens.calloutOn

    val overlay = Color(0x33000000)
    val highlight = LightStealthTokens.accent.copy(alpha = 0.22f)

    val addressHighlightBorder = Color(0xFF525252)
    val addressHighlightUnified = LightStealthTokens.accent.copy(alpha = 0.35f)
    val addressHighlightConfidential = LightStealthTokens.accentDeep
    val addressHighlightPublic = LightStealthTokens.inkMuted

    val dangerous = Color(0xFFB3261E)
    val onDangerous = Color(0xFFFFFFFF)

    val reference = LightStealthTokens.accent
    val divider = LightStealthTokens.outline
    val navigationContainer = LightStealthTokens.tertiaryPanel
    val selectedPageIndicator = LightStealthTokens.accent
    val secondaryTitleText = LightStealthTokens.inkMuted
}

internal val DarkColorPalette =
    darkColorScheme(
        primary = Dark.primaryButton,
        onPrimary = Dark.textPrimaryButton,
        primaryContainer = Dark.primaryContainer,
        onPrimaryContainer = Dark.onPrimaryContainer,
        secondary = Dark.secondaryButton,
        onSecondary = Dark.textSecondaryButton,
        secondaryContainer = Dark.secondaryContainer,
        onSecondaryContainer = Dark.onSecondaryContainer,
        tertiary = Dark.tertiaryButton,
        onTertiary = Dark.textTertiaryButton,
        background = Dark.backgroundStart,
        onBackground = Dark.textBodyOnBackground,
        surface = Dark.surface,
        onSurface = Dark.textBodyOnBackground,
        surfaceVariant = Dark.surfaceVariant,
        onSurfaceVariant = Dark.textCaption,
        outline = Dark.outline,
        outlineVariant = Dark.outlineVariant,
        error = Dark.dangerous,
        onError = Dark.onDangerous,
    )

internal val LightColorPalette =
    lightColorScheme(
        primary = Light.primaryButton,
        onPrimary = Light.textPrimaryButton,
        primaryContainer = Light.primaryContainer,
        onPrimaryContainer = Light.onPrimaryContainer,
        secondary = Light.secondaryButton,
        onSecondary = Light.textSecondaryButton,
        secondaryContainer = Light.secondaryContainer,
        onSecondaryContainer = Light.onSecondaryContainer,
        tertiary = Light.tertiaryButton,
        onTertiary = Light.textTertiaryButton,
        background = Light.backgroundStart,
        onBackground = Light.textBodyOnBackground,
        surface = Light.surface,
        onSurface = Light.textBodyOnBackground,
        surfaceVariant = Light.surfaceVariant,
        onSurfaceVariant = Light.textCaption,
        outline = Light.outline,
        outlineVariant = Light.outlineVariant,
        error = Light.dangerous,
        onError = Light.onDangerous,
    )

internal val MidnightColorPalette =
    DarkColorPalette.copy(
        primary = Color(0xFF9B87F5),
        onPrimary = Color(0xFF0B0816),
        primaryContainer = Color(0xFF221C34),
        onPrimaryContainer = Color(0xFFEAE4FF),
        secondary = Color(0xFF242C3A),
        onSecondary = Color(0xFFC5CBD9),
        secondaryContainer = Color(0xFF2C3545),
        onSecondaryContainer = Color(0xFFD7DCE8),
        tertiary = Color(0xFF1E2838),
        onTertiary = Color(0xFFDDE2EE),
        background = Color(0xFF05060B),
        onBackground = Color(0xFFC5CBD9),
        surface = Color(0xFF101827),
        onSurface = Color(0xFFC5CBD9),
        surfaceVariant = Color(0xFF172032),
        onSurfaceVariant = Color(0xFF939AAF),
        outline = Color(0xFF303848),
        outlineVariant = Color(0xFF252C38),
    )

internal val DarkExtendedColorPalette =
    ExtendedColors(
        surfaceEnd = Dark.backgroundEnd,
        onBackgroundHeader = Dark.textHeaderOnBackground,
        tertiary = Dark.tertiaryButton,
        onTertiary = Dark.textTertiaryButton,
        callout = Dark.callout,
        onCallout = Dark.onCallout,
        progressStart = Dark.progressStart,
        progressEnd = Dark.progressEnd,
        progressBackground = Dark.progressBackground,
        chipIndex = Dark.textChipIndex,
        overlay = Dark.overlay,
        highlight = Dark.highlight,
        addressHighlightBorder = Dark.addressHighlightBorder,
        addressHighlightUnified = Dark.addressHighlightUnified,
        addressHighlightConfidential = Dark.addressHighlightConfidential,
        addressHighlightPublic = Dark.addressHighlightPublic,
        dangerous = Dark.dangerous,
        onDangerous = Dark.onDangerous,
        reference = Dark.reference,
        divider = Dark.divider,
        navigationIcon = Dark.navigationIcon,
        navigationContainer = Dark.navigationContainer,
        selectedPageIndicator = Dark.selectedPageIndicator,
        secondaryTitleText = Dark.secondaryTitleText,
    )

internal val MidnightExtendedColorPalette =
    DarkExtendedColorPalette.copy(
        surfaceEnd = Color(0xFF090E17),
        reference = Color(0xFFB8A8FF),
        selectedPageIndicator = Color(0xFF9B87F5),
        progressStart = Color(0xFF9B87F5),
        progressEnd = Color(0xFF6B55C4),
        navigationContainer = Color(0xFF05060B),
        divider = Color(0xFF303848),
        addressHighlightBorder = Color(0xFF3D4659),
        addressHighlightUnified = Color(0xFFB8A8FF),
        addressHighlightConfidential = Color(0xFF939AAF),
        addressHighlightPublic = Color(0xFF9B87F5),
    )

internal val LightExtendedColorPalette =
    ExtendedColors(
        surfaceEnd = Light.backgroundEnd,
        onBackgroundHeader = Light.textHeaderOnBackground,
        tertiary = Light.tertiaryButton,
        onTertiary = Light.textTertiaryButton,
        callout = Light.callout,
        onCallout = Light.onCallout,
        progressStart = Light.progressStart,
        progressEnd = Light.progressEnd,
        progressBackground = Light.progressBackground,
        chipIndex = Light.textChipIndex,
        overlay = Light.overlay,
        highlight = Light.highlight,
        addressHighlightBorder = Light.addressHighlightBorder,
        addressHighlightUnified = Light.addressHighlightUnified,
        addressHighlightConfidential = Light.addressHighlightConfidential,
        addressHighlightPublic = Light.addressHighlightPublic,
        dangerous = Light.dangerous,
        onDangerous = Light.onDangerous,
        reference = Light.reference,
        divider = Light.divider,
        navigationIcon = Light.navigationIcon,
        navigationContainer = Light.navigationContainer,
        selectedPageIndicator = Light.selectedPageIndicator,
        secondaryTitleText = Light.secondaryTitleText,
    )

@Suppress("CompositionLocalAllowlist")
internal val LocalExtendedColors =
    staticCompositionLocalOf {
        ExtendedColors(
            surfaceEnd = Color.Unspecified,
            onBackgroundHeader = Color.Unspecified,
            tertiary = Color.Unspecified,
            onTertiary = Color.Unspecified,
            callout = Color.Unspecified,
            onCallout = Color.Unspecified,
            progressStart = Color.Unspecified,
            progressEnd = Color.Unspecified,
            progressBackground = Color.Unspecified,
            chipIndex = Color.Unspecified,
            overlay = Color.Unspecified,
            highlight = Color.Unspecified,
            addressHighlightBorder = Color.Unspecified,
            addressHighlightUnified = Color.Unspecified,
            addressHighlightConfidential = Color.Unspecified,
            addressHighlightPublic = Color.Unspecified,
            dangerous = Color.Unspecified,
            onDangerous = Color.Unspecified,
            reference = Color.Unspecified,
            divider = Color.Unspecified,
            navigationIcon = Color.Unspecified,
            navigationContainer = Color.Unspecified,
            selectedPageIndicator = Color.Unspecified,
            secondaryTitleText = Color.Unspecified,
        )
    }
