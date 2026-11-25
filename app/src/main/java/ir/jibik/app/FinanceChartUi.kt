package ir.jibik.app

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/** Presentation-only chart geometry. All financial arithmetic stays exact in the domain. */
internal data class FinanceChartScale(val low: BigInteger, val high: BigInteger) {
    init { require(high > low) }
    fun fraction(value: BigInteger): Float =
        BigDecimal(value - low).divide(BigDecimal(high - low), 8, RoundingMode.HALF_UP)
            .toFloat().coerceIn(0f, 1f)
    val ticks: List<BigInteger> get() = listOf(high, (high + low) / 2.toBigInteger(), low).distinct()
}

internal fun financeChartScale(values: List<BigInteger>): FinanceChartScale {
    val low = (values.minOrNull() ?: BigInteger.ZERO).min(BigInteger.ZERO)
    val high = (values.maxOrNull() ?: BigInteger.ZERO).max(BigInteger.ZERO)
    return FinanceChartScale(low, if (high == low) low + BigInteger.ONE else high)
}

internal fun chartAmountLabel(value: BigInteger): String {
    val units = listOf(12 to " تریلیون", 9 to " میلیارد", 6 to " میلیون", 3 to " هزار")
    val unit = units.firstOrNull { value.abs() >= BigInteger.TEN.pow(it.first) }
    val format = NumberFormat.getNumberInstance(Locale("fa", "IR")).apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 0
    }
    return if (unit == null) format.format(value)
        else format.format(BigDecimal(value).movePointLeft(unit.first)) + unit.second
}

@Composable
internal fun rememberChartReveal(identity: Any): Float {
    var shown by remember(identity) { mutableStateOf(false) }
    LaunchedEffect(identity) { shown = true }
    val progress by animateFloatAsState(if (shown) 1f else 0f,
        animationSpec = tween(420), label = "financial-chart-reveal")
    return progress
}

internal fun DrawScope.financeGrid(scale: FinanceChartScale, line: Color, zero: Color) {
    scale.ticks.forEach { value ->
        val y = size.height * (1f - scale.fraction(value))
        drawLine(if (value == BigInteger.ZERO) zero else line, Offset(0f, y),
            Offset(size.width, y), strokeWidth = 1.dp.toPx())
    }
    if (BigInteger.ZERO !in scale.ticks) {
        val y = size.height * (1f - scale.fraction(BigInteger.ZERO))
        drawLine(zero, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
    }
}

@Composable
internal fun FinanceChartFrame(scale: FinanceChartScale, description: String,
    startLabel: String, endLabel: String, draw: DrawScope.() -> Unit) {
    val grid = JibikStyle.line
    val axis = JibikStyle.muted.copy(alpha = .55f)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("مقیاس: تومان", color = JibikStyle.muted, style = MaterialTheme.typography.labelSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.widthIn(min = 44.dp, max = 78.dp).height(146.dp),
                verticalArrangement = Arrangement.SpaceBetween) {
                scale.ticks.forEach { Text(chartAmountLabel(it), fontSize = 10.sp,
                    lineHeight = 15.sp, color = JibikStyle.muted) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Canvas(Modifier.fillMaxWidth().height(146.dp).semantics { contentDescription = description }) {
                    financeGrid(scale, grid, axis)
                    draw()
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(startLabel, color = JibikStyle.muted, style = MaterialTheme.typography.labelSmall)
                    if (startLabel != endLabel) Text(endLabel, color = JibikStyle.muted,
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
internal fun ChartLegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(9.dp).background(color, RoundedCornerShape(3.dp)))
        Text(label, color = JibikStyle.muted, style = MaterialTheme.typography.labelSmall)
    }
}
