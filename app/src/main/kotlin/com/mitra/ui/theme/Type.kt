package com.mitra.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 7-step type scale from docs/design/tokens.md mapped onto Material 3 slots. Inter is the
 * locked primary face (bundle as res/font/inter_*.ttf when ready); for now we ride
 * FontFamily.SansSerif so the scale lands without the font asset. The visual proportions —
 * size, weight, line height, tracking — are the brand, not the family.
 *
 * Body is the workhorse: ~80% of text in the app sits at bodyLarge. Reach for a heading
 * only when you genuinely need one.
 */
private val FontFace = FontFamily.SansSerif // swap to Inter family once res/font/ is populated

private fun face(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0): TextStyle =
    TextStyle(
        fontFamily = FontFace,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.sp,
    )

val MitraTypography: Typography =
    Typography(
        // Display — onboarding hero, big numbers in audit summary
        displayLarge = face(32, 40, FontWeight.SemiBold, -0.5),
        displayMedium = face(32, 40, FontWeight.SemiBold, -0.5),
        displaySmall = face(32, 40, FontWeight.SemiBold, -0.5),
        // HeadlineL — screen titles ("Settings", "Audit history")
        headlineLarge = face(24, 32, FontWeight.SemiBold, -0.25),
        // HeadlineM — section headers within screens
        headlineMedium = face(20, 28, FontWeight.SemiBold),
        headlineSmall = face(20, 28, FontWeight.SemiBold),
        // TitleL — action-card titles, list-item primary text
        titleLarge = face(18, 24, FontWeight.Medium),
        titleMedium = face(18, 24, FontWeight.Medium),
        titleSmall = face(16, 24, FontWeight.Medium),
        // Body — chat messages, paragraph text, primary readable copy
        bodyLarge = face(16, 24, FontWeight.Normal),
        bodyMedium = face(14, 20, FontWeight.Normal),
        bodySmall = face(13, 18, FontWeight.Normal),
        // Label — button labels, tab labels, card metadata
        labelLarge = face(14, 20, FontWeight.Medium, 0.1),
        labelMedium = face(13, 16, FontWeight.Medium, 0.1),
        // Caption — timestamps, footnotes, audit row secondary text
        labelSmall = face(12, 16, FontWeight.Normal, 0.2),
    )
