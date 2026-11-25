package ir.jibik.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import ir.jibik.core.finance.Account
import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@Composable
internal fun FinancialReviewInbox(store: LocalLedgerStore, accounts: List<Account>, records: List<LedgerRecord>, onBank: () -> Unit, obligations: List<FinancialObligationEntity>, payments: List<ObligationPaymentEntity>, customCategories: List<CustomCategoryEntity>, showAmounts: Boolean, onChanged: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    var rules by remember { mutableStateOf(emptyList<RecurringRule>()) }
    var decisions by remember { mutableStateOf(emptyList<RecurringDecision>()) }
    var bank by remember { mutableStateOf(emptyList<BankSmsCandidateEntity>()) }
    var reload by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Pair<RecurringRule, Long>?>(null) }
    var amount by remember { mutableStateOf("") }
    var selectedDue by remember { mutableStateOf<ReviewDue?>(null) }
    var selectedBank by remember { mutableStateOf<BankSmsCandidateEntity?>(null) }
    var filter by remember { mutableStateOf("all") }
    var limit by remember { mutableIntStateOf(12) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(reload, records, open) {
        try {
            rules = store.recurringRules()
            decisions = store.allRecurringDecisions()
            bank = store.pendingBankSmsSuggestions()
            loaded = true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { loaded = false; error = "صندوق بررسی بارگذاری نشد." }
    }
    fun submit(action: suspend () -> Unit) {
        if (busy || !loaded) return
        busy = true
        error = ""
        scope.launch {
            try {
                action()
                selected = null
                selectedDue = null
                selectedBank = null
                onChanged()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "ثبت انجام نشد؛ وضعیت تراکنش را دوباره بررسی کن." }
            finally { reload++; busy = false }
        }
    }
    val now = System.currentTimeMillis()
    val pending = remember(rules, decisions, now) { reviewDueRecurring(rules, decisions, now) }
    val duesResult = remember(obligations, payments, now) { runCatching { reviewDueObligations(obligations, payments, now) } }
    val dues = duesResult.getOrDefault(emptyList())
    val obligationPaymentIds = payments.map { it.transactionId }.toSet()
    val usedIds = obligationPaymentIds + decisions.mapNotNull { it.transactionId }
    val count = pending.size + bank.size + dues.size
    fun displayAmount(value: Long) = if (showAmounts) formatToman(value) else "••••"
    LiquidGlassCard(onClick = { open = true }, tone = if (count == 0) GlassTone.CRYSTAL else GlassTone.HONEY) {
        JibikSectionTitle("صندوق بررسی", if (loaded) "$count مورد منتظر بررسی" else "در حال بررسی")
        Text("تعهد: ${dues.size} · دوره‌ای: ${pending.size} · بانک: ${bank.size}", color = JibikStyle.muted)
    }
    if (open && selected == null && selectedDue == null && selectedBank == null) JibikModalDialog(
        modifier = Modifier.jibikDialogSurface(), properties = JibikWideDialogProperties,
        onDismissRequest = { if (!busy) open = false },
        title = { JibikDialogHeading("صندوق بررسی مالی", "همه کارهای مالی در یک جا") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (error.isNotEmpty()) JibikFormHint(error, isWarning = true)
                if (!loaded) TextButton(onClick = { error = ""; reload++ }) { Text("تلاش دوباره") }
                if (duesResult.isFailure) JibikFormHint("برخی تعهدها ناسازگارند؛ سلامت دفتر مالی را بررسی کن.", isWarning = true)
                JibikSegmentedControl(listOf("all" to "همه", "due" to "تعهد", "repeat" to "دوره‌ای", "bank" to "بانک"),
                    filter, onSelected = { filter = it; limit = 12 })
                val visibleDues = if (filter == "all" || filter == "due") dues else emptyList()
                val visibleRecurring = if (filter == "all" || filter == "repeat") pending else emptyList()
                val visibleBank = if (filter == "all" || filter == "bank") bank else emptyList()
                if (loaded && visibleDues.isEmpty() && visibleRecurring.isEmpty() && visibleBank.isEmpty() && duesResult.isSuccess)
                    Text("موردی برای بررسی باقی نمانده است.")
                visibleDues.take(limit).forEach { due ->
                    JibikSurfaceCard {
                        Text(due.obligation.title + " · " + formatJalaliDueDate(due.day))
                        Text(if (due.obligation.kind == "RECEIVABLE") "دریافت طلب" else "پرداخت تعهد")
                        Text(displayAmount(due.amount), color = JibikStyle.forest)
                        TextButton(enabled = loaded && !busy, onClick = { selectedDue = due; error = "" }) { Text("بررسی و تسویه") }
                    }
                }
                visibleRecurring.take(limit).forEach { (rule, day) ->
                    JibikSurfaceCard {
                        Text(rule.title + " · " + formatJalaliDueDate(day))
                        Text(displayAmount(rule.amount))
                        TextButton(enabled = loaded && !busy, onClick = { selected = rule to day; amount = rule.amount.toString() }) { Text("بررسی مبلغ واقعی") }
                        TextButton(enabled = !busy, onClick = {
                            submit { store.decideRecurring(rule.id, day, null) }
                        }) { Text("رد این نوبت") }
                        val matches = records.filter {
                            it.id !in obligationPaymentIds && decisions.none { d -> d.transactionId == it.id } &&
                                it.amountToman == rule.amount &&
                                it.kind == (if (rule.income) LedgerKind.INCOME else LedgerKind.EXPENSE) &&
                                (it.fromAccountId ?: it.toAccountId) == rule.accountId &&
                                kotlin.math.abs(it.timestampMillis - day) < 36L * 60 * 60 * 1000
                        }
                        if (showAmounts && matches.isNotEmpty()) JibikFormHint("پرداخت مشابه پیدا شد؛ برای جلوگیری از ثبت دوباره، مورد درست را انتخاب کن.")
                        (if (showAmounts) matches else emptyList()).forEach { match ->
                            TextButton(enabled = loaded && !busy, onClick = {
                                submit { store.linkRecurring(rule.id, day, match) }
                            }) { Text(listOf("اتصال", formatToman(match.amountToman), formatJalaliDueDate(match.timestampMillis),
                                match.note, match.id.take(8)).filter { it.isNotBlank() }.joinToString(" · ")) }
                        }
                    }
                }
                visibleBank.take(limit).forEach { item ->
                    JibikSurfaceCard {
                        Text(item.bankTitle + " · " + (if (item.income) "واریز" else "برداشت"))
                        Text(formatJalaliDueDate(item.receivedAtMillis))
                        Text(if (item.amountRial > 0 && item.amountRial % 10L == 0L) displayAmount(item.amountRial / 10L) else "مبلغ نامعتبر")
                        TextButton(enabled = loaded && !busy, onClick = { selectedBank = item; error = "" }) { Text("بررسی پیام") }
                        TextButton(enabled = loaded && !busy, onClick = { submit { check(store.ignoreBankSms(item.id)) } }) { Text("نادیده گرفتن") }
                    }
                }
                if (listOf(visibleDues.size, visibleRecurring.size, visibleBank.size).any { it > limit })
                    TextButton(onClick = { limit += 12 }) { Text("نمایش موارد بیشتر") }
                if (filter == "all" || filter == "bank") TextButton(enabled = !busy, onClick = { open = false; onBank() }) {
                    Text("تنظیمات و تحلیل پیام بانکی")
                }
            }
        },
        confirmButton = { LiquidGlassButton("بستن", { open = false }, enabled = !busy) },
    )
    selectedDue?.let { due ->
        SettleObligationDialog(due.obligation, payments, accounts, records.filter { it.id !in usedIds },
            onDismiss = { if (!busy) { selectedDue = null; error = "" } },
            onSave = { account, value, existingId -> submit {
                if (existingId != null) store.linkObligationPayment(due.obligation.id, account, existingId)
                else store.settleObligation(due.obligation.id, account, value, java.util.UUID.randomUUID().toString())
            } }, isSaving = busy, suggestedAmount = due.amount, error = error)
    }
    selectedBank?.let { item ->
        BankSmsReviewDialog(item, accounts, customCategories, records, busy,
            onDismiss = { if (!busy) { selectedBank = null; error = "" } },
            onConfirm = { record, link -> submit {
                if (link) store.linkBankSms(item.id, record) else store.registerBankSms(item.id, record)
            } }, error = error)
    }
    selected?.let { (rule, day) ->
        val parsed = parseTomanDraft(amount)
        JibikModalDialog(
            modifier = Modifier.jibikDialogSurface(), properties = JibikWideDialogProperties,
            onDismissRequest = { if (!busy) selected = null },
            title = { JibikDialogHeading("تأیید نوبت", rule.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    JibikTomanField(amount, { amount = it }, "مبلغ واقعی")
                    if (error.isNotEmpty()) JibikFormHint(error, isWarning = true)
                    if (!loaded) TextButton(onClick = { error = ""; reload++ }) { Text("تلاش دوباره") }
                }
            },
            confirmButton = { LiquidGlassButton("ثبت", enabled = loaded && !busy && parsed != null && parsed > 0, onClick = {
                submit { store.decideRecurring(rule.id, day, requireNotNull(parsed)) }
            }) },
            dismissButton = { TextButton(enabled = !busy, onClick = { selected = null }) { Text("انصراف") } },
        )
    }
}
