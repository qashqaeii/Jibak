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
import androidx.compose.runtime.Composable
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

private fun receiptMoney(amount: Long): String =
    NumberFormat.getNumberInstance(Locale("fa", "IR")).format(amount) + " تومان"

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
) {
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
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JibikStyle.paper,
        shape = RoundedCornerShape(26.dp),
        tonalElevation = 0.dp,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JibikBrandLogo(Modifier.padding(1.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, color = JibikStyle.ink, fontWeight = FontWeight.ExtraBold,
                        fontSize = 19.sp)
                    Text("رسید مالی ثبت‌شده", color = JibikStyle.muted, fontSize = 11.sp)
                }
            }
        },
        text = {
            Column(
                Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                JibikHero(Modifier.fillMaxWidth()) {
                    Text("مبلغ ثبت‌شده", color = Color(0xFFD9F1E7), fontSize = 12.sp)
                    Text(
                        receiptMoney(record.amountToman),
                        color = Color.White,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
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
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = JibikStyle.cream,
                                    ),
                                    shape = RoundedCornerShape(16.dp),
                                ) {
                                    Column(
                                        Modifier.fillMaxWidth().padding(13.dp),
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
                                                receiptMoney(
                                                    Math.multiplyExact(
                                                        line.quantity.toLong(), line.unitPriceToman
                                                    )
                                                ),
                                                color = JibikStyle.forest,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                            )
                                        }
                                        Text(
                                            "${line.quantity} × ${receiptMoney(line.unitPriceToman)}",
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
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(15.dp),
            ) { Text("بستن") }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = JibikStyle.muted, fontSize = 12.sp)
        Text(
            value,
            modifier = Modifier.weight(1f),
            color = JibikStyle.ink,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
        )
    }
}
