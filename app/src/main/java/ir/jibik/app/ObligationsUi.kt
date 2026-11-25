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
import ir.jibik.core.finance.LedgerRecord
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
    store: LocalLedgerStore,
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
    var loanId by rememberSaveable { mutableStateOf<String?>(null) }
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
        JibikPageHeader(
            title = "تعهدات و اقساط",
            subtitle = "بدهی، طلب، وام و قبوض ثبت‌شده",
            onBack = onBack,
        )
        JibikHero(Modifier.fillMaxWidth(), illustration = JiboArtwork.OBLIGATIONS) {
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
            JibikStatePanel(
                title = "هنوز تعهدی ثبت نکردی",
                description = "بدهی، طلب، قبض یا قسطت رو با سررسید شمسی اضافه کن؛ فقط پرداخت واقعی روی موجودی اثر می‌ذاره.",
            )
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
                    JibikStatusText(
                        text = if (progress.settled) "تسویه شد"
                            else if (overdue) "سررسید گذشته" else "در جریان",
                        color = if (overdue && !progress.settled) JibikStyle.coral
                            else JibikStyle.forest,
                    )
                }
                JibikFinancialDetail(
                    label = "مانده تعهد",
                    value = formatToman(remaining),
                    emphasis = true,
                    valueColor = if (overdue) JibikStyle.coral else JibikStyle.ink,
                )
                JibikFinancialDetail(
                    label = "کل تعهد",
                    value = formatToman(item.totalToman),
                )
                JibikFinancialDetail(
                    label = "پرداخت یا دریافت شده",
                    value = formatToman(progress.paidToman),
                    valueColor = JibikStyle.forest,
                )
                JibikProgressTrack(
                    progress = (progress.paidToman.toDouble() /
                        item.totalToman.coerceAtLeast(1L).toDouble()).toFloat(),
                    color = JibikStyle.forest,
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
                if (item.kind == "LOAN") TextButton(onClick = { loanId = item.id }, enabled = !isSaving) { Text("اصل و سود وام") }
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
    loanId?.let { id -> LoanManagementDialog(store, id, onDismiss = { loanId = null }) }
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
    JibikModalDialog(
        properties = JibikWideDialogProperties,
        modifier = Modifier.jibikDialogSurface(),
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = Color.Transparent,
        title = {
            JibikDialogHeading("تعهد مالی جدید", "ثبت بدهی، طلب، وام یا قبض")
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 460.dp)
                    .jibikScrollableBody(rememberScrollState()),
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
                    enabled = !isSaving,
                )
                JibikTomanField(
                    value = amount, onValueChange = { amount = it },
                    label = if (kind == "RECEIVABLE") "مبلغ طلب" else "کل مبلغ تعهد",
                    enabled = !isSaving,
                )
                OutlinedTextField(
                    value = date, onValueChange = { date = it.take(10) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("سررسید شمسی") },
                    placeholder = { Text("مثلاً ۱۴۰۵/۰۹/۲۰") },
                    singleLine = true,
                    isError = date.isNotBlank() && due == null,
                    enabled = !isSaving,
                )
                if (date.isNotBlank() && due == null) {
                    JibikFormHint(
                        "تاریخ معتبر با قالب سال/ماه/روز وارد کن.",
                        isWarning = true,
                    )
                }
                OutlinedTextField(
                    value = count,
                    onValueChange = {
                        if (isAllowedTomanDraft(it)) count = normalizeTomanDraft(it).take(3)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("تعداد اقساط (۱ تا ۱۲۰)") },
                    singleLine = true,
                    enabled = !isSaving,
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(250) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("توضیحات اختیاری") },
                    maxLines = 3,
                    enabled = !isSaving,
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
    records: List<LedgerRecord>,
    onDismiss: () -> Unit,
    onSave: (String, Long, String?) -> Unit,
    isSaving: Boolean = false,
    suggestedAmount: Long? = null,
    error: String = "",
) {
    val outstanding = obligationProgress(obligation, payments).remainingToman
    var accountId by rememberSaveable { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }
    var amount by rememberSaveable { mutableStateOf((suggestedAmount?.coerceIn(1L, outstanding.coerceAtLeast(1L)) ?: outstanding).toString()) }
    var linking by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var limit by rememberSaveable { mutableStateOf(10) }
    val usedIds = payments.mapTo(hashSetOf()) { it.transactionId }
    val candidates = if (linking) records.filter {
        it.id !in usedIds && it.matchesSettlement(obligation, accountId, outstanding)
    }.sortedByDescending { it.timestampMillis } else emptyList()
    val selected = candidates.firstOrNull { it.id == selectedId }
    val parsed = if (linking) selected?.amountToman else parseTomanDraft(amount)
    val valid = !isSaving && parsed != null &&
        parsed in 1..outstanding && accounts.any { it.id == accountId }
    JibikModalDialog(
        properties = JibikWideDialogProperties,
        modifier = Modifier.jibikDialogSurface(),
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = Color.Transparent,
        title = {
            JibikDialogHeading(if (obligation.kind == "RECEIVABLE") "دریافت از طلب" else "پرداخت تعهد", "ثبت گردش پول واقعی")
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 400.dp)
                    .jibikScrollableBody(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Text(obligation.title, color = JibikStyle.ink,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("مانده تعهد: " + formatToman(outstanding),
                    color = JibikStyle.muted, fontSize = 13.sp)
                JibikSegmentedControl(
                    items = listOf(false to "پرداخت جدید", true to "تراکنش قبلی"),
                    selected = linking,
                    enabled = { !isSaving },
                    onSelected = { linking = it },
                )
                JibikAccountChoice(
                    title = if (obligation.kind == "RECEIVABLE")
                        "دریافت به حساب" else "برداشت از حساب",
                    accounts = accounts,
                    selectedId = accountId,
                    onSelect = { accountId = it; selectedId = null; limit = 10 },
                    enabled = !isSaving,
                )
                if (linking) {
                    JibikFormHint("یک تراکنش را به این تعهد وصل کن. موجودی حساب تغییر نمی‌کند.")
                    if (candidates.isEmpty()) JibikFormHint("در این حساب تراکنش قابل اتصال پیدا نشد.")
                    candidates.take(limit).forEach { record ->
                        TextButton(
                            enabled = !isSaving,
                            onClick = { selectedId = record.id },
                        ) {
                            Text(listOf(
                                if (selectedId == record.id) "✓ انتخاب شده" else "",
                                formatToman(record.amountToman), formatJalaliDueDate(record.timestampMillis),
                                record.category, record.note,
                            ).filter { it.isNotBlank() }.joinToString(" · "))
                        }
                    }
                    if (limit < candidates.size) TextButton(onClick = { limit += 10 }) {
                        Text("تراکنش‌های بیشتر")
                    }
                } else {
                    JibikTomanField(
                        value = amount, onValueChange = { amount = it },
                        label = "مبلغ واقعی این پرداخت / دریافت",
                        enabled = !isSaving,
                    )
                    JibikFormHint("اگر پرداخت را قبلاً ثبت کرده‌ای، گزینهٔ تراکنش قبلی را انتخاب کن.")
                }
                if (error.isNotEmpty()) JibikFormHint(error, isWarning = true)
                if (parsed != null && parsed > outstanding) JibikFormHint(
                    "مبلغ نمی‌تواند بیشتر از مانده تعهد باشد.", isWarning = true,
                )
            }
        },
        confirmButton = {
            LiquidGlassButton(
                text = if (isSaving) "در حال ثبت..." else if (linking) "تأیید اتصال" else "تأیید پرداخت واقعی",
                onClick = { parsed?.let { onSave(accountId, it, if (linking) selected?.id else null) } },
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
