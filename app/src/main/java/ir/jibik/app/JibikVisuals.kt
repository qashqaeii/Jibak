package ir.jibik.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Small vector mascot used while the approved, optimised WebP expressions are prepared.
 * Drawn at runtime so no platform-dependent emoji appears in the financial UI.
 */
@Composable
internal fun JiboMascot(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val unit = size.minDimension / 100f
        val origin = Offset((size.width - unit * 100f) / 2f, (size.height - unit * 100f) / 2f)
        fun p(x: Float, y: Float) = Offset(origin.x + x * unit, origin.y + y * unit)
        fun box(w: Float, h: Float) =
            Size(w * unit, h * unit)
        fun triangle(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Path =
            Path().apply {
                moveTo(p(ax, ay).x, p(ax, ay).y)
                lineTo(p(bx, by).x, p(bx, by).y)
                lineTo(p(cx, cy).x, p(cx, cy).y)
                close()
            }

        val forest = Color(0xFF07574C)
        val orange = Color(0xFFFF861C)
        val darkOrange = Color(0xFFE96D12)
        val cream = Color(0xFFFFFAF5)
        val ink = Color(0xFF283034)

        // Distinctive little green hoodie, visible even at small icon sizes.
        drawOval(forest, topLeft = p(13f, 69f), size = box(74f, 31f))
        drawOval(Color(0xFF12806C), topLeft = p(38f, 68f), size = box(24f, 18f))
        drawLine(cream, p(44f, 81f), p(43f, 94f), unit * 1.5f, cap = StrokeCap.Round)
        drawLine(cream, p(56f, 81f), p(57f, 94f), unit * 1.5f, cap = StrokeCap.Round)

        // Ears sit behind the rounded fox face.
        drawPath(triangle(20f, 37f, 25f, 5f, 45f, 25f), orange)
        drawPath(triangle(80f, 37f, 75f, 5f, 55f, 25f), orange)
        drawPath(triangle(26f, 27f, 28f, 13f, 38f, 25f), Color(0xFF9B483D))
        drawPath(triangle(74f, 27f, 72f, 13f, 62f, 25f), Color(0xFF9B483D))
        drawOval(darkOrange, topLeft = p(18f, 22f), size = box(64f, 64f))
        drawOval(orange, topLeft = p(19f, 22f), size = box(62f, 61f))
        // Soft white cheeks and muzzle.
        drawOval(cream, topLeft = p(18f, 56f), size = box(34f, 26f))
        drawOval(cream, topLeft = p(48f, 56f), size = box(34f, 26f))
        drawOval(cream, topLeft = p(40f, 63f), size = box(20f, 18f))
        drawCircle(Color(0xFFFFB36A), unit * 6f, p(28f, 58f))
        drawCircle(Color(0xFFFFB36A), unit * 6f, p(72f, 58f))

        // Expression: gentle, symmetric and legible at small sizes.
        drawCircle(ink, unit * 3f, p(38f, 49f))
        drawCircle(ink, unit * 3f, p(62f, 49f))
        drawCircle(Color.White, unit * 1.1f, p(39f, 48f))
        drawCircle(Color.White, unit * 1.1f, p(63f, 48f))
        drawPath(triangle(45f, 66f, 55f, 66f, 50f, 72f), ink)
        drawLine(ink, p(50f, 72f), p(50f, 75f), unit * 1.6f, cap = StrokeCap.Round)
        drawLine(ink, p(50f, 75f), p(46f, 77f), unit * 1.3f, cap = StrokeCap.Round)
        drawLine(ink, p(50f, 75f), p(54f, 77f), unit * 1.3f, cap = StrokeCap.Round)
    }
}

/** Consistent, outline-only navigation glyphs; no font icons or Unicode shapes. */
@Composable
internal fun JibikNavigationIcon(position: Int, selected: Boolean) {
    val tint = if (selected) Color(0xFF07574C) else Color(0xFF657772)
    Canvas(modifier = Modifier.size(24.dp)) {
        val unit = size.width / 24f
        fun p(x: Float, y: Float) = Offset(x * unit, y * unit)
        val stroke = unit * 1.85f
        when (position) {
            0 -> {
                drawLine(tint, p(3f, 11f), p(12f, 3f), stroke, cap = StrokeCap.Round)
                drawLine(tint, p(12f, 3f), p(21f, 11f), stroke, cap = StrokeCap.Round)
                drawLine(tint, p(5f, 10f), p(5f, 21f), stroke, cap = StrokeCap.Round)
                drawLine(tint, p(19f, 10f), p(19f, 21f), stroke, cap = StrokeCap.Round)
                drawLine(tint, p(5f, 21f), p(19f, 21f), stroke, cap = StrokeCap.Round)
                drawLine(tint, p(10f, 21f), p(10f, 15f), stroke, cap = StrokeCap.Round)
                drawLine(tint, p(10f, 15f), p(14f, 15f), stroke, cap = StrokeCap.Round)
                drawLine(tint, p(14f, 15f), p(14f, 21f), stroke, cap = StrokeCap.Round)
            }
            1 -> {
                for (y in listOf(6f, 12f, 18f)) {
                    drawCircle(tint, unit * 1.4f, p(5f, y))
                    drawLine(tint, p(10f, y), p(21f, y), stroke, cap = StrokeCap.Round)
                }
            }
            else -> {
                drawCircle(tint, unit * 9f, p(12f, 12f), style = Stroke(stroke))
                drawCircle(tint, unit * 4.7f, p(12f, 12f), style = Stroke(stroke))
                drawCircle(tint, unit * 1.5f, p(12f, 12f))
            }
        }
    }
}
