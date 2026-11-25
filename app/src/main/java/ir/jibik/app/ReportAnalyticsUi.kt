package ir.jibik.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.math.BigInteger

@Composable
internal fun ReportRangeSelector(preset: String, range: ReportRange, onPreset: (String) -> Unit) {
    JibikSurfaceCard {
        JibikSectionTitle("بازه گزارش", range.label)
        JibikSegmentedControl(listOf("DAY" to "امروز", "WEEK" to "هفته", "MONTH" to "ماه"), preset, onSelected = onPreset)
        JibikSegmentedControl(listOf("YEAR" to "سال", "CUSTOM" to "دلخواه"), preset, onSelected = onPreset)
        JibikFormHint("بازه‌های جاری تا پایان امروز؛ هفته از شنبه شروع می‌شود.")
    }
}

@Composable
internal fun CustomReportRangeDialog(current: ReportRange, today: Long, onDismiss: () -> Unit,
    onChoose: (ReportRange) -> Unit) {
    var start by rememberSaveable { mutableStateOf(formatJalaliDueDate(current.start)) }
    var end by rememberSaveable { mutableStateOf(formatJalaliDueDate(current.endExclusive - 1)) }
    val a = parseJalaliDueDate(start)
    val b = parseJalaliDueDate(end)
    val valid = a != null && b != null && a > 0 && b >= a && b <= today
    JibikModalDialog(properties = JibikWideDialogProperties, modifier = Modifier.jibikDialogSurface(),
        onDismissRequest = onDismiss, title = { JibikDialogHeading("بازه دلخواه", "تاریخ شمسی · سال/ماه/روز") },
        text = {
            Column(Modifier.fillMaxWidth().jibikScrollableBody(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(start, { start = it.take(10) }, label = { Text("از تاریخ") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(end, { end = it.take(10) }, label = { Text("تا تاریخ (شامل این روز)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (!valid) JibikFormHint("تاریخ پایان باید بین آغاز و امروز باشد.", isWarning = true)
            }
        },
        confirmButton = { LiquidGlassButton("نمایش گزارش", enabled = valid, onClick = {
            onChoose(ReportRange(requireNotNull(a), jalaliDueDayEndExclusive(requireNotNull(b))))
        }) }, dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}

@Composable
internal fun ReportTrendCard(range: ReportRange, totals: ReportTotals, previous: ReportTotals, buckets: List<ReportBucket>) {
    var expanded by rememberSaveable(range.start, range.endExclusive) { mutableStateOf(false) }
    JibikSurfaceCard {
        JibikSectionTitle("روند و مقایسه", "بازه قبلی هم‌طول: " + previousReportRange(range).label)
        JibikFinancialDetail("تغییر ورودی نقدی", forecastMoney(totals.income - previous.income))
        JibikFinancialDetail("تغییر خروجی نقدی", forecastMoney(totals.expense - previous.expense))
        JibikFinancialDetail("نرخ پس‌انداز نقدی", reportRateText(totals.savingsRate), emphasis = true)
        JibikFormHint("نرخ پس‌انداز نقدی = خالص جریان نقدی تقسیم بر ورودی. با ورودی صفر نامشخص است؛ سود دارایی و انتقال حساب در آن نیست.")
        val values = buckets.flatMap { listOf(it.totals.income, it.totals.expense) }
        val scale = financeChartScale(values)
        val green = JibikStyle.forest
        val coral = JibikStyle.coral
        val reveal = rememberChartReveal(buckets)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ChartLegendDot("ورودی نقدی", green)
            ChartLegendDot("خروجی نقدی", coral)
        }
        if (values.all { it == BigInteger.ZERO }) {
            JibikFormHint("در این بازه گردش نقدی ثبت نشده است.")
        } else {
            FinanceChartFrame(scale, "نمودار ورودی و خروجی؛ ورودی " + forecastMoney(totals.income) +
                "، خروجی " + forecastMoney(totals.expense),
                formatJalaliDueDate(range.start).takeLast(5),
                formatJalaliDueDate(range.endExclusive - 1).takeLast(5)) {
                val slot = size.width / buckets.size.coerceAtLeast(1)
                val width = minOf(slot * .29f, 22.dp.toPx())
                val base = size.height
                buckets.forEachIndexed { index, bucket ->
                    val center = size.width - (index + .5f) * slot
                    val gap = minOf(slot * .08f, 4.dp.toPx())
                    fun bar(value: BigInteger, x: Float, color: androidx.compose.ui.graphics.Color) {
                        val height = size.height * scale.fraction(value) * reveal
                        if (height > 0f) drawRoundRect(color, Offset(x, base - height),
                            Size(width, height), CornerRadius(minOf(4.dp.toPx(), width / 2f, height / 2f)))
                        else drawLine(color.copy(alpha = .24f), Offset(x, base - 1f),
                            Offset(x + width, base - 1f), strokeWidth = 1.dp.toPx())
                    }
                    bar(bucket.totals.income, center + gap / 2, green)
                    bar(bucket.totals.expense, center - gap / 2 - width, coral)
                }
            }
        }
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "بستن جزئیات" else "مشاهده مقادیر دقیق نمودار") }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                buckets.forEach { bucket ->
                    Text(bucket.range.label, style = MaterialTheme.typography.labelLarge, color = JibikStyle.muted)
                    JibikFinancialDetail("ورودی", forecastMoney(bucket.totals.income))
                    JibikFinancialDetail("خروجی", forecastMoney(bucket.totals.expense))
                    HorizontalDivider()
                }
            }
        }
    }
}
