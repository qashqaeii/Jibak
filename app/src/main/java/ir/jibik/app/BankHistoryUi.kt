package ir.jibik.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun BankHistoryPeriodDialog(onDismiss: () -> Unit, onScan: (Int?) -> Unit) {
    var days by remember { mutableStateOf<Int?>(30) }
    JibikModalDialog(onDismissRequest = onDismiss, modifier = Modifier.jibikDialogSurface(),
        properties = JibikWideDialogProperties,
        title = { JibikDialogHeading("ورود سوابق بانکی", "مرحله ۱ از ۲ · انتخاب بازه") },
        text = {
            Column(Modifier.fillMaxWidth().jibikScrollableBody(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30, 90, 180, null).forEach { value ->
                    JibikGlassChip(if (value == null) "آخرین پیام‌های موجود" else "$value روز گذشته",
                        selected = days == value, modifier = Modifier.fillMaxWidth(), onClick = { days = value })
                }
                JibikFormHint("حداکثر ۵۰۰ پیام اخیر صندوق در بازه انتخابی بررسی می‌شود؛ فقط سرشماره‌های معرفی‌شده تحلیل می‌شوند. هیچ تراکنشی خودکار ثبت نمی‌شود.")
            }
        },
        confirmButton = { LiquidGlassButton("اسکن و پیش‌نمایش", onClick = { onScan(days) }) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}

@Composable
internal fun BankHistoryPreviewDialog(preview: BankHistoryPreview, busy: Boolean,
    onDismiss: () -> Unit, onImport: (List<BankSmsMatch>) -> Unit) {
    var selected by remember(preview) { mutableStateOf(preview.novel.map { it.fingerprint }.toSet()) }
    var limit by remember(preview) { mutableStateOf(12) }
    val chosen = preview.novel.filter { it.fingerprint in selected }
    val incoming = chosen.filter { it.income }.fold(java.math.BigInteger.ZERO) { sum, row -> sum + requireNotNull(row.amountToman).toBigInteger() }
    val outgoing = chosen.filterNot { it.income }.fold(java.math.BigInteger.ZERO) { sum, row -> sum + requireNotNull(row.amountToman).toBigInteger() }
    JibikModalDialog(onDismissRequest = onDismiss, modifier = Modifier.jibikDialogSurface(),
        properties = JibikWideDialogProperties,
        title = { JibikDialogHeading("پیش‌نمایش سوابق", "مرحله ۲ از ۲ · بدون تغییر موجودی") },
        text = {
            Column(Modifier.fillMaxWidth().jibikScrollableBody(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                JibikSurfaceCard {
                    JibikFinancialDetail("پیشنهاد جدید", preview.novel.size.toString(), emphasis = true)
                    JibikFinancialDetail("پیام پردازش‌شده قبلی", preview.alreadyHandled.toString())
                    JibikFinancialDetail("جمع ورودی انتخابی", forecastMoney(incoming))
                    JibikFinancialDetail("جمع خروجی انتخابی", forecastMoney(outgoing))
                    JibikFormHint("این مبالغ گردش پیام‌ها هستند، نه درآمد یا هزینه قطعی؛ ممکن است شامل انتقال داخلی باشند.")
                }
                if (preview.comparisons.isNotEmpty()) {
                    JibikSectionTitle("آخرین مانده‌های قابل تشخیص", "در محدوده همین اسکن")
                    preview.comparisons.forEach { row ->
                        JibikSurfaceCard {
                            JibikSectionTitle(row.accountTitle, formatJalaliDueDate(row.atMillis))
                            JibikFinancialDetail("مانده پیامک", formatToman(row.reported))
                            JibikFinancialDetail("مانده فعلی جیبیک", formatToman(row.local))
                            JibikFinancialDetail("اختلاف", forecastMoney(row.difference), emphasis = true)
                        }
                    }
                    JibikFormHint("مانده پیامک ممکن است قدیمی باشد؛ اختلاف به‌تنهایی خطا نیست و هیچ اصلاحی انجام نمی‌شود.")
                }
                if (preview.novel.isEmpty()) JibikStatePanel(title = "پیشنهاد جدیدی پیدا نشد",
                    description = "پیام‌های معتبر این بازه ممکن است قبلاً وارد صندوق شده باشند.")
                else {
                    JibikSectionTitle("انتخاب پیام‌ها", chosen.size.toString() + " از " + preview.novel.size)
                    TextButton(enabled = !busy, onClick = {
                        selected = if (selected.size == preview.novel.size) emptySet() else preview.novel.map { it.fingerprint }.toSet()
                    }) { Text(if (selected.size == preview.novel.size) "لغو انتخاب همه" else "انتخاب همه") }
                    preview.novel.groupBy { it.bankId }.values.forEach { group ->
                        val ids = group.map { it.fingerprint }.toSet()
                        val all = selected.containsAll(ids)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(all, enabled = !busy, onCheckedChange = { selected = if (it) selected + ids else selected - ids })
                            Text(group.first().bankTitle + " · " + group.size + " پیام")
                        }
                    }
                    preview.novel.take(limit).forEach { row ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(row.fingerprint in selected, enabled = !busy, onCheckedChange = {
                                selected = if (it) selected + row.fingerprint else selected - row.fingerprint
                            })
                            Column(Modifier.weight(1f)) {
                                Text(row.bankTitle + " · " + if (row.income) "واریز" else "برداشت")
                                JibikFormHint(formatJalaliDueDate(row.receivedAtMillis))
                            }
                            Text(formatToman(requireNotNull(row.amountToman)))
                        }
                    }
                    if (limit < preview.novel.size) TextButton(onClick = { limit += 30 }) { Text("نمایش پیام‌های بیشتر") }
                }
            }
        },
        confirmButton = { LiquidGlassButton(if (busy) "در حال ورود…" else "ورود به صندوق بررسی",
            enabled = !busy && chosen.isNotEmpty(), onClick = { onImport(chosen) }) },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("بستن") } })
}
