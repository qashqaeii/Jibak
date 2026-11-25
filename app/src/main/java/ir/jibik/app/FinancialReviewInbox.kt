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
internal fun FinancialReviewInbox(store: LocalLedgerStore, accounts: List<Account>, records: List<LedgerRecord>, onBank: () -> Unit, obligationPaymentIds: Set<String>, onChanged: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    var rules by remember { mutableStateOf(emptyList<RecurringRule>()) }
    var decisions by remember { mutableStateOf(emptyList<RecurringDecision>()) }
    var bank by remember { mutableStateOf(emptyList<BankSmsCandidateEntity>()) }
    var reload by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Pair<RecurringRule, Long>?>(null) }
    var amount by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(reload, records, open) {
        try {
            rules = store.recurringRules()
            decisions = store.allRecurringDecisions()
            bank = store.bankSmsSuggestions().filter { it.state == "PENDING" }
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
                onChanged()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "ثبت انجام نشد؛ وضعیت تراکنش را دوباره بررسی کن." }
            finally { reload++; busy = false }
        }
    }
    val now = System.currentTimeMillis()
    val pending = remember(rules, decisions, now) {
        rules.flatMap { rule ->
            (0..24).mapNotNull { offset ->
                val month = currentJalaliMonth(offsetJalaliMonth(currentJalaliMonth(now).startMillis, -offset))
                val day = rule.dueIn(month) ?: return@mapNotNull null
                if (day <= now && decisions.none { it.ruleId == rule.id && it.day == day }) rule to day else null
            }
        }.distinctBy { it.first.id to it.second }.sortedByDescending { it.second }
    }
    LiquidGlassCard(onClick = { open = true }, tone = if (pending.isEmpty() && bank.isEmpty()) GlassTone.CRYSTAL else GlassTone.HONEY) {
        JibikSectionTitle("صندوق بررسی", "${pending.size + bank.size} مورد منتظر بررسی")
    }
    if (open && selected == null) JibikModalDialog(
        modifier = Modifier.jibikDialogSurface(), properties = JibikWideDialogProperties,
        onDismissRequest = { if (!busy) open = false },
        title = { JibikDialogHeading("صندوق بررسی مالی", "نوبت‌ها و پیشنهادهای بانکی") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (error.isNotEmpty()) JibikFormHint(error, isWarning = true)
                if (!loaded) TextButton(onClick = { error = ""; reload++ }) { Text("تلاش دوباره") }
                if (loaded && pending.isEmpty() && bank.isEmpty()) Text("موردی برای بررسی باقی نمانده است.")
                pending.forEach { (rule, day) ->
                    JibikSurfaceCard {
                        Text(rule.title + " · " + formatJalaliDueDate(day))
                        Text(formatToman(rule.amount))
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
                        if (matches.isNotEmpty()) JibikFormHint("پرداخت مشابه پیدا شد؛ برای جلوگیری از ثبت دوباره، مورد درست را انتخاب کن.")
                        matches.forEach { match ->
                            TextButton(enabled = loaded && !busy, onClick = {
                                submit { store.linkRecurring(rule.id, day, match) }
                            }) { Text(listOf("اتصال", formatToman(match.amountToman), formatJalaliDueDate(match.timestampMillis),
                                match.note, match.id.take(8)).filter { it.isNotBlank() }.joinToString(" · ")) }
                        }
                    }
                }
                if (bank.isNotEmpty()) {
                    Text("${bank.size} پیشنهاد بانکی منتظر بررسی")
                    TextButton(onClick = { open = false; onBank() }) { Text("بررسی پیشنهادهای بانکی") }
                }
            }
        },
        confirmButton = { LiquidGlassButton("بستن", { open = false }, enabled = !busy) },
    )
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
