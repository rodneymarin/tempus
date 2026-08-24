package com.rodneymarin.tempus.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 shape scale with Google-like rounded corners.
 * Small/medium used by buttons, chips and fields; large/extra-large by
 * bottom sheets, dialogs and cards for a soft, modern look.
 */
val TempusShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)