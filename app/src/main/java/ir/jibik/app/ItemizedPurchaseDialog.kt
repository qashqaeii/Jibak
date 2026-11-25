package ir.jibik.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalDensity
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
        unit in PURCHASE_UNITS &&
        isSelectableCategory(category, false, customCategories)
    ) PurchaseLine(name.trim(), category.trim(), count, price, unit) else null
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
    val completeCount = lines.size
    val incompleteCount = drafts.size - completeCount
    var selectingCategoryIndex by rememberSaveable { mutableStateOf(-1) }

    JibikModalDialog(
        properties = JibikWideDialogProperties,
        modifier = Modifier.jibikDialogSurface(),
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(26.dp),
        title = {
            JibikDialogHeading("خرید چندقلمی", "یک پرداخت، با جزئیات هر کالا")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 425.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                JibikFormHint("جمع نهایی از تعداد × قیمت واحد محاسبه می‌شود؛ ثبت ناقص موجودی را تغییر نمی‌دهد.")
                JibikAccountChoice(
                    title = "پرداخت از حساب",
                    accounts = accounts,
                    selectedId = accountId,
                    onSelect = { accountId = it },
                )
                JibikSectionTitle("اقلام خرید", "$completeCount قلم کامل از ${drafts.size}")
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
                            // On small phones the quantity/price fields must not
                            // squeeze into unreadable side-by-side input widths.
                            val quantityField: @Composable (Modifier) -> Unit = { fieldModifier ->
                                OutlinedTextField(
                                    value = draft.quantity,
                                    onValueChange = {
                                        drafts[index] = draft.copy(
                                            quantity = normalizeTomanDraft(it).take(5),
                                        )
                                    },
                                    modifier = fieldModifier,
                                    singleLine = true,
                                    label = { Text("تعداد", maxLines = 1) },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Number,
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    enabled = !isSaving,
                                )
                            }
                            val unitPriceField: @Composable (Modifier) -> Unit = { fieldModifier ->
                                JibikTomanField(
                                    value = draft.unitPrice,
                                    onValueChange = {
                                        drafts[index] = draft.copy(unitPrice = it)
                                    },
                                    label = "قیمت واحد",
                                    modifier = fieldModifier,
                                    allowZero = true,
                                    enabled = !isSaving,
                                )
                            }
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val stacked = maxWidth < 320.dp ||
                                    LocalDensity.current.fontScale >= 1.3f
                                if (stacked) {
                                    Column(
                                        Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        quantityField(Modifier.fillMaxWidth())
                                        unitPriceField(Modifier.fillMaxWidth())
                                    }
                                } else {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        quantityField(Modifier.weight(0.34f))
                                        unitPriceField(Modifier.weight(0.66f))
                                    }
                                }
                            }
                            Text(
                                "واحد اندازه‌گیری",
                                color = JibikStyle.muted, fontSize = 12.sp,
                            )
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                            ) {
                                PURCHASE_UNITS.forEach { option ->
                                    JibikGlassChip(
                                        text = option,
                                        selected = draft.unit == option,
                                        onClick = {
                                            if (!isSaving) drafts[index] =
                                                draft.copy(unit = option)
                                        },
                                        enabled = !isSaving,
                                    )
                                }
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
                            if (draft.name.isBlank()) {
                                JibikFormHint("نام کالا را وارد کن.", isWarning = true)
                            } else if (parseTomanDraft(draft.quantity)?.let { it in 1..10_000L } != true) {
                                JibikFormHint("تعداد باید بین ۱ تا ۱۰٬۰۰۰ باشد.", isWarning = true)
                            } else if (parseTomanDraft(draft.unitPrice) == null) {
                                JibikFormHint("قیمت واحد را وارد کن.", isWarning = true)
                            } else if (!isSelectableCategory(draft.category, false, customCategories)) {
                                JibikFormHint("دسته معتبر برای این قلم انتخاب کن.", isWarning = true)
                            } else if (lineTotal == null) {
                                JibikFormHint("جمع قلم از محدوده مجاز بیشتر است.", isWarning = true)
                            } else {
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
                    Text(if (drafts.size == 20) "حداکثر ۲۰ قلم" else "＋ افزودن قلم دیگر (${drafts.size}/۲۰)")
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
                            if (incompleteCount > 0) "$incompleteCount قلم نیاز به تکمیل دارد؛ ثبت غیرفعال است."
                            else "$completeCount قلم آماده ثبت · مبلغ فقط یک‌بار از حساب کم می‌شود.",
                            isWarning = incompleteCount > 0,
                        )
                    }
                }
            }
        },
        confirmButton = {
            LiquidGlassButton(
                text = if (isSaving) "در حال ثبت..." else if (valid) "ثبت خرید • " + money(total!!) else "تکمیل اقلام برای ثبت",
                enabled = valid,
                tone = GlassTone.EMERALD,
                onClick = {
                    val sum = total ?: return@LiquidGlassButton
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
            )
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
