package com.ceroX.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// La escala de Material 3 no tiene h1/h2/body1/body2: esos nombres son de M2.
// Los equivalentes son displayLarge/headlineLarge/bodyLarge/bodyMedium.
val CeroXTypography = Typography(
    displayLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
)
