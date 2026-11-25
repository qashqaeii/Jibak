package ir.jibik.app

import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibik.core.finance.Account
import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord
import ir.jibik.core.finance.ShoppingItem
import ir.jibik.core.finance.itemizedPurchaseTotal
import java.text.NumberFormat
import java.util.Locale

private fun receiptMoney(amount: Long): String = formatToman(amount)

private fun receiptDate(timestampMillis: Long): String {
    val calendar = Calendar.getInstance(
        TimeZone.getTimeZone("Asia/Tehran"),
        ULocale("fa_IR@calendar=persian"),
    )
    calendar.timeInMillis = timestampMillis
    val raw = String.format(
        Locale.US, "%04d/%02d/%02d  %02d:%02d",
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH),
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE),
    )
    return raw.map { ch ->
        if (ch in '0'..'9') ('۰'.code + ch.code - '0'.code).toChar() else ch
    }.joinToString("")
}

/**
 * A read-only view of the saved, atomic purchase. The receipt is never rebuilt
 * from category summaries or inferred from a single amount.
 */
@Composable
internal fun TransactionDetailDialog(
    record: LedgerRecord,
    accounts: List<Account>,
    lines: List<PurchaseLine>?,
    loadFailed: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    obligationTitle: String? = null,
    revisions: List<LedgerRevision> = emptyList(),
    voided: Boolean = false,
    recurringLinked: Boolean = false,
    financialEdits: List<FinancialEditHistory> = emptyList(),
    onDetach: (() -> Unit)? = null,
    onSchedule: (() -> Unit)? = null,
    onToggleVoid: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onRepeat: (() -> Unit)? = null,
) {
    var confirmVoid by remember(record.id) { mutableStateOf(false) }
    var confirmDetach by remember(record.id) { mutableStateOf(false) }
    val isTransfer = record.kind == LedgerKind.TRANSFER
    val title = when (record.kind) {
        LedgerKind.EXPENSE -> "جزئیات هزینه"
        LedgerKind.INCOME -> "جزئیات درآمد"
        LedgerKind.TRANSFER -> "جزئیات انتقال"
    }
    val sourceName = accounts.firstOrNull { it.id == record.fromAccountId }?.title
        ?: "حساب ثبت‌نشده"
    val targetName = accounts.firstOrNull { it.id == record.toAccountId }?.title
        ?: "حساب ثبت‌نشده"
    val expectedTotal = lines?.takeIf { it.isNotEmpty() }?.let { items ->
        runCatching {
            itemizedPurchaseTotal(
                items.map { ShoppingItem(it.title, it.quantity, it.unitPriceToman) }
            )
        }.getOrNull()
    }
    JibikModalDialog(
        properties = JibikWideDialogProperties,
        modifier = Modifier.jibikDialogSurface(),
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        shape = RoundedCornerShape(26.dp),
        tonalElevation = 0.dp,
        title = {
            JibikDialogHeading(title, "رسید مالی ثبت‌شده")
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                JibikHero(Modifier.fillMaxWidth()) {
                    Text("مبلغ ثبت‌شده", color = Color(0xFFD9F1E7), fontSize = 12.sp)
                    Text(
                        receiptMoney(record.amountToman),
                        color = Color.White,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 30.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        receiptDate(record.timestampMillis),
                        color = Color(0xFFDCF1E8),
                        fontSize = 12.sp,
                    )
                }
                val accountLabel = when (record.kind) {
                    LedgerKind.INCOME -> "دریافت در حساب"
                    LedgerKind.EXPENSE -> "پرداخت از حساب"
                    LedgerKind.TRANSFER -> "از حساب"
                }
                DetailRow(accountLabel, if (record.kind == LedgerKind.INCOME) targetName else sourceName)
                if (isTransfer) {
                    DetailRow("به حساب", targetName)
                    JibikFormHint("این جابه‌جایی درآمد یا هزینه حساب نمی‌شه.")
                }
                if (record.category.isNotBlank()) DetailRow("دسته", record.category)
                if (obligationTitle != null) {
                    DetailRow("مرتبط با تعهد", obligationTitle)
                    JibikFormHint(
                        "این مبلغ فقط یک بار در گردش حساب ثبت شده است. " +
                            "سوابق تسویه در بخش تعهدات هم قابل مشاهده‌اند."
                    )
                }
                if (recurringLinked) JibikFormHint("این تراکنش به یک نوبت مالی متصل است. برای حفظ صحت سررسید، اصلاح و ابطال مستقیم غیرفعال است.")
                DetailRow("شناسه ثبت", record.id.take(12))
                if (record.note.isNotBlank()) DetailRow("توضیحات", record.note)
                if (record.kind == LedgerKind.EXPENSE) {
                    HorizontalDivider(color = JibikStyle.line)
                    JibikSectionTitle("اقلام رسید", "جزئیات ذخیره‌شده در دستگاه")
                    when {
                        loadFailed -> {
                            JibikStatePanel(
                                title = "جزئیات رسید بارگذاری نشد",
                                description = "اطلاعات اصلی تراکنش محفوظ است. دوباره تلاش کن.",
                                actionLabel = "تلاش دوباره",
                                onAction = onRetry,
                            )
                        }
                        lines == null -> {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) { CircularProgressIndicator(color = JibikStyle.forest) }
                        }
                        lines.isEmpty() -> {
                            JibikFormHint("برای این هزینه، اقلام جداگانه ثبت نشده‌اند.")
                        }
                        else -> {
                            lines.forEachIndexed { index, line ->
                                LiquidGlassCard(
                                    tone = GlassTone.CRYSTAL,
                                    padding = androidx.compose.foundation.layout.PaddingValues(13.dp),
                                ) {
                                    Column(
                                        Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Text(
                                                "${index + 1}. ${line.title}",
                                                color = JibikStyle.ink,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                runCatching {
                                                    receiptMoney(
                                                        Math.multiplyExact(
                                                            line.quantity.toLong(), line.unitPriceToman
                                                        )
                                                    )
                                                }.getOrDefault("مبلغ نامعتبر"),
                                                color = JibikStyle.forest,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                maxLines = 3,
                                                lineHeight = 18.sp,
                                            )
                                        }
                                        Text(
                                            "${line.quantity} ${line.unit} × ${receiptMoney(line.unitPriceToman)}",
                                            color = JibikStyle.muted,
                                            fontSize = 11.sp,
                                        )
                                        Text(
                                            line.category,
                                            color = JibikStyle.muted,
                                            fontSize = 11.sp,
                                        )
                                    }
                                }
                            }
                            val matches = expectedTotal == record.amountToman
                            if (matches) {
                                DetailRow("جمع اقلام", receiptMoney(expectedTotal!!))
                            } else {
                                JibikFormHint(
                                    "جمع اقلام با مبلغ اصلی یکسان نیست. " +
                                        "هیچ تغییری در حساب انجام نشده است.",
                                    isWarning = true,
                                )
                            }
                        }
                    }
                }
                if (onDetach != null) {
                    if (confirmDetach) {
                        JibikFormHint("تمام ارتباط‌های این تراکنش با قسط، نوبت دوره‌ای و پیام بانکی باز می‌شوند و باید دوباره بررسی شوند. خود تراکنش و مانده حساب تغییر نمی‌کند.", isWarning = true)
                        TextButton(onClick = onDetach) { Text("تأیید قطع ارتباط‌ها") }
                        TextButton(onClick = { confirmDetach = false }) { Text("انصراف") }
                    } else TextButton(onClick = { confirmDetach = true }) { Text("قطع ارتباط برای اصلاح") }
                }
                financialEdits.forEach { edit ->
                    DetailRow(if (edit.action == "RECEIPT") "اقلام پیش از اصلاح" else "ارتباط‌های بازشده",
                        receiptDate(edit.atMillis) + "\n" + edit.details)
                }
                if (onToggleVoid != null) {
                    if (confirmVoid) {
                        JibikFormHint(if (voided) "این مبلغ دوباره در مانده حساب محاسبه می‌شود."
                            else "مبلغ از محاسبات کنار گذاشته می‌شود. از فهرست باطل‌شده‌ها قابل بازگردانی است.")
                        TextButton(onClick = onToggleVoid) { Text("تأیید تغییر وضعیت") }
                        TextButton(onClick = { confirmVoid = false }) { Text("انصراف") }
                    } else TextButton(onClick = { confirmVoid = true }) {
                        Text(if (voided) "بازگردانی تراکنش" else "ابطال تراکنش")
                    }
                }
                if (onSchedule != null) TextButton(onClick = onSchedule) { Text("تکرار ماهانهٔ این تراکنش") }
                if (onEdit != null) TextButton(onClick = onEdit) { Text("اصلاح تراکنش") }
                revisions.forEach { revision ->
                    val old = revision.previous
                    DetailRow("پیش از اصلاح در " + receiptDate(revision.editedAt),
                        listOf(receiptMoney(old.amountToman), receiptDate(old.timestampMillis),
                            accounts.firstOrNull { it.id == old.fromAccountId }?.let { "از " + it.title }.orEmpty(),
                            accounts.firstOrNull { it.id == old.toAccountId }?.let { "به " + it.title }.orEmpty(),
                            old.category, old.note).filter { it.isNotBlank() }.joinToString(" · "))
                }
            }
        },
        confirmButton = {
            // Receipt has two explicit actions; avoid stretching each button
            // inside the dialog's primary-action composition local.
            androidx.compose.runtime.CompositionLocalProvider(
                LocalJibikModalPrimaryAction provides false,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                if (onRepeat != null) {
                    LiquidGlassButton(
                        text = "ثبت مشابه",
                        onClick = onRepeat,
                        tone = GlassTone.HONEY,
                    )
                }
                LiquidGlassButton(
                    text = "بستن",
                    onClick = onDismiss,
                    tone = GlassTone.CRYSTAL,
                )
                }
            }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    JibikFinancialDetail(
        label = label,
        value = value,
        valueColor = JibikStyle.ink,
    )
}
