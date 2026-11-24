package ir.jibik.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import ir.jibik.core.finance.LedgerKind
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

/** Crisp, typography-independent search icon used by all search fields. */
@Composable
internal fun JibikSearchIcon(modifier: Modifier = Modifier.size(20.dp)) {
    Canvas(modifier = modifier) {
        val unit = size.minDimension / 24f
        val stroke = unit * 2f
        val green = Color(0xFF07574C)
        drawCircle(
            color = green,
            radius = 6.7f * unit,
            center = Offset(10f * unit, 10f * unit),
            style = Stroke(width = stroke),
        )
        drawLine(
            color = green,
            start = Offset(15f * unit, 15f * unit),
            end = Offset(21f * unit, 21f * unit),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Small money-direction symbols are real Compose geometry, not font glyphs
 * and not mascot artwork. The card itself owns the interaction target.
 */
@Composable
internal fun JibikMoneyDirectionIcon(
    kind: LedgerKind,
    tint: Color,
    modifier: Modifier = Modifier.size(42.dp),
) {
    Canvas(modifier) {
        val unit = size.minDimension / 42f
        val x = size.width / 2f
        val y = size.height / 2f
        val stroke = 2.7f * unit
        fun point(dx: Float, dy: Float) = Offset(x + dx * unit, y + dy * unit)
        when (kind) {
            LedgerKind.EXPENSE -> {
                drawLine(tint, point(-10f, -10f), point(9f, 9f), stroke, cap = StrokeCap.Round)
                drawLine(tint, point(-1f, 9f), point(9f, 9f), stroke, cap = StrokeCap.Round)
                drawLine(tint, point(9f, -1f), point(9f, 9f), stroke, cap = StrokeCap.Round)
            }
            LedgerKind.INCOME -> {
                drawLine(tint, point(10f, 10f), point(-9f, -9f), stroke, cap = StrokeCap.Round)
                drawLine(tint, point(1f, -9f), point(-9f, -9f), stroke, cap = StrokeCap.Round)
                drawLine(tint, point(-9f, 1f), point(-9f, -9f), stroke, cap = StrokeCap.Round)
            }
            LedgerKind.TRANSFER -> {
                drawLine(tint, point(-12f, -6f), point(11f, -6f), stroke, cap = StrokeCap.Round)
                drawLine(tint, point(4f, -12f), point(11f, -6f), stroke, cap = StrokeCap.Round)
                drawLine(tint, point(4f, 0f), point(11f, -6f), stroke, cap = StrokeCap.Round)
                drawLine(tint, point(12f, 7f), point(-11f, 7f), stroke, cap = StrokeCap.Round)
                drawLine(tint, point(-4f, 1f), point(-11f, 7f), stroke, cap = StrokeCap.Round)
                drawLine(tint, point(-4f, 13f), point(-11f, 7f), stroke, cap = StrokeCap.Round)
            }
        }
    }
}

/** A small frameless account icon, consistent with the navigation glyphs. */
@Composable
internal fun JibikAccountIcon(
    wallet: Boolean,
    modifier: Modifier = Modifier.size(42.dp),
) {
    Canvas(modifier = modifier) {
        val unit = size.minDimension / 42f
        val stroke = 2.2f * unit
        val green = Color(0xFF07574C)
        drawRoundRect(
            color = green,
            topLeft = Offset(7f * unit, 12f * unit),
            size = Size(28f * unit, 20f * unit),
            cornerRadius = CornerRadius(4f * unit),
            style = Stroke(stroke),
        )
        if (wallet) {
            drawLine(
                color = green,
                start = Offset(9f * unit, 12f * unit),
                end = Offset(30f * unit, 8f * unit),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawCircle(green, radius = 1.8f * unit,
                center = Offset(29f * unit, 22f * unit))
        } else {
            drawLine(green,
                start = Offset(9f * unit, 19f * unit),
                end = Offset(33f * unit, 19f * unit),
                strokeWidth = stroke,
            )
            drawLine(green,
                start = Offset(12f * unit, 26f * unit),
                end = Offset(21f * unit, 26f * unit),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
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
            2 -> {
                drawCircle(tint, unit * 9f, p(12f, 12f), style = Stroke(stroke))
                drawCircle(tint, unit * 4.7f, p(12f, 12f), style = Stroke(stroke))
                drawCircle(tint, unit * 1.5f, p(12f, 12f))
            }
            3 -> {
                // Reports: three minimal bars with a shared baseline.
                drawLine(tint, p(3f, 21f), p(21f, 21f), stroke, cap = StrokeCap.Round)
                drawLine(tint, p(6f, 18f), p(6f, 12f), unit * 4f, cap = StrokeCap.Round)
                drawLine(tint, p(12f, 18f), p(12f, 5f), unit * 4f, cap = StrokeCap.Round)
                drawLine(tint, p(18f, 18f), p(18f, 9f), unit * 4f, cap = StrokeCap.Round)
            }
            else -> {
                // Wallet glyph for the dedicated financial accounts screen.
                drawRoundRect(
                    color = tint,
                    topLeft = p(3f, 6f),
                    size = androidx.compose.ui.geometry.Size(18f * unit, 15f * unit),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * unit),
                    style = Stroke(stroke),
                )
                drawLine(tint, p(3f, 9f), p(3f, 4f), stroke, cap = StrokeCap.Round)
                drawLine(tint, p(3f, 4f), p(18f, 4f), stroke, cap = StrokeCap.Round)
                drawCircle(tint, unit * 1.3f, p(16.5f, 13.5f))
            }
        }
    }
}

/** Distinct brand mark; transparent WebP that stays crisp on light surfaces. */
@Composable
internal fun JibikBrandLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.jibik_logo),
        contentDescription = "لوگوی جیبیک",
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
