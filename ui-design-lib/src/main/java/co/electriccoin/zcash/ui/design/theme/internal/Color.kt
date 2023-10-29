@file:Suppress("MagicNumber")

package co.electriccoin.zcash.ui.design.theme.internal

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import co.electriccoin.zcash.ui.design.theme.ExtendedColors

internal object Dark {
    val backgroundStart = Color(0xFF000000)
    val backgroundEnd = Color(0xFF000000)

    val textHeaderOnBackground = Color(0xffCBDCF2)
    val textBodyOnBackground = Color(0xFF93A4BE)
    val textPrimaryButton = Color(0xFF0F2341)
    val textSecondaryButton = Color(0xFF0F2341)
    val textTertiaryButton = Color.White
    val textNavigationButton = Color.Black
    val textCaption = Color(0xFF68728B)
    val textChipIndex = Color(0xFF93A4BE)

    val primaryButton = Color(0xffCBDCF2)
    val primaryButtonPressed = Color(0xFF93A4BE)
    val primaryButtonDisabled = Color(0xFF68728B)

    val secondaryButton = Color(0xFFA7C0D9)
    val secondaryButtonPressed = Color(0xFFC8DCEF)
    val secondaryButtonDisabled = Color(0x33C8DCEF)

    val tertiaryButton = Color(0xFF131212)
    val tertiaryButtonPressed = Color(0xB0C3D2BA)

    val navigationButton = Color(0xFFA7C0D9)
    val navigationButtonPressed = Color(0xFFC8DCEF)
    val navigationIcon = Color(0xFFFE7757)

    val progressStart = Color(0xFFF364CE)
    val progressEnd = Color(0xFFE91E63)
    val progressBackground = Color(0xFF929bb3)

    val callout = Color(0xFFa7bed8)
    val onCallout = Color(0xFF3d698f)

    val overlay = Color(0x22000000)
    val highlight = Color(0xFFFFFFFF)

    val addressHighlightBorder = Color(0xFF525252)
    val addressHighlightUnified = Color(0xFFFFFFFF)
    val addressHighlightSapling = Color(0xFF97999A)
    val addressHighlightTransparent = Color(0xFF1BBFF6)

    val dangerous = Color(0xFFEC0008)
    val onDangerous = Color(0xFFFFFFFF)

    val reference = Color(0xFF9AD9FF)
    val divider = Color(0xFF2B2551)
    val navigationContainer = Color(0xFF131212)
    val selectedPageIndicator = Color(0xFFFE7757)
    val secondaryTitleText = Color(0xFF93A4BE)
}

internal object Light {
    val backgroundStart = Color(0xFF110E2B)
    val backgroundEnd = Color(0xFFD2E4F3)
    val primaryPeach = Color(0xFFFE7757)

    val textHeaderOnBackground = primaryPeach
    val textBodyOnBackground = Color.White
    val textNavigationButton = Color(0xFF7B8897)
    val textPrimaryButton = Color(0xFF110E2B)
    val textSecondaryButton = Color(0xFF2E476E)
    val textTertiaryButton = primaryPeach
    val textCaption = Color(0xFF2D3747)
    val textChipIndex = Color(0xFFEE8592)

    // TODO The button colors are wrong for light
    val primaryButton = Color(0xFFFE7757)
    val primaryButtonPressed = Color(0xFFFEAD9A)
    val primaryButtonDisabled = Color(0xFFCE624E)

    val secondaryButton = Color(0xFFE8F3FA)
    val secondaryButtonPressed = Color(0xFFFAFBFD)
    val secondaryButtonDisabled = Color(0xFFE6EFF8)

    val tertiaryButton = Color(0xFF2B2551)
    val tertiaryButtonPressed = Color(0xFFFFFFFF)

    val navigationButton = Color(0xFFE3EDF7)
    val navigationButtonPressed = Color(0xFFE3EDF7)
    val navigationIcon = Color(0xFF110E2B)

    val progressStart = Color(0xFFF364CE)
    val progressEnd = Color(0xFFE91E63)
    val progressBackground = Color(0xFFbeccdf)

    val callout = Color(0xFFe6f0f9)
    val onCallout = Color(0xFFa1b8d0)

    val overlay = Color(0x22000000)
    val highlight = Color(0xFFFEAD9A)

    // TODO [#159]: The colors are wrong for light theme
    // TODO [#159]: https://github.com/zcash/secant-android-wallet/issues/159
    val addressHighlightBorder = Color(0xFF525252)
    val addressHighlightUnified = Color(0xFFFEAD9A)
    val addressHighlightSapling = Color(0xFF1BBFF6)
    val addressHighlightTransparent = Color(0xFF97999A)

    val dangerous = Color(0xFFEC0008)
    val onDangerous = Color(0xFFFFFFFF)

    val reference = primaryPeach
    val divider = Color(0xFF2B2551)
    val navigationContainer = Color(0xFF2B2551)
    val selectedPageIndicator = Color(0xFFD2E4F3)
    val secondaryTitleText = Color(0xFFD2E4F3)
}

internal val DarkColorPalette = darkColorScheme(
    primary = Dark.primaryButton,
    secondary = Dark.secondaryButton,
    onPrimary = Dark.textPrimaryButton,
    onSecondary = Dark.textSecondaryButton,
    surface = Dark.backgroundStart,
    onSurface = Dark.textBodyOnBackground,
    background = Dark.backgroundStart,
    onBackground = Dark.textBodyOnBackground
)

internal val LightColorPalette = lightColorScheme(
    primary = Light.primaryButton,
    secondary = Light.secondaryButton,
    onPrimary = Light.textPrimaryButton,
    onSecondary = Light.textSecondaryButton,
    surface = Light.backgroundStart,
    onSurface = Light.textBodyOnBackground,
    background = Light.backgroundStart,
    onBackground = Light.textBodyOnBackground
)

internal val DarkExtendedColorPalette = ExtendedColors(
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
    addressHighlightSapling = Dark.addressHighlightSapling,
    addressHighlightTransparent = Dark.addressHighlightTransparent,
    dangerous = Dark.dangerous,
    onDangerous = Dark.onDangerous,
    reference = Dark.reference,
    divider = Dark.divider,
    navigationIcon = Dark.navigationIcon,
    navigationContainer = Dark.navigationContainer,
    selectedPageIndicator = Dark.selectedPageIndicator,
    secondaryTitleText = Dark.secondaryTitleText
)

internal val LightExtendedColorPalette = ExtendedColors(
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
    addressHighlightSapling = Light.addressHighlightSapling,
    addressHighlightTransparent = Light.addressHighlightTransparent,
    dangerous = Light.dangerous,
    onDangerous = Light.onDangerous,
    reference = Light.reference,
    divider = Light.divider,
    navigationIcon = Light.navigationIcon,
    navigationContainer = Light.navigationContainer,
    selectedPageIndicator = Light.selectedPageIndicator,
    secondaryTitleText = Light.secondaryTitleText
)

@Suppress("CompositionLocalAllowlist")
internal val LocalExtendedColors = staticCompositionLocalOf {
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
        addressHighlightSapling = Color.Unspecified,
        addressHighlightTransparent = Color.Unspecified,
        dangerous = Color.Unspecified,
        onDangerous = Color.Unspecified,
        reference = Color.Unspecified,
        divider = Color.Unspecified,
        navigationIcon = Color.Unspecified,
        navigationContainer = Color.Unspecified,
        selectedPageIndicator = Color.Unspecified,
        secondaryTitleText = Color.Unspecified
    )
}
