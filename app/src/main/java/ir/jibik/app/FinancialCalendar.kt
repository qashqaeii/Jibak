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
import ir.jibik.core.finance.LedgerRecord
import ir.jibik.core.finance.LedgerKind
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

private data class CalendarRow(val day: Long, val title: String, val amount: Long,
    val status: String, val rule: RecurringRule? = null, val pending: Boolean = false,
    val obligationId: String? = null, val receiving: Boolean = false)

@Composable
internal fun FinancialCalendar(store: LocalLedgerStore, accounts: List<Account>,
    obligations: List<FinancialObligationEntity>, payments: List<ObligationPaymentEntity>,
    requestedDay: Long, onOpened: () -> Unit, source: ir.jibik.core.finance.LedgerRecord?,
    onSourceUsed: () -> Unit, records: List<LedgerRecord>, allDecisions: List<RecurringDecision>,
    onPayObligation: (String) -> Unit, onLedgerChange: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    LiquidGlassCard(onClick = { open = true }, tone = GlassTone.CRYSTAL) {
        JibikSectionTitle("تقویم مالی", "سررسیدها، حقوق و پرداخت‌های ماهانه")
    }
    var start by rememberSaveable { mutableLongStateOf(currentJalaliMonth().startMillis) }
    LaunchedEffect(requestedDay) {
        if (requestedDay > 0) {
            start = currentJalaliMonth(requestedDay).startMillis
            open = true
            onOpened()
        }
    }
    LaunchedEffect(source?.id) { if (source != null) open = true }
    if (!open) return
    val month = remember(start) { currentJalaliMonth(start) }
    var mode by rememberSaveable { mutableStateOf("calendar") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var selected by rememberSaveable { mutableStateOf("") }
    var selectedDay by rememberSaveable { mutableLongStateOf(0L) }
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
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "ذخیره انجام نشد. وضعیت نوبت را دوباره بررسی کن."; refresh++ }
            finally { busy = false }
        }
    }
    val rows = remember(month, rules, decisions, obligations, payments) {
        buildList<CalendarRow> {
            rules.forEach { rule ->
                rule.dueBetween(month.startMillis, month.endExclusiveMillis).forEach { day ->
                val decision = decisions.firstOrNull { it.ruleId == rule.id && it.day == day }
                val status = when {
                    decision == null -> if (rule.income) "دریافت برنامه‌ریزی‌شده" else "پرداخت برنامه‌ریزی‌شده"
                    decision.transactionId == null -> "رد شده"
                    else -> "ثبت شده"
                }
                add(CalendarRow(day, rule.title, decision?.actual ?: rule.amount, status, rule, decision == null, receiving = rule.income))
                }
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
                    if (remaining == 0L) "تسویه‌شده" else if (item.kind == "RECEIVABLE") "ماندهٔ طلب" else "ماندهٔ تعهد",
                    pending = remaining > 0, obligationId = item.id, receiving = item.kind == "RECEIVABLE"))
            }
        }.sortedBy { it.day }
    }
    val usedIds = payments.map { it.transactionId }.toSet() + allDecisions.mapNotNull { it.transactionId } + decisions.mapNotNull { it.transactionId }
    val row = rows.firstOrNull { it.rule?.id == selected && it.day == selectedDay }
    var title by rememberSaveable { mutableStateOf("") }
    var income by rememberSaveable { mutableStateOf(false) }
    var estimate by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }
    var frequency by rememberSaveable { mutableStateOf("MONTHLY") }
    var intervalText by rememberSaveable { mutableStateOf("1") }
    var endText by rememberSaveable { mutableStateOf("") }
    var account by rememberSaveable { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }
    LaunchedEffect(source?.id) {
        if (source != null) {
            income = source.kind == ir.jibik.core.finance.LedgerKind.INCOME
            title = source.note.ifBlank { source.category }.take(70)
            account = (source.fromAccountId ?: source.toAccountId).orEmpty()
            estimate = source.amountToman.toString()
            date = formatJalaliDueDate(offsetJalaliMonth(source.timestampMillis, 1))
            mode = "new"
            onSourceUsed()
        }
    }
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
                        JibikSegmentedControl(listOf("DAILY" to "روزانه", "WEEKLY" to "هفتگی", "MONTHLY" to "ماهانه", "YEARLY" to "سالانه"),
                            frequency, onSelected = { frequency = it })
                        OutlinedTextField(intervalText, { intervalText = it.take(3) }, label = { Text("هر چند " + recurringFrequencyLabel(frequency) + "؟") }, singleLine = true)
                        OutlinedTextField(endText, { endText = it.take(12) }, label = { Text("آخرین تاریخ شمسی (اختیاری)") }, singleLine = true)
                        JibikFormHint("تکرار به تاریخ نخست متصل است؛ ماه کوتاه‌تر به آخرین روز همان ماه محدود می‌شود.")
                    }
                    "review" -> if (row != null) {
                        Text(row.title + " · " + formatJalaliDueDate(row.day))
                        Text("حساب: " + accounts.firstOrNull { it.id == row.rule?.accountId }?.title.orEmpty())
                        val rule = row.rule
                        if (rule != null) {
                            val matches = records.filter {
                                it.id !in usedIds &&
                                    it.kind == (if (rule.income) LedgerKind.INCOME else LedgerKind.EXPENSE) &&
                                    (it.fromAccountId ?: it.toAccountId) == rule.accountId &&
                                    kotlin.math.abs(it.timestampMillis - row.day) < 36L * 60 * 60 * 1000
                            }.sortedBy { if (it.amountToman == rule.amount) 0 else 1 }
                            if (matches.isNotEmpty()) JibikFormHint("اگر قبلاً پرداخت کرده‌ای، به تراکنش موجود وصل کن.")
                            matches.forEach { match ->
                                TextButton(enabled = loaded && !busy && row.pending, onClick = {
                                    submit { store.linkRecurring(rule.id, row.day, match) }
                                }) { Text(listOf("اتصال", formatToman(match.amountToman), formatJalaliDueDate(match.timestampMillis),
                                    match.note, match.id.take(8)).filter { it.isNotBlank() }.joinToString(" · ")) }
                            }
                        }
                        JibikTomanField(actual, { actual = it }, "مبلغ واقعی")
                        JibikFormHint("تأیید، گردش مالی را به تاریخ امروز ثبت می‌کند. رد کردن فقط همین نوبت را رد می‌کند.")
                        TextButton(enabled = !busy && loaded && row.pending, onClick = {
                            submit { store.decideRecurring(selected, row.day, null) }
                        }) { Text("رد همین نوبت") }
                    }
                    "manage" -> {
                        if (rules.isEmpty()) Text("هنوز تکراری تعریف نشده است.")
                        rules.forEach { rule ->
                            Text(rule.title + " · " + formatToman(rule.amount) + " · هر " + rule.interval + " " + recurringFrequencyLabel(rule.frequency))
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
                            income = false; frequency = "MONTHLY"; intervalText = "1"; endText = ""; mode = "new" }) { Text("＋ تعریف ثبت دوره‌ای") }
                        TextButton(onClick = { mode = "manage" }) { Text("مدیریت تکرارها") }
                        if (!loaded && error.isEmpty()) Text("در حال بارگذاری…")
                        if (loaded) {
                            val plannedIncome = rows.filter { it.pending && it.receiving }.fold(java.math.BigInteger.ZERO) { sum, r -> sum + r.amount.toBigInteger() }
                            val plannedExpense = rows.filter { it.pending && !it.receiving }.fold(java.math.BigInteger.ZERO) { sum, r -> sum + r.amount.toBigInteger() }
                            JibikSurfaceCard {
                                JibikSectionTitle("خلاصه ماه", "${rows.count { it.pending }} نوبت باز · ${rows.count { !it.pending }} تعیین تکلیف‌شده")
                                Text("دریافت برنامه‌ریزی‌شده: " + plannedIncome.toString() + " تومان", color = JibikStyle.forest)
                                Text("پرداخت برنامه‌ریزی‌شده: " + plannedExpense.toString() + " تومان", color = JibikStyle.muted)
                                JibikFormHint("این مبالغ پیش‌بینی‌اند، نه گردش ثبت‌شده حساب.")
                            }
                            JibikSegmentedControl(listOf("all" to "همه", "open" to "باز", "closed" to "بسته"),
                                filter, onSelected = { filter = it })
                            val visible = rows.filter { filter == "all" || (filter == "open" && it.pending) || (filter == "closed" && !it.pending) }
                            if (visible.isEmpty()) Text("موردی در این بخش نیست.")
                            visible.groupBy { formatJalaliDueDate(it.day) }.forEach { (dateLabel, entries) ->
                                JibikSectionTitle(dateLabel, "${entries.size} نوبت")
                                entries.forEach { entry ->
                                    JibikSurfaceCard {
                                        Text(entry.title, fontWeight = FontWeight.Bold)
                                        Text(entry.status + " · " + formatToman(entry.amount), color = JibikStyle.muted)
                                        if (entry.pending && entry.day <= System.currentTimeMillis()) {
                                            if (entry.rule != null) TextButton(enabled = !busy, onClick = {
                                                selected = entry.rule.id; selectedDay = entry.day; actual = entry.amount.toString(); mode = "review"
                                            }) { Text("بررسی نوبت") }
                                            else if (entry.obligationId != null) TextButton(enabled = !busy, onClick = {
                                                open = false; onPayObligation(entry.obligationId)
                                            }) { Text("بررسی تعهد") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (error.isNotEmpty()) TextButton(onClick = { error = ""; refresh++ }) { Text("$error تلاش دوباره") }
            }
        },
        confirmButton = {
            val day = parseJalaliDueDate(date)
            val interval = intervalText.map { it.digitToIntOrNull()?.toString() ?: it.toString() }.joinToString("").toIntOrNull()
            val end = parseJalaliDueDate(endText)
            val amount = parseTomanDraft(if (mode == "new") estimate else actual)
            when (mode) {
                "new" -> LiquidGlassButton(text = "ذخیرهٔ تکرار", enabled = !busy && title.isNotBlank() &&
                    day != null && amount != null && amount > 0 && interval != null && interval in 1..365 &&
                    (endText.isBlank() || (end != null && end >= day)) && accounts.any { it.id == account },
                    onClick = { submit {
                        val first = requireNotNull(day)
                        store.addRecurring(RecurringRule(UUID.randomUUID().toString(), title.trim(), income,
                            account, requireNotNull(amount), first, frequency = frequency, interval = requireNotNull(interval), endDay = end))
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
