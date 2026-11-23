package ir.jibik.app

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Bundled, offline Persian typeface. License: licenses/Vazirmatn-OFL.txt
 * Apply through MaterialTheme so dialogs, text fields and every finance screen
 * inherit one consistent family without fragile per-screen overrides.
 */
internal val JibikFontFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
    Font(R.font.vazirmatn_extrabold, FontWeight.ExtraBold),
)

private val defaultTypography = Typography()

internal val JibikTypography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(fontFamily = JibikFontFamily),
    displayMedium = defaultTypography.displayMedium.copy(fontFamily = JibikFontFamily),
    displaySmall = defaultTypography.displaySmall.copy(fontFamily = JibikFontFamily),
    headlineLarge = defaultTypography.headlineLarge.copy(fontFamily = JibikFontFamily),
    headlineMedium = defaultTypography.headlineMedium.copy(fontFamily = JibikFontFamily),
    headlineSmall = defaultTypography.headlineSmall.copy(fontFamily = JibikFontFamily),
    titleLarge = defaultTypography.titleLarge.copy(fontFamily = JibikFontFamily),
    titleMedium = defaultTypography.titleMedium.copy(fontFamily = JibikFontFamily),
    titleSmall = defaultTypography.titleSmall.copy(fontFamily = JibikFontFamily),
    bodyLarge = defaultTypography.bodyLarge.copy(fontFamily = JibikFontFamily),
    bodyMedium = defaultTypography.bodyMedium.copy(fontFamily = JibikFontFamily),
    bodySmall = defaultTypography.bodySmall.copy(fontFamily = JibikFontFamily),
    labelLarge = defaultTypography.labelLarge.copy(fontFamily = JibikFontFamily),
    labelMedium = defaultTypography.labelMedium.copy(fontFamily = JibikFontFamily),
    labelSmall = defaultTypography.labelSmall.copy(fontFamily = JibikFontFamily),
)
