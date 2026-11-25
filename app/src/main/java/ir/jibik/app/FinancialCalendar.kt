package ir.jibik.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.jibik.core.finance.Account
import kotlinx.coroutines.launch
import java.util.UUID

private data class CalendarRow(val day: Long, val title: String, val amount: Long,
    val status: String, val rule: RecurringRule? = null, val pending: Boolean = false)

@Composable
internal fun FinancialCalendar(store: LocalLedgerStore, accounts: List<Account>,
    obligations: List<FinancialObligationEntity>, payments: List<ObligationPaymentEntity>,
    onLedgerChange: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    LiquidGlassCard(onClick = { open = true }, tone = GlassTone.CRYSTAL) {
        JibikSectionTitle("تقویم مالی", "سررسیدها، حقوق و پرداخت‌های ماهانه")
    }
    if (!open) return
    var start by rememberSaveable { mutableLongStateOf(currentJalaliMonth().startMillis) }
    val month = remember(start) { currentJalaliMonth(start) }
    var mode by rememberSaveable { mutableStateOf("calendar") }
    var selected by rememberSaveable { mutableStateOf("") }
    var actual by rememberSaveable { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var rules by remember { mutableStateOf(emptyList<RecurringRule>()) }
    var decisions by remember(month) { mutableStateOf(emptyList<RecurringDecision>()) }
    var loaded by remember(month, refresh) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(month, refresh) {
        try {
            rules = store.recurringRules()
            decisions = store.recurringDecisions(month)
            loaded = true
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { error = "تقویم بارگذاری نشد." }
    }
    fun submit(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { action(); mode = "calendar"; error = ""; refresh++; onLedgerChange() }
            catch (_: Exception) { error = "ذخیره انجام نشد. وضعیت نوبت را دوباره بررسی کن."; refresh++ }
            finally { busy = false }
        }
    }
    val rows = remember(month, rules, decisions, obligations, payments) {
        buildList<CalendarRow> {
            rules.forEach { rule ->
                val day = rule.dueIn(month) ?: return@forEach
                val decision = decisions.firstOrNull { it.ruleId == rule.id && it.day == day }
                val status = when {
                    decision == null -> if (rule.income) "دریافت برنامه‌ریزی‌شده" else "پرداخت برنامه‌ریزی‌شده"
                    decision.transactionId == null -> "رد شده"
                    else -> "ثبت شده"
                }
                add(CalendarRow(day, rule.title, decision?.actual ?: rule.amount, status, rule, decision == null))
            }
            obligations.forEach { item ->
                val first = currentJalaliMonth(item.dueAtMillis)
                val index = (month.year - first.year) * 12 + month.month - first.month
                if (index !in 0 until item.installmentCount) return@forEach
                val base = item.totalToman / item.installmentCount
                val extra = item.totalToman % item.installmentCount
                val before = base * index + minOf(index.toLong(), extra)
                val amount = base + if (index < extra) 1 else 0
                val paid = obligationProgress(item, payments).paidToman
                val remaining = amount - (paid - before).coerceIn(0L, amount)
                add(CalendarRow(jalaliInstallmentDueDate(item.dueAtMillis, index), item.title,
                    if (remaining == 0L) amount else remaining,
                    if (remaining == 0L) "تسویه‌شده" else if (item.kind == "RECEIVABLE") "ماندهٔ طلب" else "ماندهٔ تعهد"))
            }
        }.sortedBy { it.day }
    }
    val row = rows.firstOrNull { it.rule?.id == selected }
    var title by rememberSaveable { mutableStateOf("") }
    var income by rememberSaveable { mutableStateOf(false) }
    var estimate by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }
    var account by rememberSaveable { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }
    fun back() { if (mode == "calendar") open = false else mode = "calendar" }
    JibikModalDialog(
        modifier = Modifier.jibikDialogSurface(), properties = JibikWideDialogProperties,
        onDismissRequest = { if (!busy) back() },
        title = { JibikDialogHeading(when(mode) {
            "new" -> "ثبت دوره‌ای جدید"
            "review" -> "بررسی نوبت"
            "manage" -> "مدیریت تکرارها"
            else -> "تقویم مالی"
        }, month.title) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (mode) {
                    "new" -> {
                        JibikSegmentedControl(listOf(false to "هزینه", true to "درآمد"), income, onSelected = { income = it })
                        OutlinedTextField(title, { title = it.take(70) }, label = { Text("عنوان، مثلاً اجاره") })
                        JibikTomanField(estimate, { estimate = it }, "مبلغ معمول")
                        OutlinedTextField(date, { date = it.take(12) }, label = { Text("اولین سررسید شمسی (سال/ماه/روز)") })
                        JibikAccountChoice("حساب", accounts, account, { account = it })
                        JibikFormHint("هر ماه در همین روز تکرار می‌شود. برای ماه کوتاه‌تر، آخرین روز ماه لحاظ می‌شود.")
                    }
                    "review" -> if (row != null) {
                        Text(row.title + " · " + formatJalaliDueDate(row.day))
                        Text("حساب: " + accounts.firstOrNull { it.id == row.rule?.accountId }?.title.orEmpty())
                        JibikTomanField(actual, { actual = it }, "مبلغ واقعی")
                        JibikFormHint("تأیید، گردش مالی را به تاریخ امروز ثبت می‌کند. رد کردن فقط همین نوبت را رد می‌کند.")
                        TextButton(enabled = !busy && loaded && row.pending, onClick = {
                            submit { store.decideRecurring(selected, row.day, null) }
                        }) { Text("رد همین نوبت") }
                    }
                    "manage" -> {
                        if (rules.isEmpty()) Text("هنوز تکراری تعریف نشده است.")
                        rules.forEach { rule ->
                            Text(rule.title + " · " + formatToman(rule.amount))
                            if (rule.stoppedAt == null) TextButton(enabled = !busy, onClick = {
                                selected = rule.id; mode = "stop"
                            }) { Text("توقف نوبت‌های آینده") } else Text("متوقف شده")
                        }
                    }
                    "stop" -> Text("نوبت‌های آیندهٔ «" + rules.firstOrNull { it.id == selected }?.title.orEmpty() +
                        "» متوقف شوند؟ نوبت‌های گذشته در تقویم می‌مانند.")
                    else -> {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { start = offsetJalaliMonth(start, -1) }) { Text("ماه قبل") }
                            TextButton(onClick = { start = currentJalaliMonth().startMillis }) { Text("این ماه") }
                            TextButton(onClick = { start = offsetJalaliMonth(start, 1) }) { Text("ماه بعد") }
                        }
                        TextButton(onClick = { title = ""; estimate = ""; date = formatJalaliDueDate(System.currentTimeMillis())
                            income = false; mode = "new" }) { Text("＋ تعریف ثبت ماهانه") }
                        TextButton(onClick = { mode = "manage" }) { Text("مدیریت تکرارها") }
                        if (!loaded && error.isEmpty()) Text("در حال بارگذاری…")
                        if (loaded && rows.isEmpty()) Text("این ماه سررسیدی ثبت نشده است.")
                        if (loaded) rows.forEach { entry ->
                            HorizontalDivider()
                            Text(formatJalaliDueDate(entry.day) + " · " + entry.title)
                            Text(entry.status + " · " + formatToman(entry.amount))
                            if (entry.pending && entry.day <= System.currentTimeMillis())
                                TextButton(onClick = { selected = requireNotNull(entry.rule).id
                                    actual = entry.amount.toString(); mode = "review" }) { Text("بررسی نوبت") }
                        }
                    }
                }
                if (error.isNotEmpty()) TextButton(onClick = { error = ""; refresh++ }) { Text("$error تلاش دوباره") }
            }
        },
        confirmButton = {
            val day = parseJalaliDueDate(date)
            val amount = parseTomanDraft(if (mode == "new") estimate else actual)
            when (mode) {
                "new" -> LiquidGlassButton(text = "ذخیرهٔ تکرار", enabled = !busy && title.isNotBlank() &&
                    day != null && amount != null && amount > 0 && accounts.any { it.id == account },
                    onClick = { submit {
                        val first = requireNotNull(day)
                        store.addRecurring(RecurringRule(UUID.randomUUID().toString(), title.trim(), income,
                            account, requireNotNull(amount), first))
                        start = currentJalaliMonth(first).startMillis
                    } })
                "review" -> LiquidGlassButton(text = "تأیید و ثبت", enabled = loaded && !busy && row?.pending == true && amount != null && amount > 0,
                    onClick = { submit { store.decideRecurring(selected, requireNotNull(row).day, requireNotNull(amount)) } })
                "stop" -> LiquidGlassButton(text = "تأیید توقف", enabled = !busy,
                    onClick = { submit { store.stopRecurring(selected) } })
                else -> LiquidGlassButton(text = if (mode == "calendar") "بستن" else "بازگشت", onClick = { back() })
            }
        },
        dismissButton = { if (mode in listOf("stop", "new", "review"))
            TextButton(enabled = !busy, onClick = { back() }) { Text("انصراف") } },
    )
}
