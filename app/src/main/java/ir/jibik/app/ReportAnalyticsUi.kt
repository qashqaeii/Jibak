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
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(start, { start = it.take(10) }, label = { Text("از تاریخ") }, singleLine = true)
                OutlinedTextField(end, { end = it.take(10) }, label = { Text("تا تاریخ (شامل این روز)") }, singleLine = true)
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
        val maxValue = buckets.maxOfOrNull { maxOf(it.totals.income, it.totals.expense) }?.max(BigInteger.ONE)?.toDouble() ?: 1.0
        val green = JibikStyle.forest; val coral = JibikStyle.coral
        Canvas(Modifier.fillMaxWidth().height(126.dp).semantics {
            contentDescription = "نمودار ورودی و خروجی بازه؛ ورودی " + forecastMoney(totals.income) + "، خروجی " + forecastMoney(totals.expense)
        }) {
            val slot = size.width / buckets.size.coerceAtLeast(1)
            val width = slot * .30f
            buckets.forEachIndexed { i, bucket ->
                val x = size.width - (i + 1) * slot
                val income = (bucket.totals.income.toDouble() / maxValue * size.height).toFloat()
                val expense = (bucket.totals.expense.toDouble() / maxValue * size.height).toFloat()
                drawRect(green, Offset(x + slot * .15f, size.height - income), Size(width, income))
                drawRect(coral, Offset(x + slot * .55f, size.height - expense), Size(width, expense))
            }
        }
        Text("سبز: ورودی · مرجانی: خروجی", color = JibikStyle.muted)
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "بستن جزئیات" else "مقادیر دقیق نمودار") }
        if (expanded) buckets.forEach { bucket ->
            Text(bucket.range.label)
            JibikFinancialDetail("ورودی", forecastMoney(bucket.totals.income))
            JibikFinancialDetail("خروجی", forecastMoney(bucket.totals.expense))
            HorizontalDivider()
        }
    }
}
