package ir.jibik.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibik.core.finance.Account
import ir.jibik.core.finance.ExpenseCategoryCatalog
import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord
import ir.jibik.core.finance.ShoppingItem
import ir.jibik.core.finance.itemizedPurchaseTotal
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

private val WalletGreen = Color(0xFF07574C)
private val WarmWhite = Color.White
private val Muted = Color(0xFF677A78)
private val Ink = Color(0xFF102D31)

private data class DraftPurchaseItem(
    val name: String = "",
    val quantity: String = "1",
    val unitPrice: String = "",
    val category: String = "خوراک / خرید مواد غذایی / سایر",
)

private fun DraftPurchaseItem.toPurchaseLineOrNull(): PurchaseLine? {
    val quantityNumber = parseAccountAmount(quantity)?.toInt()
    val price = parseAccountAmount(unitPrice)
    return if (name.trim().isNotEmpty() && quantityNumber != null &&
        quantityNumber in 1..10000 && price != null && price >= 0L && category.isNotBlank()
    ) {
        PurchaseLine(name.trim(), category.trim(), quantityNumber, price)
    } else null
}

/** A receipt is a SINGLE deduction with structured lines and category metadata. */
@Composable
internal fun ItemizedPurchaseDialog(
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onSave: (LedgerRecord, List<PurchaseLine>) -> Unit,
) {
    var accountId by rememberSaveable { mutableStateOf(accounts.first().id) }
    var note by rememberSaveable { mutableStateOf("") }
    val drafts = remember { mutableStateListOf(DraftPurchaseItem()) }
    val parsed = drafts.map { it.toPurchaseLineOrNull() }
    val lines = parsed.filterNotNull()
    val total = if (lines.size == drafts.size) {
        runCatching {
            itemizedPurchaseTotal(lines.map { ShoppingItem(it.title, it.quantity, it.unitPriceToman) })
        }.getOrNull()
    } else null
    val valid = total != null && total > 0 && lines.isNotEmpty() &&
        accounts.any { it.id == accountId }
    val groceries = ExpenseCategoryCatalog.leaves()
        .filter { it.ids.size == 3 && it.ids.first() == "food" }
        .take(10)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("خرید چندقلمی", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 490.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("اقلام رو جدا ثبت کن؛ از موجودی فقط جمع رسید کم می‌شه.",
                    fontSize = 12.sp, color = Muted)
                Text("پرداخت از حساب", fontSize = 12.sp, color = Muted)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    accounts.forEach { account ->
                        FilterChip(
                            selected = accountId == account.id,
                            onClick = { accountId = account.id },
                            label = { Text(account.title, maxLines = 1, fontSize = 12.sp) },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
                drafts.forEachIndexed { index, item ->
                    HorizontalDivider(color = Color(0xFFECEAE4))
                    Text("قلم ${index + 1}", fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, color = WalletGreen)
                    OutlinedTextField(
                        value = item.name,
                        onValueChange = { drafts[index] = item.copy(name = it.take(80)) },
                        label = { Text("نام کالا") },
                        placeholder = { Text("مثلاً برنج ایرانی") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(13.dp),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = item.quantity,
                            onValueChange = { drafts[index] = item.copy(quantity = it) },
                            label = { Text("تعداد") },
                            singleLine = true,
                            modifier = Modifier.weight(0.34f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(13.dp),
                        )
                        OutlinedTextField(
                            value = item.unitPrice,
                            onValueChange = { drafts[index] = item.copy(unitPrice = it) },
                            label = { Text("قیمت واحد (تومان)") },
                            singleLine = true,
                            modifier = Modifier.weight(0.66f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(13.dp),
                        )
                    }
                    Text("دسته کالا", color = Muted, fontSize = 12.sp)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        groceries.forEach { cat ->
                            FilterChip(
                                selected = item.category == cat.displayName,
                                onClick = { drafts[index] = item.copy(category = cat.displayName) },
                                label = { Text(cat.labels.last(), fontSize = 11.sp) },
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                    }
                    if (drafts.size > 1) {
                        TextButton(onClick = { drafts.removeAt(index) }) {
                            Text("حذف این قلم", color = Color(0xFFE95C50), fontSize = 12.sp)
                        }
                    }
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = drafts.size < 20,
                    onClick = { drafts.add(DraftPurchaseItem()) },
                    shape = RoundedCornerShape(14.dp),
                ) { Text("＋ افزودن قلم بعدی") }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("نام فروشگاه یا توضیحات (اختیاری)") },
                    singleLine = true, shape = RoundedCornerShape(13.dp),
                )
                Text(
                    if (total != null) "جمع رسید: " +
                        NumberFormat.getNumberInstance(Locale("fa", "IR")).format(total) + " تومان"
                    else "برای محاسبه جمع رسید، نام و قیمت هر قلم رو وارد کن.",
                    fontWeight = FontWeight.Bold,
                    color = if (total != null) WalletGreen else Muted, fontSize = 14.sp,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    val sum = total ?: return@Button
                    onSave(
                        LedgerRecord(
                            id = UUID.randomUUID().toString(),
                            kind = LedgerKind.EXPENSE,
                            amountToman = sum,
                            fromAccountId = accountId,
                            category = "خرید منزل",
                            note = note.trim(),
                            timestampMillis = System.currentTimeMillis(),
                        ),
                        lines,
                    )
                },
                shape = RoundedCornerShape(14.dp),
            ) { Text("ثبت خرید") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
        containerColor = WarmWhite,
        shape = RoundedCornerShape(26.dp),
        tonalElevation = 0.dp,
    )
}
