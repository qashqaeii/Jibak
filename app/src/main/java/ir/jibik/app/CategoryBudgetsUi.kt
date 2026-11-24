package ir.jibik.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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

/** Persistent caps are renewed on each Jalali month without overwriting history. */
@Composable
internal fun CategoryBudgetsPanel(
    budgets: List<CategoryBudgetEntity>,
    usage: List<CategoryBudgetUsage>,
    onAddOrEdit: (CategoryBudgetEntity?) -> Unit,
    onRemove: (String) -> Unit,
    isSaving: Boolean,
) {
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    JibikSectionTitle(
        title = "بودجه دسته‌ها و زیر‌دسته‌ها",
        subtitle = "هر سقف مستقل است؛ بودجه‌های والد و فرزند با هم جمع نمی‌شوند.",
    )
    LiquidGlassButton(
        text = "＋ تعیین سقف برای دسته",
        onClick = { onAddOrEdit(null) },
        modifier = Modifier.fillMaxWidth(),
        tone = GlassTone.HONEY,
        enabled = !isSaving,
    )
    if (budgets.isEmpty()) {
        JibikStatePanel(
            title = "سقف دسته‌ای نداری",
            description = "برای دسته‌ای مثل خوراک، یک سقف تعیین کن تا پیشرفت خرج و هشدار نزدیک‌شدن به سقف رو ببینی.",
        )
    } else {
        usage.forEach { line ->
            LiquidGlassCard(tone = GlassTone.CRYSTAL) {
                Text(
                    line.categoryPath.substringAfterLast(" / "),
                    fontSize = 15.sp, color = JibikStyle.ink,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    line.categoryPath,
                    fontSize = 12.sp, color = JibikStyle.muted,
                    lineHeight = 19.sp, maxLines = 2,
                )
                JibikFinancialDetail(
                    label = "هزینه این ماه",
                    value = formatToman(line.spentToman),
                    valueColor = if (line.alert == "over") JibikStyle.coral
                        else JibikStyle.ink,
                    emphasis = true,
                )
                JibikFinancialDetail(
                    label = "سقف ماهانه",
                    value = formatToman(line.limitToman),
                )
                JibikProgressTrack(
                    progress = line.usageRatio.toFloat(),
                    color = when (line.alert) {
                        "over" -> JibikStyle.coral
                        "warning" -> JibikStyle.orange
                        else -> JibikStyle.forest
                    },
                )
                Text(
                    when (line.alert) {
                        "over" -> "سقف این دسته پر شده یا از آن عبور کرده است."
                        "warning" -> "هشدار: به سقف تعیین‌شده نزدیک می‌شوی."
                        else -> "باقی‌مانده: " + formatToman(line.remainingToman)
                    },
                    color = if (line.alert == "over") JibikStyle.coral
                        else JibikStyle.muted,
                    fontSize = 12.sp,
                )
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiquidGlassButton(
                        text = "ویرایش",
                        tone = GlassTone.CRYSTAL,
                        onClick = {
                            onAddOrEdit(budgets.first { it.categoryPath == line.categoryPath })
                        },
                        enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { pendingDelete = line.categoryPath }, enabled = !isSaving,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("حذف سقف", color = JibikStyle.coral, fontSize = 12.sp)
                    }
                }
            }
        }
    }
    pendingDelete?.let { path ->
        AlertDialog(
            modifier = Modifier.jibikDialogSurface(),
            containerColor = Color.Transparent,
            onDismissRequest = { if (!isSaving) pendingDelete = null },
            title = { Text("حذف سقف دسته", color = JibikStyle.ink,
                fontWeight = FontWeight.ExtraBold) },
            text = { Text(
                "فقط سقف بودجه «" + path + "» حذف می‌شود؛ تراکنش‌ها و " +
                    "گزارش‌های قبلی بدون تغییر می‌مانند.",
                color = JibikStyle.muted, fontSize = 13.sp,
            ) },
            confirmButton = {
                TextButton(onClick = {
                    if (!isSaving) {
                        pendingDelete = null
                        onRemove(path)
                    }
                }, enabled = !isSaving) {
                    Text("حذف سقف", color = JibikStyle.coral)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }, enabled = !isSaving) {
                    Text("انصراف", color = JibikStyle.forest)
                }
            },
        )
    }
}

@Composable
internal fun CategoryBudgetDialog(
    previous: CategoryBudgetEntity?,
    existingBudgets: List<CategoryBudgetEntity>,
    customCategories: List<CustomCategoryEntity>,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (CategoryBudgetEntity) -> Unit,
) {
    var category by rememberSaveable(previous?.categoryPath) {
        mutableStateOf(previous?.categoryPath.orEmpty())
    }
    var amount by rememberSaveable(previous?.categoryPath) {
        mutableStateOf(previous?.limitToman?.toString().orEmpty())
    }
    var warning by rememberSaveable(previous?.categoryPath) {
        mutableStateOf(previous?.warningPercent ?: 80)
    }
    var picking by rememberSaveable { mutableStateOf(false) }
    val parsed = parseTomanDraft(amount)
    val categoryValid = category.isNotEmpty() &&
        isSelectableCategory(category, false, customCategories)
    val duplicates = previous == null &&
        existingBudgets.any { it.categoryPath == category }
    AlertDialog(
        modifier = Modifier.jibikDialogSurface(),
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = Color.Transparent,
        title = {
            JibikDialogHeading(if (previous == null) "بودجه دسته جدید" else "ویرایش بودجه دسته", "سقف هزینه و هشدار ماهانه")
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 410.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("دسته یا زیر‌دسته هزینه", color = JibikStyle.muted, fontSize = 12.sp)
                LiquidGlassCard(
                    onClick = { if (!isSaving && previous == null) picking = true },
                    tone = GlassTone.CRYSTAL,
                ) {
                    Text(category.ifBlank { "انتخاب دسته هزینه" },
                        color = JibikStyle.ink, fontSize = 13.sp)
                    if (previous == null) {
                        Text("‹ انتخاب دسته", color = JibikStyle.forest, fontSize = 12.sp)
                    }
                }
                JibikTomanField(value = amount, onValueChange = { amount = it },
                    label = "سقف ماهانه (تومان)")
                Text("هشدار در میزان مصرف", color = JibikStyle.muted, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(70, 80, 90).forEach { threshold ->
                        JibikGlassChip(
                            text = "$threshold٪", selected = warning == threshold,
                            onClick = { warning = threshold },
                            enabled = !isSaving,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                JibikFormHint(
                    "سقف از ابتدای هر ماه شمسی روی خرج واقعی همین دسته و زیرمجموعه‌اش حساب می‌شود."
                )
                if (duplicates) JibikFormHint(
                    "برای این دسته سقف داری؛ همان سقف را ویرایش کن.", isWarning = true)
            }
        },
        confirmButton = {
            LiquidGlassButton(
                text = if (isSaving) "در حال ذخیره..." else "ذخیره بودجه",
                enabled = !isSaving && categoryValid && !duplicates &&
                    parsed != null && parsed > 0L,
                onClick = {
                    val sum = parsed
                    if (sum != null && sum > 0L && categoryValid && !duplicates) {
                        onSave(CategoryBudgetEntity(category, sum, warning))
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
    if (picking && previous == null && !isSaving) {
        CategoryPickerDialog(
            income = false, selected = category, custom = customCategories,
            onSelected = { category = it; picking = false },
            onDismiss = { picking = false },
        )
    }
}
