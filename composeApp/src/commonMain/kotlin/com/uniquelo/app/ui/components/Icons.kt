package com.uniquelo.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.uniquelo.app.ui.theme.Palette

enum class UiIcon {
    Discover,
    Bag,
    Person,
    Search,
    ArrowLeft,
    ChevronRight,
    Check,
    Plus,
    Minus,
    Close,
    Heart,
    HeartFilled,
}

/**
 * Small hand-drawn icon set so the project has no icon dependency and the
 * shapes stay consistent with the editorial art direction.
 */
@Composable
fun UiIcon(
    icon: UiIcon,
    modifier: Modifier = Modifier,
    tint: Color = Palette.Ink,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(
            width = w * 0.075f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )

        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(tint, Offset(w * x1, h * y1), Offset(w * x2, h * y2), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }

        when (icon) {
            UiIcon.Discover -> {
                val path = Path().apply {
                    moveTo(w * 0.5f, h * 0.08f)
                    lineTo(w * 0.59f, h * 0.41f)
                    lineTo(w * 0.92f, h * 0.5f)
                    lineTo(w * 0.59f, h * 0.59f)
                    lineTo(w * 0.5f, h * 0.92f)
                    lineTo(w * 0.41f, h * 0.59f)
                    lineTo(w * 0.08f, h * 0.5f)
                    lineTo(w * 0.41f, h * 0.41f)
                    close()
                }
                drawPath(path, tint, style = stroke)
            }

            UiIcon.Bag -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.19f, h * 0.34f),
                    size = Size(w * 0.62f, h * 0.54f),
                    cornerRadius = CornerRadius(w * 0.09f),
                    style = stroke,
                )
                drawArc(
                    color = tint,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * 0.33f, h * 0.14f),
                    size = Size(w * 0.34f, h * 0.38f),
                    style = stroke,
                )
            }

            UiIcon.Person -> {
                drawCircle(
                    color = tint,
                    radius = w * 0.16f,
                    center = Offset(w * 0.5f, h * 0.31f),
                    style = stroke,
                )
                drawArc(
                    color = tint,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * 0.18f, h * 0.56f),
                    size = Size(w * 0.64f, h * 0.5f),
                    style = stroke,
                )
            }

            UiIcon.Search -> {
                drawCircle(
                    color = tint,
                    radius = w * 0.26f,
                    center = Offset(w * 0.43f, h * 0.43f),
                    style = stroke,
                )
                line(0.63f, 0.63f, 0.86f, 0.86f)
            }

            UiIcon.ArrowLeft -> {
                line(0.82f, 0.5f, 0.2f, 0.5f)
                val path = Path().apply {
                    moveTo(w * 0.44f, h * 0.24f)
                    lineTo(w * 0.18f, h * 0.5f)
                    lineTo(w * 0.44f, h * 0.76f)
                }
                drawPath(path, tint, style = stroke)
            }

            UiIcon.ChevronRight -> {
                val path = Path().apply {
                    moveTo(w * 0.4f, h * 0.24f)
                    lineTo(w * 0.64f, h * 0.5f)
                    lineTo(w * 0.4f, h * 0.76f)
                }
                drawPath(path, tint, style = stroke)
            }

            UiIcon.Check -> {
                val path = Path().apply {
                    moveTo(w * 0.2f, h * 0.54f)
                    lineTo(w * 0.42f, h * 0.75f)
                    lineTo(w * 0.8f, h * 0.27f)
                }
                drawPath(path, tint, style = stroke)
            }

            UiIcon.Plus -> {
                line(0.5f, 0.2f, 0.5f, 0.8f)
                line(0.2f, 0.5f, 0.8f, 0.5f)
            }

            UiIcon.Minus -> line(0.2f, 0.5f, 0.8f, 0.5f)

            UiIcon.Close -> {
                line(0.24f, 0.24f, 0.76f, 0.76f)
                line(0.76f, 0.24f, 0.24f, 0.76f)
            }

            UiIcon.Heart -> {
                val path = Path().apply {
                    moveTo(w * 0.5f, h * 0.32f)
                    cubicTo(w * 0.42f, h * 0.16f, w * 0.18f, h * 0.16f, w * 0.18f, h * 0.38f)
                    cubicTo(w * 0.18f, h * 0.56f, w * 0.35f, h * 0.70f, w * 0.5f, h * 0.82f)
                    cubicTo(w * 0.65f, h * 0.70f, w * 0.82f, h * 0.56f, w * 0.82f, h * 0.38f)
                    cubicTo(w * 0.82f, h * 0.16f, w * 0.58f, h * 0.16f, w * 0.5f, h * 0.32f)
                    close()
                }
                drawPath(path, tint, style = stroke)
            }

            UiIcon.HeartFilled -> {
                val path = Path().apply {
                    moveTo(w * 0.5f, h * 0.32f)
                    cubicTo(w * 0.42f, h * 0.16f, w * 0.18f, h * 0.16f, w * 0.18f, h * 0.38f)
                    cubicTo(w * 0.18f, h * 0.56f, w * 0.35f, h * 0.70f, w * 0.5f, h * 0.82f)
                    cubicTo(w * 0.65f, h * 0.70f, w * 0.82f, h * 0.56f, w * 0.82f, h * 0.38f)
                    cubicTo(w * 0.82f, h * 0.16f, w * 0.58f, h * 0.16f, w * 0.5f, h * 0.32f)
                    close()
                }
                drawPath(path, tint)
            }
        }
    }
}

val DefaultIconSize = 22.dp
