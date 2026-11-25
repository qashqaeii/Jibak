package ir.jibik.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import java.math.BigInteger

@Composable
internal fun CashForecastChart(forecast: CashForecast) {
    val points = forecast.points
    if (points.isEmpty()) return
    var expanded by rememberSaveable(forecast.today, forecast.endExclusive) { mutableStateOf(false) }
    val scale = financeChartScale(points.map { it.available })
    val positive = JibikStyle.forest
    val negative = JibikStyle.coral
    val reveal = rememberChartReveal(points)
    JibikSectionTitle("مسیر مانده پیش‌بینی‌شده", "از امروز تا تاریخ انتخابی")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        ChartLegendDot("مانده مثبت", positive)
        ChartLegendDot("کسری", negative)
    }
    if (points.all { it.available == BigInteger.ZERO }) {
        JibikFormHint("مانده پیش‌بینی‌شده در نقاط این بازه صفر است.")
    } else FinanceChartFrame(scale, "نمودار پیش‌بینی؛ مانده پایان بازه " + forecastMoney(forecast.available),
        formatJalaliDueDate(points.first().day).takeLast(5), formatJalaliDueDate(points.last().day).takeLast(5)) {
        val inset = 5.dp.toPx()
        val span = (points.last().day - points.first().day).coerceAtLeast(1L)
        val zero = size.height * (1f - scale.fraction(BigInteger.ZERO))
        fun position(index: Int): Offset {
            val point = points[index]
            val fraction = if (points.size == 1) .5f else ((point.day - points.first().day).toDouble() / span).toFloat()
            val y = size.height * (1f - scale.fraction(point.available))
            return Offset(size.width - inset - (size.width - 2 * inset) * fraction, zero + (y - zero) * reveal)
        }
        points.indices.forEach { index ->
            val p = position(index)
            val tint = if (points[index].available.signum() < 0) negative else positive
            if (index > 0) {
                val previous = points[index - 1].available
                val current = points[index].available
                val from = position(index - 1)
                if (previous.signum() * current.signum() < 0) {
                    val ratio = previous.abs().toDouble() / (previous.abs() + current.abs()).toDouble()
                    val cross = Offset(from.x + (p.x - from.x) * ratio.toFloat(), zero)
                    drawLine(if (previous.signum() < 0) negative else positive, from, cross, 2.5.dp.toPx(), StrokeCap.Round)
                    drawLine(tint, cross, p, 2.5.dp.toPx(), StrokeCap.Round)
                } else drawLine(tint, from, p, 2.5.dp.toPx(), StrokeCap.Round)
            }
            drawCircle(tint, 3.dp.toPx(), p)
        }
    }
    JibikFormHint("نمونه‌های محاسبه‌شده؛ درآمد آینده لحاظ نشده است.")
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "بستن جزئیات نمودار" else "مشاهده مقادیر دقیق") }
    AnimatedVisibility(expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            points.forEach { point -> JibikFinancialDetail(formatJalaliDueDate(point.day), forecastMoney(point.available)) }
        }
    }
}
