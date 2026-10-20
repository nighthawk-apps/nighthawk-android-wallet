@file:Suppress("UnusedPrivateProperty")

package com.nighthawkapps.lib.android.ui.design.theme.internal

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.sp
import com.nighthawkapps.lib.android.ui.design.R

@OptIn(ExperimentalTextApi::class)
private val provider =
    GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs
    )

// We use bestEffort here to be able to get the closest font weight, if accidentally use
// an unspecified font weight and not the default one.
@OptIn(ExperimentalTextApi::class)
private val RubikFont = GoogleFont(name = "Rubik", bestEffort = true)

@OptIn(ExperimentalTextApi::class)
private val RubikFontFamily =
    FontFamily(
        Font(googleFont = RubikFont, fontProvider = provider, weight = FontWeight.Normal), // W400
        Font(googleFont = RubikFont, fontProvider = provider, weight = FontWeight.Medium), // W500
        Font(googleFont = RubikFont, fontProvider = provider, weight = FontWeight.SemiBold), // W600
        Font(googleFont = RubikFont, fontProvider = provider, weight = FontWeight.Bold) // W700
    )

private val Zboto =
    FontFamily(
        Font(R.font.zboto, FontWeight.Normal)
    )

@OptIn(ExperimentalTextApi::class)
private val OrbitronFont = GoogleFont(name = "Orbitron", bestEffort = true)

@OptIn(ExperimentalTextApi::class)
private val PulpDisplay =
    FontFamily(
        Font(googleFont = OrbitronFont, fontProvider = provider, weight = FontWeight.Normal), // W400
        Font(googleFont = OrbitronFont, fontProvider = provider, weight = FontWeight.Medium), // W500
        Font(googleFont = OrbitronFont, fontProvider = provider, weight = FontWeight.SemiBold), // W600
        Font(googleFont = OrbitronFont, fontProvider = provider, weight = FontWeight.Bold) // W700
    )

@OptIn(ExperimentalTextApi::class)
private val ShareTechMonoFont = GoogleFont(name = "Share Tech Mono", bestEffort = true)

@OptIn(ExperimentalTextApi::class)
private val ShareTechMonoFontFamily =
    FontFamily(
        Font(googleFont = ShareTechMonoFont, fontProvider = provider, weight = FontWeight.Normal), // W400
        Font(googleFont = ShareTechMonoFont, fontProvider = provider, weight = FontWeight.Medium), // W500
        Font(googleFont = ShareTechMonoFont, fontProvider = provider, weight = FontWeight.SemiBold), // W600
        Font(googleFont = ShareTechMonoFont, fontProvider = provider, weight = FontWeight.Bold) // W700
    )

private fun variableFamily(resId: Int): FontFamily {
    fun face(weight: FontWeight, axis: Int) =
        Font(
            resId = resId,
            weight = weight,
            variationSettings = FontVariation.Settings(FontVariation.weight(axis)),
        )
    return FontFamily(
        face(FontWeight.Normal, 400),
        face(FontWeight.Medium, 500),
        face(FontWeight.SemiBold, 600),
        face(FontWeight.Bold, 700),
    )
}

private fun serifFamily(resId: Int): FontFamily {
    fun face(weight: FontWeight, axis: Int) =
        Font(
            resId = resId,
            weight = weight,
            variationSettings =
                FontVariation.Settings(
                    FontVariation.weight(axis),
                    FontVariation.Setting("opsz", 18f),
                ),
        )
    return FontFamily(
        face(FontWeight.Normal, 400),
        face(FontWeight.Medium, 500),
        face(FontWeight.SemiBold, 600),
        face(FontWeight.Bold, 700),
    )
}

/** Stealth and Midnight headings. Outfit is geometric but readable at title sizes. */
private val OutfitFamily = variableFamily(R.font.outfit)

/** Body for every theme. Source Sans 3 is a text face; Share Tech Mono stays for raw keys. */
private val SourceSansFamily = variableFamily(R.font.source_sans_3)

/** Paper headings. Source Serif 4 is a text serif, not a display face. */
private val SourceSerifFamily = serifFamily(R.font.source_serif_4)

private val IbmPlexMonoFamily =
    FontFamily(Font(R.font.ibm_plex_mono, FontWeight.Normal))

// If you change this definition of our Typography, don't forget to check if you use only
// the defined font weights above, otherwise the closest one will be used.
internal val Typography =
    Typography(
        headlineLarge =
            TextStyle(
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp,
                letterSpacing = TextUnit(value = 0.3f, TextUnitType.Sp),
                lineHeight = TextUnit(value = 38f, TextUnitType.Sp)
            ),
        headlineMedium =
            TextStyle(
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 28.sp,
                letterSpacing = TextUnit(value = 0.3f, TextUnitType.Sp),
                lineHeight = TextUnit(value = 34f, TextUnitType.Sp)
            ),
        bodyLarge =
            TextStyle(
                fontFamily = SourceSansFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                letterSpacing = TextUnit(value = 0.3f, TextUnitType.Sp),
                lineHeight = TextUnit(value = 22f, TextUnitType.Sp)
            ),
        bodyMedium =
            TextStyle(
                fontFamily = SourceSansFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                letterSpacing = TextUnit(value = 0.3f, TextUnitType.Sp),
                lineHeight = TextUnit(value = 20f, TextUnitType.Sp)
            ),
        bodySmall =
            TextStyle(
                fontFamily = SourceSansFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                letterSpacing = TextUnit(value = 0.3f, TextUnitType.Sp),
                lineHeight = TextUnit(value = 18f, TextUnitType.Sp)
            ),
        labelLarge =
            TextStyle(
                fontFamily = SourceSansFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp
            ),
        titleLarge =
            TextStyle(
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 21.sp
            ),
        titleMedium =
            TextStyle(
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp
            ),
        titleSmall =
            TextStyle(
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp
            )
    )

internal fun paperTypography(): Typography {
    fun TextStyle.asSerif() = copy(fontFamily = SourceSerifFamily, letterSpacing = 0.sp)
    fun TextStyle.asText() = copy(fontFamily = SourceSansFamily, letterSpacing = 0.sp)
    return Typography.copy(
        headlineLarge = Typography.headlineLarge.asSerif(),
        headlineMedium = Typography.headlineMedium.asSerif(),
        titleLarge = Typography.titleLarge.asSerif(),
        titleMedium = Typography.titleMedium.asSerif(),
        titleSmall = Typography.titleSmall.asSerif(),
        bodyLarge = Typography.bodyLarge.asText(),
        bodyMedium = Typography.bodyMedium.asText(),
        bodySmall = Typography.bodySmall.asText(),
        labelLarge = Typography.labelLarge.asText(),
    )
}

internal fun extendedTypography(paper: Boolean): ExtendedTypography {
    val body = if (paper) paperTypography().bodyLarge else Typography.bodyLarge
    return ExtendedTypography(
        chipIndex =
            body.copy(
                fontSize = 10.sp,
                baselineShift = BaselineShift.Superscript,
                fontWeight = FontWeight.Bold,
            ),
        listItem = body.copy(fontSize = 24.sp),
        drkBalance =
            TextStyle(
                fontFamily = if (paper) SourceSerifFamily else OutfitFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 30.sp,
            ),
    )
}

@Immutable
data class ExtendedTypography(
    val chipIndex: TextStyle,
    val listItem: TextStyle,
    val drkBalance: TextStyle
)

@Suppress("CompositionLocalAllowlist")
val LocalExtendedTypography =
    compositionLocalOf {
        ExtendedTypography(
            chipIndex =
                Typography.bodyLarge.copy(
                    fontSize = 10.sp,
                    baselineShift = BaselineShift.Superscript,
                    fontWeight = FontWeight.Bold
                ),
            listItem =
                Typography.bodyLarge.copy(
                    fontSize = 24.sp
                ),
            drkBalance =
                TextStyle(
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.Normal,
                    fontSize = 30.sp
                )
        )
    }
