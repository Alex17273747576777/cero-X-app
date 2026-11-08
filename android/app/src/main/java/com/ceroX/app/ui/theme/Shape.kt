package com.ceroX.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// `Shapes(small = 4.dp)` no compila: Shapes espera formas, no densidades.
// Hay que envolver cada valor en RoundedCornerShape.
val CeroXShapes = Shapes(
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
)
