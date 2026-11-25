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

@Composable
internal fun FinancialReviewInbox(store: LocalLedgerStore, accounts: List<Account>, records: List<LedgerRecord>, onBank: () -> Unit, onPosted: (LedgerRecord) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var rules by remember { mutableStateOf(emptyList<RecurringRule>()) }
    var decisions by remember { mutableStateOf(emptyList<RecurringDecision>()) }
    var bank by remember { mutableStateOf(emptyList<BankSmsCandidateEntity>()) }
    var reload by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Pair<RecurringRule, Long>?>(null) }
    var amount by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reload) {
        rules = store.recurringRules()
        decisions = store.allRecurringDecisions()
        bank = store.bankSmsSuggestions().filter { it.state == "PENDING" }
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
    if (open) JibikModalDialog(
        modifier = Modifier.jibikDialogSurface(), properties = JibikWideDialogProperties,
        onDismissRequest = { open = false },
        title = { JibikDialogHeading("صندوق بررسی مالی", "نوبت‌ها و پیشنهادهای بانکی") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (pending.isEmpty() && bank.isEmpty()) Text("موردی برای بررسی باقی نمانده است.")
                pending.forEach { (rule, day) ->
                    JibikSurfaceCard {
                        Text(rule.title + " · " + formatJalaliDueDate(day))
                        Text(formatToman(rule.amount))
                        TextButton(onClick = { selected = rule to day; amount = rule.amount.toString() }) { Text("بررسی مبلغ واقعی") }
                        TextButton(enabled = !busy, onClick = {
                            busy = true
                            scope.launch { store.decideRecurring(rule.id, day, null); reload++; busy = false }
                        }) { Text("رد این نوبت") }
                        val match = records.firstOrNull {
                            it.amountToman == rule.amount &&
                                it.kind == (if (rule.income) LedgerKind.INCOME else LedgerKind.EXPENSE) &&
                                (it.fromAccountId ?: it.toAccountId) == rule.accountId &&
                                kotlin.math.abs(it.timestampMillis - day) < 36L * 60 * 60 * 1000
                        }
                        if (match != null) TextButton(enabled = !busy, onClick = {
                            busy = true
                            scope.launch { store.linkRecurring(rule.id, day, match); reload++; busy = false }
                        }) { Text("اتصال تراکنش موجود · " + formatToman(match.amountToman)) }
                    }
                }
                if (bank.isNotEmpty()) {
                    Text("${bank.size} پیشنهاد بانکی منتظر بررسی")
                    TextButton(onClick = { open = false; onBank() }) { Text("بررسی پیشنهادهای بانکی") }
                }
            }
        },
        confirmButton = { LiquidGlassButton("بستن", { open = false }) },
    )
    selected?.let { (rule, day) ->
        val parsed = parseTomanDraft(amount)
        JibikModalDialog(
            modifier = Modifier.jibikDialogSurface(), properties = JibikWideDialogProperties,
            onDismissRequest = { selected = null },
            title = { JibikDialogHeading("تأیید نوبت", rule.title) },
            text = { JibikTomanField(amount, { amount = it }, "مبلغ واقعی") },
            confirmButton = { LiquidGlassButton("ثبت", enabled = !busy && parsed != null && parsed > 0, onClick = {
                busy = true
                scope.launch {
                    store.decideRecurring(rule.id, day, parsed)
                    val posted = store.load().firstOrNull { it.note == rule.title && it.timestampMillis >= day }
                    posted?.let(onPosted)
                    reload++; busy = false; selected = null
                }
            }) },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("انصراف") } },
        )
    }
}
