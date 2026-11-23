package ir.jibik.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibik.core.finance.Account
import java.util.UUID

private fun obligationKindName(kind: String): String = when (kind) {
    "DEBT" -> "بدهی"
    "LOAN" -> "وام و اقساط"
    "BILL" -> "قبض و پرداخت"
    "RECEIVABLE" -> "طلب"
    else -> "تعهد"
}

/** Dedicated offline liabilities workspace. Creating a promise never posts money. */
@Composable
internal fun ObligationsScreen(
    obligations: List<FinancialObligationEntity>,
    payments: List<ObligationPaymentEntity>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onPay: (String) -> Unit,
    onPaymentClick: (String) -> Unit,
    remindersEnabled: Boolean,
    onEnableReminders: () -> Unit,
    onDisableReminders: () -> Unit,
    modifier: Modifier = Modifier,
    isSaving: Boolean = false,
) {
    val now = System.currentTimeMillis()
    var expandedPaymentId by rememberSaveable { mutableStateOf("") }
    var showingHistoryId by rememberSaveable { mutableStateOf("") }
    val active = obligations.filter { !obligationProgress(it, payments).settled }
    val due = active.count { item ->
        val installment = nextUnpaidInstallment(item, payments)
        installment != null &&
            jalaliDueDayEndExclusive(
                jalaliInstallmentDueDate(item.dueAtMillis, installment.index)
            ) <= now
    }
    Column(
        modifier.fillMaxSize().liquidBackdrop()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        JibikSectionTitle("تعهدات و اقساط", "بدهی، طلب، وام و قبوض ثبت‌شده",
            trailing = { TextButton(onClick = onBack) {
                Text("بازگشت", color = JibikStyle.forest)
            } })
        JibikHero(Modifier.fillMaxWidth()) {
            Text("تعهدات تسویه‌نشده", fontSize = 13.sp,
                color = Color(0xFFD8F2EA))
            Text(active.size.toString() + " مورد",
                color = Color.White, fontSize = 25.sp,
                fontWeight = FontWeight.ExtraBold)
            Text(if (due > 0) "$due مورد سررسید گذشته"
                else "همه تعهدات ثبت‌شده قابل پیگیری هستند",
                color = Color(0xFFDBF4EC), fontSize = 12.sp)
        }
        LiquidGlassCard(tone = GlassTone.CRYSTAL) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("یادآوری آفلاین جیبو", color = JibikStyle.ink,
                        fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        if (remindersEnabled)
                            "فعال • بررسی روزانه سررسیدها"
                        else "خاموش • فقط با اجازه تو فعال می‌شود",
                        color = if (remindersEnabled) JibikStyle.forest
                            else JibikStyle.muted,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    if (remindersEnabled) "●" else "○",
                    color = if (remindersEnabled) JibikStyle.forest
                        else JibikStyle.muted, fontSize = 21.sp,
                )
            }
            JibikFormHint(
                "سررسید امروز، سه روز آینده و موارد عقب‌افتاده را حدود " +
                    "۹ صبح تهران یادآوری می‌کنیم. ممکن است به‌دلیل مصرف باتری گوشی " +
                    "با تأخیر نمایش داده شود. مبلغ و نام افراد در اعلان نیست."
            )
            LiquidGlassButton(
                text = if (remindersEnabled) "خاموش‌کردن یادآوری‌ها"
                    else "فعال‌کردن یادآوری‌ها",
                onClick = if (remindersEnabled) onDisableReminders
                    else onEnableReminders,
                tone = if (remindersEnabled) GlassTone.CRYSTAL
                    else GlassTone.EMERALD,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LiquidGlassButton(
            text = "＋ ثبت بدهی، طلب، قبض یا وام",
            onClick = onAdd, enabled = !isSaving,
            tone = GlassTone.HONEY,
            modifier = Modifier.fillMaxWidth(),
        )
        JibikFormHint(
            "ایجاد تعهد موجودی حساب را تغییر نمی‌دهد. فقط هنگام ثبت تسویه، " +
                "یک تراکنش واقعی به دفتر مالی اضافه می‌شود."
        )
        if (obligations.isEmpty()) {
            JibikSurfaceCard {
                Text("هنوز تعهدی ثبت نشده. اولین قبض، قسط، بدهی یا طلب خودت را ثبت کن.",
                    fontSize = 13.sp, color = JibikStyle.muted, lineHeight = 21.sp)
            }
        }
        obligations.sortedWith(
            compareBy<FinancialObligationEntity> {
                obligationProgress(it, payments).settled
            }.thenBy { it.dueAtMillis }
        ).forEach { item ->
            val progress = obligationProgress(item, payments)
            val upcoming = nextUnpaidInstallment(item, payments)
            val nextDate = upcoming?.let {
                jalaliInstallmentDueDate(item.dueAtMillis, it.index)
            }
            val overdue = nextDate != null &&
                jalaliDueDayEndExclusive(nextDate) <= now
            val remaining = progress.remainingToman
            LiquidGlassCard(tone = GlassTone.CRYSTAL) {
                Row(Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, fontWeight = FontWeight.ExtraBold,
                            color = JibikStyle.ink, fontSize = 16.sp)
                        Text(obligationKindName(item.kind),
                            color = JibikStyle.muted, fontSize = 12.sp)
                    }
                    Text(
                        if (progress.settled) "تسویه شد"
                        else if (overdue) "سررسید گذشته"
                        else "در جریان",
                        color = if (overdue) JibikStyle.coral
                            else JibikStyle.forest,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    )
                }
                Text("باقی‌مانده: " + formatToman(remaining),
                    color = JibikStyle.ink, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("کل: " + formatToman(item.totalToman),
                        color = JibikStyle.muted, fontSize = 12.sp)
                    Text("پرداخت/دریافت: " + formatToman(progress.paidToman),
                        color = JibikStyle.forest, fontSize = 12.sp)
                }
                LinearProgressIndicator(
                    progress = {
                        (progress.paidToman.toDouble() / item.totalToman.toDouble())
                            .toFloat().coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = JibikStyle.forest,
                    trackColor = Color(0xFFE6F1E9),
                )
                Text(
                    if (nextDate == null) "تمام اقساط تسویه شده‌اند"
                    else "سررسید بعدی: " + formatJalaliDueDate(nextDate) +
                        if (upcoming != null && upcoming.count > 1)
                            " · قسط " + (upcoming.index + 1) + " از " + upcoming.count
                        else "",
                    fontSize = 12.sp,
                    color = if (overdue) JibikStyle.coral else JibikStyle.muted,
                )
                if (upcoming != null && upcoming.count > 1) {
                    Text(
                        "مانده قسط جاری: " +
                            formatToman(upcoming.amountToman - upcoming.paidTowardToman),
                        color = JibikStyle.muted, fontSize = 12.sp,
                    )
                }
                if (item.note.isNotBlank()) {
                    Text(item.note, fontSize = 12.sp,
                        color = JibikStyle.muted, maxLines = 3)
                }
                val itemPayments = payments.filter { it.obligationId == item.id }
                Text(itemPayments.size.toString() + " پرداخت/دریافت ثبت‌شده",
                    color = JibikStyle.muted, fontSize = 11.sp)
                if (itemPayments.isNotEmpty()) {
                    LiquidGlassButton(
                        text = if (showingHistoryId == item.id) "بستن سوابق پرداخت"
                            else "مشاهده سوابق پرداخت",
                        tone = GlassTone.CRYSTAL,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showingHistoryId = if (showingHistoryId == item.id) "" else item.id
                            expandedPaymentId = ""
                        },
                    )
                    if (showingHistoryId == item.id) {
                    Text("سابقه پرداخت‌ها", color = JibikStyle.ink,
                        fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    (if (expandedPaymentId == item.id) itemPayments
                        else itemPayments.take(6)).forEach { payment ->
                        LiquidGlassCard(
                            tone = GlassTone.CRYSTAL,
                            onClick = { onPaymentClick(payment.transactionId) },
                            padding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 12.dp, vertical = 9.dp,
                            ),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(formatJalaliDueDate(payment.paidAtMillis),
                                        color = JibikStyle.muted, fontSize = 12.sp)
                                    Text("مشاهده رسید", color = JibikStyle.forest,
                                        fontSize = 11.sp)
                                }
                                Text(formatToman(payment.amountToman),
                                    color = JibikStyle.ink,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (itemPayments.size > 6) {
                        TextButton(onClick = {
                            expandedPaymentId =
                                if (expandedPaymentId == item.id) "" else item.id
                        }) {
                            Text(
                                if (expandedPaymentId == item.id)
                                    "نمایش شش پرداخت اخیر"
                                else "مشاهده تمام " + itemPayments.size + " پرداخت",
                                color = JibikStyle.forest, fontSize = 12.sp,
                            )
                        }
                    }
                    }
                }
                if (!progress.settled) {
                    LiquidGlassButton(
                        text = if (item.kind == "RECEIVABLE") "ثبت دریافت طلب"
                            else "ثبت پرداخت واقعی",
                        onClick = { onPay(item.id) },
                        modifier = Modifier.fillMaxWidth(),
                        tone = GlassTone.EMERALD,
                        enabled = !isSaving,
                    )
                }
            }
        }
    }
}

@Composable
internal fun NewObligationDialog(
    onDismiss: () -> Unit,
    onSave: (FinancialObligationEntity) -> Unit,
    isSaving: Boolean = false,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("DEBT") }
    var amount by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(
        formatJalaliDueDate(System.currentTimeMillis())
    ) }
    var count by rememberSaveable { mutableStateOf("1") }
    var note by rememberSaveable { mutableStateOf("") }
    val total = parseTomanDraft(amount)
    val due = parseJalaliDueDate(date)
    val installments = parseTomanDraft(count)?.takeIf { it in 1..120L }?.toInt()
    val valid = !isSaving && title.trim().length in 2..70 &&
        total != null && total > 0L && due != null && installments != null
    AlertDialog(
        modifier = Modifier.liquidGlass(tone = GlassTone.FROSTED,
            radius = 26.dp, elevation = 15.dp),
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = Color.Transparent,
        title = {
            Text("تعهد مالی جدید", color = JibikStyle.ink,
                fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("نوع تعهد", color = JibikStyle.muted, fontSize = 12.sp)
                listOf(
                    listOf("DEBT", "LOAN"),
                    listOf("BILL", "RECEIVABLE"),
                ).forEach { options ->
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        options.forEach { option ->
                            JibikGlassChip(
                                text = obligationKindName(option),
                                selected = kind == option,
                                onClick = { kind = option },
                                enabled = !isSaving,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(70) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("عنوان تعهد") },
                    placeholder = { Text("مثلاً قسط وام یا اجاره") },
                )
                JibikTomanField(
                    value = amount, onValueChange = { amount = it },
                    label = if (kind == "RECEIVABLE") "مبلغ طلب" else "کل مبلغ تعهد",
                )
                OutlinedTextField(
                    value = date, onValueChange = { date = it.take(10) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("سررسید شمسی (مثلاً ۱۴۰۵/۰۹/۲۰)") },
                    singleLine = true,
                    isError = date.isNotBlank() && due == null,
                )
                OutlinedTextField(
                    value = count,
                    onValueChange = {
                        if (isAllowedTomanDraft(it)) count = normalizeTomanDraft(it).take(3)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("تعداد اقساط (۱ تا ۱۲۰)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(250) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("توضیحات اختیاری") },
                    maxLines = 3,
                )
                JibikFormHint(
                    "تاریخ واردشده، سررسید اولین قسط است؛ اقساط بعدی هر ماه شمسی " +
                        "بر اساس همین روز محاسبه می‌شوند. هر پرداخت واقعی فقط با تأیید ثبت می‌شود."
                )
            }
        },
        confirmButton = {
            LiquidGlassButton(
                text = if (isSaving) "در حال ذخیره..." else "ثبت تعهد",
                enabled = valid,
                onClick = {
                    val value = total
                    val at = due
                    val parts = installments
                    if (value != null && at != null && parts != null) {
                        onSave(FinancialObligationEntity(
                            id = UUID.randomUUID().toString(),
                            title = title.trim(), kind = kind, totalToman = value,
                            dueAtMillis = at, installmentCount = parts,
                            note = note.trim(), createdAtMillis = System.currentTimeMillis(),
                        ))
                    }
                },
                tone = GlassTone.EMERALD,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("انصراف", color = JibikStyle.muted)
            }
        },
    )
}

@Composable
internal fun SettleObligationDialog(
    obligation: FinancialObligationEntity,
    payments: List<ObligationPaymentEntity>,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit,
    isSaving: Boolean = false,
) {
    val outstanding = obligationProgress(obligation, payments).remainingToman
    var accountId by rememberSaveable { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf(outstanding.toString()) }
    val parsed = parseTomanDraft(amount)
    val valid = !isSaving && parsed != null &&
        parsed in 1..outstanding && accounts.any { it.id == accountId }
    AlertDialog(
        modifier = Modifier.liquidGlass(tone = GlassTone.FROSTED,
            radius = 26.dp, elevation = 15.dp),
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = Color.Transparent,
        title = {
            Text(
                if (obligation.kind == "RECEIVABLE")
                    "دریافت از طلب" else "پرداخت تعهد",
                color = JibikStyle.ink, fontWeight = FontWeight.ExtraBold,
                fontSize = 19.sp,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Text(obligation.title, color = JibikStyle.ink,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("مانده تعهد: " + formatToman(outstanding),
                    color = JibikStyle.muted, fontSize = 13.sp)
                JibikAccountChoice(
                    title = if (obligation.kind == "RECEIVABLE")
                        "دریافت به حساب" else "برداشت از حساب",
                    accounts = accounts,
                    selectedId = accountId,
                    onSelect = { accountId = it },
                )
                JibikTomanField(
                    value = amount, onValueChange = { amount = it },
                    label = "مبلغ واقعی این پرداخت / دریافت",
                )
                JibikFormHint(
                    "با تأیید، دقیقاً یک تراکنش " +
                        (if (obligation.kind == "RECEIVABLE") "درآمد" else "هزینه") +
                        " ثبت می‌شود و مانده این تعهد کاهش پیدا می‌کند."
                )
                JibikFormHint(
                    "اگر این پرداخت را قبلاً در تراکنش‌های عادی ثبت کرده‌ای، " +
                        "دوباره از این بخش ثبتش نکن؛ مبلغ دوبار از حساب کم می‌شود.",
                    isWarning = true,
                )
                if (parsed != null && parsed > outstanding) JibikFormHint(
                    "مبلغ نمی‌تواند بیشتر از مانده تعهد باشد.", isWarning = true,
                )
            }
        },
        confirmButton = {
            LiquidGlassButton(
                text = if (isSaving) "در حال ثبت..." else "تأیید پرداخت واقعی",
                onClick = { parsed?.let { onSave(accountId, it) } },
                enabled = valid,
                tone = GlassTone.EMERALD,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("انصراف", color = JibikStyle.muted)
            }
        },
    )
}
