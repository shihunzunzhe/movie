package com.earthvideo.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val display = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.em)
private val headlineLg = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.em)
private val headlineMd = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.em)
private val titleLg = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.em)
private val titleMd = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.em)
private val body = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.em)
private val bodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.em)
private val caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.em)
private val micro = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.em)

val AppTypography = Typography(
    displayLarge = display,
    displayMedium = display.copy(fontSize = 20.sp),
    displaySmall = display.copy(fontSize = 18.sp),
    headlineLarge = headlineLg,
    headlineMedium = headlineMd,
    headlineSmall = headlineMd.copy(fontSize = 15.sp),
    titleLarge = titleLg,
    titleMedium = titleMd,
    titleSmall = titleMd.copy(fontSize = 13.sp),
    bodyLarge = body.copy(fontWeight = FontWeight.Medium),
    bodyMedium = body,
    bodySmall = bodySmall,
    labelLarge = caption.copy(fontWeight = FontWeight.Medium),
    labelMedium = caption,
    labelSmall = micro,
)
