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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
internal fun AccountManagementDialog(store: LocalLedgerStore, account: Account, balance: Long,
    onDismiss: () -> Unit, onChanged: () -> Unit) {
    var mode by rememberSaveable(account.id) { mutableStateOf("DETAIL") }
    var title by rememberSaveable(account.id) { mutableStateOf(account.title) }
    var actual by rememberSaveable(account.id) { mutableStateOf(balance.toString()) }
    var reason by rememberSaveable(account.id) { mutableStateOf("") }
    var history by remember { mutableStateOf(emptyList<AccountEditEvent>()) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(account.id) {
        try { history = store.accountHistory(account.id) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "سوابق قابل خواندن نیست." }
    }
    fun save(operation: suspend () -> Unit) {
        if (busy) return
        busy = true; error = ""
        scope.launch {
            try { operation(); onChanged() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "تغییر انجام نشد؛ مانده، وابستگی‌ها یا اطلاعات جدید حساب را بررسی کن." }
            finally { busy = false }
        }
    }
    val parsed = parseAccountAmount(actual)
    JibikModalDialog(properties = JibikWideDialogProperties, modifier = Modifier.jibikDialogSurface(),
        onDismissRequest = { if (!busy) onDismiss() },
        bodyKey = mode,
        title = { JibikDialogHeading(when(mode) {
            "RENAME" -> "ویرایش نام حساب"; "RECONCILE" -> "اصلاح موجودی"; "ARCHIVE" -> "بایگانی حساب"; "RESTORE" -> "بازیابی حساب"; else -> "مدیریت حساب"
        }, account.title) },
        text = {
            Column(Modifier.fillMaxWidth().jibikScrollableBody(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                JibikFinancialDetail("مانده ثبت‌شده", formatToman(balance), emphasis = true)
                when(mode) {
                    "RENAME" -> OutlinedTextField(title, { title = it.take(48) }, modifier = Modifier.fillMaxWidth(), label = { Text("نام جدید") }, enabled = !busy, singleLine = true)
                    "RECONCILE" -> {
                        OutlinedTextField(actual, { actual = it.take(24) }, modifier = Modifier.fillMaxWidth(), label = { Text("موجودی واقعی (تومان؛ منفی مجاز)") }, enabled = !busy, singleLine = true)
                        OutlinedTextField(reason, { reason = it.take(200) }, modifier = Modifier.fillMaxWidth(), label = { Text("دلیل اصلاح") }, enabled = !busy)
                        parsed?.let { JibikFinancialDetail("اختلاف", forecastMoney(it.toBigInteger() - balance.toBigInteger())) }
                        JibikFormHint("اختلاف در یک تراکنش با دسته اصلاح موجودی ثبت می‌شود؛ مانده آغازین و سابقه حذف نمی‌شوند. این اختلاف در گردش نقدی دیده می‌شود.")
                    }
                    "ARCHIVE" -> JibikFormHint("فقط حساب صفر و بدون هدف، پاکت یا برنامه دوره‌ای بایگانی می‌شود. برای تغییر تراکنش‌های قبلی ابتدا حساب را بازیابی کن.")
                    "RESTORE" -> JibikFormHint("حساب دوباره در انتخاب‌گرهای ثبت مالی قابل استفاده خواهد بود.")
                    else -> {
                        TextButton(onClick = { mode = "RENAME" }) { Text("ویرایش نام") }
                        if (!account.archived) TextButton(onClick = { mode = "RECONCILE" }) { Text("اصلاح موجودی با دلیل") }
                        if (account.id != "wallet") TextButton(onClick = { mode = if (account.archived) "RESTORE" else "ARCHIVE" }, enabled = account.archived || balance == 0L) {
                            Text(if (account.archived) "بازگرداندن حساب" else "بایگانی حساب")
                        }
                        JibikSectionTitle("سوابق مدیریت", "آخرین ۲۰ تغییر")
                        history.take(20).forEach { event -> Text(formatJalaliDueDate(event.atMillis) + " · " + event.details); HorizontalDivider() }
                    }
                }
                if (error.isNotBlank()) JibikFormHint(error, isWarning = true)
            }
        },
        confirmButton = {
            val valid = !busy && when(mode) {
                "RENAME" -> title.isNotBlank()
                "RECONCILE" -> parsed != null && parsed != balance && reason.trim().length >= 3
                else -> true
            }
            LiquidGlassButton(if (mode == "DETAIL") "بستن" else "تأیید تغییر", enabled = valid, onClick = {
                when(mode) {
                    "RENAME" -> save { store.renameAccount(account, title) }
                    "RECONCILE" -> save { store.reconcileAccount(account, balance, requireNotNull(parsed), reason, UUID.randomUUID().toString()) }
                    "ARCHIVE" -> save { store.archiveAccount(account, true) }
                    "RESTORE" -> save { store.archiveAccount(account, false) }
                    else -> onDismiss()
                }
            })
        }, dismissButton = if (mode != "DETAIL") {
        { TextButton(onClick = { mode = "DETAIL"; error = "" }, enabled = !busy) { Text("بازگشت") } }
    } else null)
}

@Composable
internal fun WeeklyBudgetPanel(store: LocalLedgerStore, records: List<LedgerRecord>) {
    val today = requireNotNull(parseJalaliDueDate(formatJalaliDueDate(System.currentTimeMillis())))
    val current = reportRange("WEEK", today).start
    var week by rememberSaveable(current) { mutableLongStateOf(current) }
    var plans by remember { mutableStateOf(emptyList<WeeklyBudget>()) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var edit by rememberSaveable { mutableStateOf(false) }
    var amount by rememberSaveable { mutableStateOf("") }
    var carry by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
        try { plans = store.weeklyBudgets(); loaded = true; error = "" }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { loaded = false; error = "بودجه هفتگی قابل خواندن نیست." }
    }
    LaunchedEffect(store) { refresh() }
    val usage = weeklyBudgetUsage(week, plans, records)
    val plan = plans.firstOrNull { it.weekStart == week }
    JibikSurfaceCard {
        JibikSectionTitle("بودجه هفتگی", formatJalaliDueDate(week) + " تا " + formatJalaliDueDate(offsetJalaliDay(week,6)))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { week = offsetJalaliDay(week,-7) }) { Text("هفته قبل") }
            TextButton(onClick = { week = offsetJalaliDay(week,7) }, enabled = week < current) { Text("هفته بعد") }
        }
        if (!loaded) {
            Text(if (error.isBlank()) "در حال بارگذاری…" else error)
            TextButton(onClick = { scope.launch { refresh() } }) { Text("تلاش مجدد") }
        } else {
            JibikFinancialDetail("سقف پایه", if (plan == null) "تعیین نشده" else forecastMoney(usage.base))
            JibikFinancialDetail("انتقال از هفته قبل", forecastMoney(usage.carried))
            JibikFinancialDetail("خروجی نقدی هفته", forecastMoney(usage.spent))
            if (plan != null) JibikFinancialDetail("مانده بودجه", forecastMoney(usage.remaining), emphasis = true)
            LiquidGlassButton("تنظیم سقف هفته", enabled = !busy, onClick = {
                amount = (plan?.limitToman ?: 0).toString(); carry = plan?.carryPrevious == true; edit = true; error = ""
            })
            JibikFormHint("مستقل از سقف ماهانه. انتقال اختیاری فقط مانده مثبت سقف پایه هفته قبل را اضافه می‌کند؛ مبلغ منتقل‌شده دوباره منتقل نمی‌شود.")
        }
    }
    if (edit) {
        val parsed = parseTomanDraft(amount)
        JibikModalDialog(properties = JibikWideDialogProperties, modifier = Modifier.jibikDialogSurface(), onDismissRequest = { if (!busy) edit = false },
            title = { JibikDialogHeading("بودجه هفته", formatJalaliDueDate(week)) },
            text = {
                Column(Modifier.fillMaxWidth().jibikScrollableBody(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    JibikTomanField(amount, { amount = it }, "سقف پایه هفته", allowZero = true, enabled = !busy)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = carry, onCheckedChange = { carry = it }, enabled = !busy)
                        Text("انتقال مانده مثبت سقف پایه هفته قبل")
                    }
                    if (error.isNotBlank()) JibikFormHint(error, isWarning = true)
                }
            }, confirmButton = { LiquidGlassButton("ذخیره بودجه", enabled = !busy && parsed != null, onClick = {
                busy = true
                scope.launch {
                    try { store.saveWeeklyBudget(WeeklyBudget(week, requireNotNull(parsed), carry)); refresh(); edit = false }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { error = "بودجه ذخیره نشد." }
                    finally { busy = false }
                }
            }) }, dismissButton = { TextButton(onClick = { edit = false }, enabled = !busy) { Text("انصراف") } })
    }
}

@Composable
internal fun LoanManagementDialog(store: LocalLedgerStore, id: String, onDismiss: () -> Unit) {
    var details by remember { mutableStateOf<LoanDetails?>(null) }
    var mode by rememberSaveable(id) { mutableStateOf("DETAIL") }
    var draft by rememberSaveable(id) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    suspend fun load() {
        try { details = store.loanDetails(id); error = "" }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "اطلاعات وام بارگذاری نشد." }
    }
    LaunchedEffect(id) { load() }
    val info = details
    val selected = info?.payments?.firstOrNull { it.transactionId == mode }
    val parsed = parseTomanDraft(draft)
    JibikModalDialog(properties = JibikWideDialogProperties, modifier = Modifier.jibikDialogSurface(), onDismissRequest = { if (!busy) onDismiss() },
        bodyKey = mode,
        title = { JibikDialogHeading("اصل و سود وام", "طبقه‌بندی پرداخت‌های واقعی") },
        text = {
            Column(Modifier.fillMaxWidth().jibikScrollableBody(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (info == null) {
                    Text(if (error.isBlank()) "در حال بارگذاری…" else error)
                    TextButton(onClick = { scope.launch { load() } }) { Text("تلاش مجدد") }
                } else {
                    JibikFinancialDetail("کل مبلغ قراردادی", formatToman(info.total))
                    if (mode == "TERMS") {
                        JibikTomanField(draft, { draft = it }, "اصل وام در قرارداد", enabled = !busy)
                        if (parsed != null && parsed in 1..info.total) JibikFinancialDetail("سود و هزینه قراردادی", formatToman(info.total - parsed))
                    } else if (selected != null) {
                        JibikFinancialDetail("مبلغ این پرداخت", formatToman(selected.amountToman))
                        JibikTomanField(draft, { draft = it }, "سهم اصل", allowZero = true, enabled = !busy)
                        if (parsed != null && parsed in 0..selected.amountToman) JibikFinancialDetail("سهم سود و هزینه", formatToman(selected.amountToman - parsed))
                        JibikFormHint("براساس صورتحساب واقعی وام وارد کن؛ این طبقه‌بندی تراکنش دیگری ایجاد نمی‌کند.")
                    } else {
                        info.terms?.let { terms ->
                            JibikFinancialDetail("اصل قراردادی", formatToman(terms.principalToman))
                            JibikFinancialDetail("سود و هزینه قراردادی", formatToman(info.total - terms.principalToman))
                            JibikFinancialDetail("اصل پرداخت‌شده مشخص", forecastMoney(info.paidPrincipal))
                            JibikFinancialDetail("سود پرداخت‌شده مشخص", forecastMoney(info.paidInterest))
                            JibikFinancialDetail("پرداخت تفکیک‌نشده", forecastMoney(info.unclassified))
                            info.remainingPrincipal()?.let { balance ->
                                HorizontalDivider()
                                if (balance.exact) {
                                    JibikFinancialDetail("مانده اصل وام", forecastMoney(balance.minimum), emphasis = true)
                                } else {
                                    JibikSectionTitle("مانده احتمالی اصل وام", "بازه قابل اثبات بر اساس قرارداد")
                                    JibikFinancialDetail("حداقل", forecastMoney(balance.minimum))
                                    JibikFinancialDetail("حداکثر", forecastMoney(balance.maximum), emphasis = true)
                                    JibikFormHint("برای تعیین مانده دقیق، پرداخت‌های تفکیک‌نشده را بر اساس صورتحساب وام مشخص کن.")
                                }
                            }
                        }
                        TextButton(onClick = { draft = info.terms?.principalToman?.toString().orEmpty(); mode = "TERMS" }) { Text("تعیین یا اصلاح اصل قراردادی") }
                        if (info.terms != null) info.payments.forEach { payment ->
                            JibikFinancialDetail(formatJalaliDueDate(payment.paidAtMillis), formatToman(payment.amountToman))
                            val split = info.splits[payment.transactionId]
                            TextButton(onClick = { draft = split?.principalToman?.toString().orEmpty(); mode = payment.transactionId }) {
                                Text(if (split == null) "تفکیک اصل و سود" else "اصل " + formatToman(split.principalToman) + " · سود " + formatToman(split.interestToman))
                            }
                            HorizontalDivider()
                        }
                        JibikFormHint("مانده تعهد و گزارش نقدی همچنان مبلغ کامل قراردادی را نشان می‌دهند. پرداخت‌های تفکیک‌نشده حدس زده نمی‌شوند.")
                    }
                    if (error.isNotBlank()) JibikFormHint(error, isWarning = true)
                }
            }
        }, confirmButton = {
            val valid = !busy && when {
                mode == "DETAIL" -> true
                mode == "TERMS" -> parsed != null && info != null && parsed in 1..info.total
                selected != null -> parsed != null && parsed in 0..selected.amountToman
                else -> false
            }
            LiquidGlassButton(if (mode == "DETAIL") "بستن" else "ذخیره", enabled = valid, onClick = {
                if (mode == "DETAIL") onDismiss()
                else {
                    busy = true; error = ""
                    scope.launch {
                        try {
                            if (mode == "TERMS") store.saveLoanTerms(id, requireNotNull(parsed))
                            else store.classifyLoanPayment(id, mode, requireNotNull(parsed))
                            mode = "DETAIL"; load()
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { error = "مبالغ با اصل و سود قرارداد یا پرداخت‌های قبلی سازگار نیستند." }
                        finally { busy = false }
                    }
                }
            })
        }, dismissButton = if (mode != "DETAIL") {
        { TextButton(enabled = !busy, onClick = { mode = "DETAIL"; error = "" }) { Text("بازگشت") } }
    } else null)
}
