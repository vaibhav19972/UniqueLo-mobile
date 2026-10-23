package com.uniquelo.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.uniquelo.app.model.Product
import com.uniquelo.app.ui.theme.Palette

/**
 * Deterministic, asset-free fallback artwork. It uses the universe theme from
 * Supabase, so a piece still feels like it belongs to its world while its real
 * photography loads (or when a product has no usable image).
 */
@Composable
fun ProductArtwork(
    product: Product,
    modifier: Modifier = Modifier,
    corner: Dp = 0.dp,
) {
    val theme = product.universe?.theme
    val base = parseHexColor(theme?.bg, Palette.Ink)
    val mid = parseHexColor(theme?.surface ?: theme?.accent2, Palette.Olive)
    val accent = parseHexColor(theme?.accent, Palette.Lime)
    val accent2 = parseHexColor(theme?.accent2, Palette.Rust)
    val seed = (product.slug.hashCode() and 0x7fffffff) % 4

    Canvas(modifier.clip(RoundedCornerShape(corner))) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(base, mid),
                start = Offset.Zero,
                end = Offset(size.width, size.height),
            ),
            topLeft = Offset.Zero,
            size = size,
        )

        when (seed) {
            0 -> {
                drawCircle(
                    color = accent,
                    radius = size.minDimension * 0.34f,
                    center = Offset(size.width * 0.68f, size.height * 0.32f),
                )
                drawCircle(
                    color = accent.copy(alpha = 0.35f),
                    radius = size.minDimension * 0.48f,
                    center = Offset(size.width * 0.68f, size.height * 0.32f),
                    style = Stroke(width = size.minDimension * 0.012f),
                )
            }

            1 -> {
                val center = Offset(size.width * 0.32f, size.height * 0.66f)
                for (i in 1..5) {
                    drawCircle(
                        color = accent.copy(alpha = 0.14f + i * 0.09f),
                        radius = size.minDimension * (0.08f + i * 0.09f),
                        center = center,
                        style = Stroke(width = size.minDimension * 0.014f),
                    )
                }
            }

            2 -> {
                val step = size.width / 5f
                for (i in -2..6) {
                    drawLine(
                        color = accent.copy(alpha = 0.22f),
                        start = Offset(i * step, size.height),
                        end = Offset(i * step + size.height * 0.65f, 0f),
                        strokeWidth = size.minDimension * 0.05f,
                    )
                }
                drawCircle(
                    color = accent,
                    radius = size.minDimension * 0.11f,
                    center = Offset(size.width * 0.78f, size.height * 0.24f),
                )
            }

            else -> {
                val block = size.minDimension * 0.42f
                drawRoundRect(
                    color = accent2.copy(alpha = 0.92f),
                    topLeft = Offset(size.width * 0.16f, size.height * 0.44f),
                    size = Size(block, block),
                    cornerRadius = CornerRadius(block * 0.12f),
                )
                drawRoundRect(
                    color = base.copy(alpha = 0.55f),
                    topLeft = Offset(size.width * 0.46f, size.height * 0.2f),
                    size = Size(block, block),
                    cornerRadius = CornerRadius(block * 0.12f),
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.18f),
                    topLeft = Offset(size.width * 0.34f, size.height * 0.32f),
                    size = Size(block, block),
                    cornerRadius = CornerRadius(block * 0.12f),
                    style = Stroke(width = size.minDimension * 0.008f),
                )
            }
        }

        for (i in 1..5) {
            drawLine(
                color = Color.White.copy(alpha = 0.055f),
                start = Offset(0f, size.height * i / 6f),
                end = Offset(size.width, size.height * i / 6f),
                strokeWidth = 1f,
            )
        }
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.24f)),
                startY = size.height * 0.58f,
                endY = size.height,
            ),
            topLeft = Offset(0f, size.height * 0.58f),
            size = Size(size.width, size.height * 0.42f),
        )
    }
}

fun parseHexColor(value: String?, fallback: Color): Color {
    if (value.isNullOrBlank()) return fallback
    val clean = value.removePrefix("#")
    val parsed = clean.toLongOrNull(16) ?: return fallback
    return when (clean.length) {
        6 -> Color((0xFF000000L or parsed).toInt())
        8 -> Color(parsed.toInt())
        else -> fallback
    }
}
