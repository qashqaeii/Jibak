package ir.jibik.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Optimised transparent WebP; same mascot across welcome, header and empty states. */
@Composable
internal fun JiboMascot(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.jibo_welcome),
        contentDescription = "جیبو، روباه راهنمای جیبیک",
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
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
