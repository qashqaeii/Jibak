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
    onConfirm: (LedgerRecord) -> Unit,
) {
    val income = candidate.income
    var accountId by remember(candidate.id) {
        mutableStateOf(accounts.firstOrNull()?.id.orEmpty())
    }
    var category by remember(candidate.id) {
        mutableStateOf(if (income) "سایر درآمدها" else "سایر هزینه‌ها")
    }
    var choosingCategory by remember { mutableStateOf(false) }
    var note by remember(candidate.id) { mutableStateOf("") }
    val amount = candidate.amountRial.takeIf { it > 0L && it % 10L == 0L }
        ?.div(10L)
    val looksDuplicate = amount != null && existingRecords.any {
        it.kind == (if (income) LedgerKind.INCOME else LedgerKind.EXPENSE) &&
            it.amountToman == amount &&
            kotlin.math.abs(it.timestampMillis - candidate.receivedAtMillis) < 900_000L
    }
    val valid = !busy && amount != null && amount > 0L &&
        accounts.any { it.id == accountId } &&
        isSelectableCategory(category, income, customCategories)

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
                    .verticalScroll(rememberScrollState()),
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
                    "تاریخ دریافت: " + formatJalaliDueDate(candidate.receivedAtMillis) +
                        (if (candidate.accountLast4.isNotBlank())
                            " • حساب پیامک ⋯" + candidate.accountLast4 else ""),
                    color = JibikStyle.muted, fontSize = 12.sp,
                )
                JibikFormHint(
                    "مبلغ و جهت تراکنش را با پیام بانک مقایسه کن. " +
                        "شماره حساب پیامک الزاماً با حساب‌های جیبیک همسان نیست."
                )
                if (looksDuplicate) {
                    JibikFormHint(
                        "احتمال ثبت تکراری: تراکنشی با همین مبلغ و جهت " +
                            "نزدیک به زمان این پیام از قبل وجود دارد.",
                        isWarning = true,
                    )
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
                JibikFormHint(
                    "با تأیید دقیقاً یک تراکنش با مبلغ پیامک ثبت می‌شود. " +
                        "جیبیک هیچ واریز یا برداشتی را خودکار ثبت نمی‌کند."
                )
            }
        },
        confirmButton = {
            LiquidGlassButton(
                text = if (busy) "در حال ثبت..." else "تأیید و ثبت تراکنش",
                enabled = valid, tone = GlassTone.EMERALD,
                onClick = {
                    val exact = amount ?: return@LiquidGlassButton
                    onConfirm(
                        LedgerRecord(
                            id = UUID.randomUUID().toString(),
                            kind = if (income) LedgerKind.INCOME else LedgerKind.EXPENSE,
                            amountToman = exact,
                            fromAccountId = if (income) null else accountId,
                            toAccountId = if (income) accountId else null,
                            category = category,
                            note = note.trim(),
                            timestampMillis = candidate.receivedAtMillis,
                        )
                    )
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
