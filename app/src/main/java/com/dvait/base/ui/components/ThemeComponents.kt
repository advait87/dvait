package com.dvait.base.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dvait.base.ui.theme.AccentPalette

@Composable
fun AccentSelectorCircle(
    palette: AccentPalette,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = CircleShape
            )
            .clickable { onClick() }
            .padding(if (isSelected) 6.dp else 4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasSize = size

            // Top-Left: White
            drawArc(
                color = Color.White,
                startAngle = 180f,
                sweepAngle = 90f,
                useCenter = true,
                size = canvasSize,
                topLeft = Offset.Zero
            )
            // Top-Right: Black
            drawArc(
                color = Color.Black,
                startAngle = 270f,
                sweepAngle = 90f,
                useCenter = true,
                size = canvasSize,
                topLeft = Offset.Zero
            )
            // Bottom-Left: Subtle/Light Accent
            drawArc(
                color = palette.primarySubtle,
                startAngle = 90f,
                sweepAngle = 90f,
                useCenter = true,
                size = canvasSize,
                topLeft = Offset.Zero
            )
            // Bottom-Right: Main/Dark Accent
            drawArc(
                color = palette.primary,
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = true,
                size = canvasSize,
                topLeft = Offset.Zero
            )
        }
    }
}
