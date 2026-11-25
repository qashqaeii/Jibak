package ir.jibik.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.math.BigInteger

@Composable
internal fun CashForecastChart(forecast: CashForecast) {
    val points = forecast.points
    if (points.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    val maxAbs = points.maxOf { it.available.abs() }.max(BigInteger.ONE).toDouble()
    val positive = JibikStyle.forest
    val negative = JibikStyle.warningText
    val axis = JibikStyle.muted
    JibikSectionTitle("مسیر مانده پیش‌بینی‌شده", "نمونه‌های زمانی · بدون درآمد آینده")
    Canvas(Modifier.fillMaxWidth().height(120.dp).semantics {
        contentDescription = "نمودار پیش‌بینی؛ مانده پایان بازه " + forecastMoney(forecast.available)
    }) {
        val mid = size.height / 2f
        val step = size.width / points.size
        drawLine(axis.copy(alpha = .45f), Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())
        points.forEachIndexed { index, point ->
            val x = size.width - (index + .5f) * step
            val length = (point.available.toDouble() / maxAbs * (mid - 8.dp.toPx())).toFloat()
            drawLine(if (point.available.signum() < 0) negative else positive,
                Offset(x, mid), Offset(x, mid - length), strokeWidth = minOf(14.dp.toPx(), step * .6f), cap = StrokeCap.Round)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatJalaliDueDate(points.first().day), color = JibikStyle.muted)
        if (points.size > 1) Text(formatJalaliDueDate(points.last().day), color = JibikStyle.muted)
    }
    Text("بالای خط: مانده مثبت · پایین خط: کسری", color = JibikStyle.muted)
    TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "بستن جزئیات نمودار" else "مشاهده مقادیر دقیق") }
    if (expanded) points.forEach { point ->
        JibikFinancialDetail(formatJalaliDueDate(point.day), forecastMoney(point.available))
    }
}
