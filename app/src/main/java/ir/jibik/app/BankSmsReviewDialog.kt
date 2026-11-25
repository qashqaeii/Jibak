package ir.jibik.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibik.core.finance.Account
import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord
import java.util.UUID

/** No pre-approved SMS can mutate a ledger; this is the required confirmation. */
@Composable
internal fun BankSmsReviewDialog(
    candidate: BankSmsCandidateEntity,
    accounts: List<Account>,
    customCategories: List<CustomCategoryEntity>,
    existingRecords: List<LedgerRecord>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LedgerRecord, Boolean, BankPaymentTarget?) -> Unit,
    error: String = "",
    linkedIds: Set<String> = emptySet(),
    targetsFor: (LedgerRecord) -> List<BankPaymentOption> = { emptyList() },
    suggestedAccountId: String? = null,
) {
    val income = candidate.income
    var accountId by remember(candidate.id) {
        mutableStateOf(accounts.firstOrNull { it.id == suggestedAccountId && !it.archived }?.id
            ?: "")
    }
    var category by remember(candidate.id) {
        mutableStateOf(if (income) "سایر درآمدها" else "سایر هزینه‌ها")
    }
    var choosingCategory by remember { mutableStateOf(false) }
    var note by remember(candidate.id) { mutableStateOf("") }
    val amount = candidate.amountRial.takeIf { it > 0L && it % 10L == 0L }
        ?.div(10L)
    val matches = existingRecords.filter {
        it.id !in linkedIds && it.kind == (if (income) LedgerKind.INCOME else LedgerKind.EXPENSE) &&
            it.amountToman == amount &&
            (if (income) it.toAccountId else it.fromAccountId) == accountId &&
            kotlin.math.abs(it.timestampMillis - candidate.receivedAtMillis) < 900_000L
    }
    var existingId by remember(candidate.id, accountId) { mutableStateOf<String?>(null) }
    var explicitNew by remember(candidate.id, accountId) { mutableStateOf(false) }
    var target by remember(candidate.id, accountId, existingId) { mutableStateOf<BankPaymentTarget?>(null) }
    val selected = matches.firstOrNull { it.id == existingId }
    val draft = amount?.let { exact ->
        LedgerRecord(
            id = "preview", kind = if (income) LedgerKind.INCOME else LedgerKind.EXPENSE,
            amountToman = exact, fromAccountId = accountId.takeUnless { income },
            toAccountId = accountId.takeIf { income }, category = category,
            note = note.trim(), timestampMillis = candidate.receivedAtMillis,
        )
    }
    val options = (selected ?: draft)?.let(targetsFor).orEmpty()
    val validTarget = target == null || options.any { it.target == target }
    val valid = !busy && amount != null && amount > 0L && validTarget &&
        accounts.any { it.id == accountId } &&
        (selected != null || (existingId == null && (matches.isEmpty() || explicitNew) &&
            isSelectableCategory(category, income, customCategories)))

    JibikModalDialog(
        properties = JibikWideDialogProperties,
        modifier = Modifier.jibikDialogSurface(),
        containerColor = Color.Transparent,
        onDismissRequest = { if (!busy) onDismiss() },
        title = {
            JibikDialogHeading("بررسی پیام بانکی", "تأیید مبلغ و حساب پیش از ثبت")
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 475.dp)
                    .jibikScrollableBody(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    candidate.bankTitle + " • " +
                        (if (income) "واریز" else "برداشت"),
                    color = JibikStyle.forest, fontWeight = FontWeight.Bold,
                )
                Text(
                    amount?.let(::formatToman) ?: "مبلغ ریالی قابل تبدیل دقیق نیست.",
                    color = JibikStyle.ink, fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "تاریخ تراکنش: " + formatJalaliDueDate(candidate.receivedAtMillis) +
                        (if (candidate.accountLast4.isNotBlank())
                            " • حساب پیامک ⋯" + candidate.accountLast4 else ""),
                    color = JibikStyle.muted, fontSize = 12.sp,
                )
                JibikFormHint(
                    "مبلغ و جهت تراکنش را با پیام بانک مقایسه کن. " +
                        "شماره حساب پیامک الزاماً با حساب‌های جیبیک همسان نیست."
                )
                if (error.isNotEmpty()) JibikFormHint(error, isWarning = true)
                if (matches.isNotEmpty()) {
                    JibikFormHint(
                        "احتمال ثبت تکراری: تراکنشی با همین مبلغ و جهت " +
                            "نزدیک به زمان این پیام از قبل وجود دارد.",
                        isWarning = true,
                    )
                    matches.forEach { match ->
                        TextButton(enabled = !busy, onClick = { existingId = match.id; explicitNew = false; target = null }) {
                            Text((if (existingId == match.id) "✓ انتخاب شده" else "انتخاب تراکنش") + " · " +
                                listOf(match.category, match.note, formatToman(match.amountToman),
                                    formatJalaliDueDate(match.timestampMillis), match.id.take(8))
                                    .filter { it.isNotBlank() }.joinToString(" · "))
                        }
                    }
                    TextButton(enabled = !busy, onClick = { existingId = null; explicitNew = true; target = null }) {
                        Text(if (explicitNew) "✓ ثبت جدید انتخاب شد" else "این پیام مربوط به پرداخت دیگری است؛ ثبت جدید")
                    }
                }
                JibikFormHint(
                    "انتقال بین حساب‌های خودت با درآمد یا هزینه فرق دارد. " +
                        "اگر پیام مربوط به انتقال داخلی است، آن را دستی به‌صورت انتقال ثبت کن.",
                )
                JibikAccountChoice(
                    title = if (income) "به کدام حساب واریز شده؟"
                        else "از کدام حساب برداشت شده؟",
                    accounts = accounts, selectedId = accountId,
                    onSelect = { accountId = it },
                    enabled = !busy,
                )
                if (selected == null) {
                LiquidGlassCard(
                    onClick = if (busy) null else ({ choosingCategory = true }),
                    tone = GlassTone.CRYSTAL,
                ) {
                    Text("دسته: " + category, color = JibikStyle.ink, fontSize = 13.sp)
                    Text("انتخاب دسته و زیر‌دسته",
                        color = JibikStyle.forest, fontSize = 12.sp)
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(250) },
                    label = { Text("یادداشت اختیاری") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 3,
                    enabled = !busy,
                )
                }
                JibikSectionTitle("ارتباط با برنامه مالی", "اختیاری · فقط یک مورد")
                TextButton(enabled = !busy, onClick = { target = null }) {
                    Text(if (target == null) "✓ فقط بررسی پیام" else "بدون اتصال به تعهد یا نوبت")
                }
                options.forEach { option ->
                    TextButton(enabled = !busy, onClick = { target = option.target }) {
                        Text((if (target == option.target) "✓ " else "") + option.label)
                    }
                }
                if (options.isEmpty()) JibikFormHint("تعهد یا نوبت قابل اتصال پیدا نشد؛ ممکن است پرداخت قبلاً تعیین تکلیف شده باشد.")
                if (!validTarget) JibikFormHint("وضعیت مورد انتخابی تغییر کرده است؛ دوباره انتخاب کن.", isWarning = true)
                JibikFormHint(if (selected != null)
                    "با تأیید، پیام و مورد انتخابی به تراکنش قبلی متصل می‌شوند؛ مانده حساب تغییر نمی‌کند."
                    else "با تأیید، فقط یک تراکنش ثبت می‌شود و مورد انتخابی در همان عملیات تعیین تکلیف می‌شود.")
            }
        },
        confirmButton = {
            LiquidGlassButton(
                text = if (busy) "در حال ثبت..." else if (selected != null) "تأیید اتصال" else "تأیید و ثبت تراکنش",
                enabled = valid, tone = GlassTone.EMERALD,
                onClick = {
                    val record = selected ?: draft?.copy(id = UUID.randomUUID().toString()) ?: return@LiquidGlassButton
                    onConfirm(record, selected != null, target)
                },
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("فعلاً نه", color = JibikStyle.muted)
            }
        },
    )
    if (choosingCategory && !busy) {
        CategoryPickerDialog(
            income = income, selected = category, custom = customCategories,
            onSelected = { category = it; choosingCategory = false },
            onDismiss = { choosingCategory = false },
        )
    }
}
