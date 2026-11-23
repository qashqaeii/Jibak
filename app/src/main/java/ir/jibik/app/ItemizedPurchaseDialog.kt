package ir.jibik.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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

private fun PurchaseDraftItem.asPurchaseLine(
    customCategories: List<CustomCategoryEntity>,
): PurchaseLine? {
    val count = parseTomanDraft(quantity)?.takeIf { it in 1L..10_000L }?.toInt()
    val price = parseTomanDraft(unitPrice)
    return if (name.isNotBlank() && name.length <= 80 &&
        count != null && price != null && price >= 0L &&
        isSelectableCategory(category, false, customCategories)
    ) PurchaseLine(name.trim(), category.trim(), count, price) else null
}

private fun money(value: Long): String = formatToman(value)

/** One receipt means exactly one expense; a validation error cannot deduct any money. */
@Composable
internal fun ItemizedPurchaseDialog(
    accounts: List<Account>,
    customCategories: List<CustomCategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (LedgerRecord, List<PurchaseLine>) -> Unit,
    isSaving: Boolean = false,
) {
    var accountId by rememberSaveable { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }
    var note by rememberSaveable { mutableStateOf("") }
    val drafts = rememberSaveable(saver = purchaseDraftStateSaver) {
        mutableStateListOf(PurchaseDraftItem())
    }
    val lines = drafts.mapNotNull { it.asPurchaseLine(customCategories) }
    val total = if (lines.size == drafts.size) runCatching {
        itemizedPurchaseTotal(lines.map {
            ShoppingItem(it.title, it.quantity, it.unitPriceToman)
        })
    }.getOrNull() else null
    val valid = !isSaving && total != null && total > 0L &&
        drafts.size in 1..20 && accounts.any { it.id == accountId }
    var selectingCategoryIndex by rememberSaveable { mutableStateOf(-1) }

    AlertDialog(
        modifier = Modifier.liquidGlass(tone = GlassTone.FROSTED, radius = 28.dp, elevation = 15.dp),
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(26.dp),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                JibikBrandLogo(Modifier.size(40.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("خرید چندقلمی", color = JibikStyle.ink,
                        fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text("هر رسید، فقط یک پرداخت", color = JibikStyle.muted, fontSize = 11.sp)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 425.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                JibikAccountChoice(
                    title = "پرداخت از حساب",
                    accounts = accounts,
                    selectedId = accountId,
                    onSelect = { accountId = it },
                )
                drafts.forEachIndexed { index, draft ->
                    val line = draft.asPurchaseLine(customCategories)
                    val lineTotal = line?.let {
                        runCatching { Math.multiplyExact(it.quantity.toLong(), it.unitPriceToman) }
                            .getOrNull()
                    }
                    LiquidGlassCard(
                        tone = GlassTone.CRYSTAL,
                        padding = androidx.compose.foundation.layout.PaddingValues(13.dp),
                    ) {
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "قلم ${index + 1}", color = JibikStyle.forest,
                                    fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                                )
                                if (drafts.size > 1) {
                                    TextButton(
                                        enabled = !isSaving,
                                        onClick = { drafts.removeAt(index) },
                                    ) {
                                        Text("حذف", color = JibikStyle.coral, fontSize = 12.sp)
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = draft.name,
                                onValueChange = {
                                    drafts[index] = draft.copy(name = it.take(80))
                                },
                                label = { Text("نام کالا") },
                                placeholder = { Text("مثلاً برنج یا میوه") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                            )
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = draft.quantity,
                                    onValueChange = {
                                        drafts[index] = draft.copy(
                                            quantity = normalizeTomanDraft(it).take(5),
                                        )
                                    },
                                    modifier = Modifier.weight(0.34f),
                                    singleLine = true,
                                    label = { Text("تعداد", maxLines = 1) },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                )
                                JibikTomanField(
                                    value = draft.unitPrice,
                                    onValueChange = {
                                        drafts[index] = draft.copy(unitPrice = it)
                                    },
                                    label = "قیمت واحد",
                                    modifier = Modifier.weight(0.66f),
                                    allowZero = true,
                                )
                            }
                            Text(
                                "دسته‌بندی این کالا",
                                color = JibikStyle.muted, fontSize = 12.sp,
                            )
                            LiquidGlassCard(
                                onClick = {
                                    if (!isSaving) selectingCategoryIndex = index
                                },
                                tone = GlassTone.CRYSTAL,
                                padding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 12.dp, vertical = 11.dp,
                                ),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        draft.category.ifBlank {
                                            "انتخاب دسته و زیر‌دسته کالا"
                                        },
                                        color = if (draft.category.isBlank())
                                            JibikStyle.muted else JibikStyle.ink,
                                        modifier = Modifier.weight(1f),
                                        fontSize = 12.sp, maxLines = 3,
                                    )
                                    Text("‹ انتخاب", color = JibikStyle.forest, fontSize = 11.sp)
                                }
                            }
                            if (!isSelectableCategory(draft.category, false, customCategories)) {
                                JibikFormHint("دسته معتبر برای این قلم انتخاب کن.", isWarning = true)
                            } else if (lineTotal != null) {
                                Text(
                                    "جمع قلم: " + money(lineTotal),
                                    fontSize = 12.sp,
                                    color = JibikStyle.forest,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = { drafts.add(PurchaseDraftItem()) },
                    enabled = !isSaving && drafts.size < 20,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp),
                ) {
                    Text("＋ افزودن قلم دیگر")
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(200) },
                    label = { Text("نام فروشگاه یا توضیحات") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                LiquidGlassCard(
                    tone = GlassTone.HONEY,
                    padding = androidx.compose.foundation.layout.PaddingValues(14.dp),
                ) {
                    Column(Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("جمع نهایی رسید", color = JibikStyle.muted, fontSize = 12.sp)
                        Text(
                            if (total != null) money(total) else "در انتظار تکمیل اقلام",
                            color = JibikStyle.forest, fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        JibikFormHint(
                            "${lines.size} قلم کامل از ${drafts.size} · مبلغ فقط یک‌بار از حساب کم می‌شه.",
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                shape = RoundedCornerShape(15.dp),
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
            ) { Text(if (isSaving) "در حال ثبت..." else "ثبت خرید") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("انصراف") }
        },
    )
    if (selectingCategoryIndex in drafts.indices && !isSaving) {
        val itemIndex = selectingCategoryIndex
        CategoryPickerDialog(
            income = false,
            selected = drafts[itemIndex].category,
            custom = customCategories,
            onSelected = { chosen ->
                drafts[itemIndex] = drafts[itemIndex].copy(category = chosen)
                selectingCategoryIndex = -1
            },
            onDismiss = { selectingCategoryIndex = -1 },
        )
    }
}
