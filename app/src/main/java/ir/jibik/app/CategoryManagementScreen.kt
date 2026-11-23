package ir.jibik.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import ir.jibik.core.finance.ExpenseCategoryCatalog
import ir.jibik.core.finance.IncomeCategoryCatalog
import java.util.UUID

/** Private, offline-only taxonomy management. Never changes historical ledger entries. */
@Composable
internal fun CategoryManagementScreen(
    categories: List<CustomCategoryEntity>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onArchive: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isSaving: Boolean = false,
) {
    var income by rememberSaveable { mutableStateOf(false) }
    val selectedKind = if (income) "INCOME" else "EXPENSE"
    val roots = if (income) IncomeCategoryCatalog.roots
        else ExpenseCategoryCatalog.roots
    val custom = categories.filter { it.kind == selectedKind }
    Column(
        modifier.fillMaxSize().liquidBackdrop()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        JibikSectionTitle(
            title = "مدیریت دسته‌ها",
            subtitle = "دسته اصلی، زیر‌دسته و جزئیات هزینه و درآمد",
            trailing = {
                TextButton(onClick = onBack) {
                    Text("بازگشت", color = JibikStyle.forest)
                }
            },
        )
        JibikHero(Modifier.fillMaxWidth()) {
            Text("طبقه‌بندی شخصی خودت", color = Color(0xFFD5F2E8),
                fontSize = 13.sp)
            Text(
                (roots.size + custom.count { !it.archived }).toString() +
                    " دسته اصلی و اختصاصی",
                color = Color.White, fontSize = 23.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                "تغییر دسته‌ها روی تراکنش‌های گذشته اثر نمی‌گذارد.",
                color = Color(0xFFDAF4E9), fontSize = 12.sp,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            LiquidGlassButton(
                text = "هزینه‌ها", onClick = { income = false },
                tone = if (!income) GlassTone.EMERALD else GlassTone.CRYSTAL,
                modifier = Modifier.weight(1f),
            )
            LiquidGlassButton(
                text = "درآمدها", onClick = { income = true },
                tone = if (income) GlassTone.EMERALD else GlassTone.CRYSTAL,
                modifier = Modifier.weight(1f),
            )
        }
        LiquidGlassButton(
            text = "＋ افزودن دسته یا زیر‌دسته شخصی",
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth(),
            tone = GlassTone.HONEY,
            enabled = !isSaving,
        )
        JibikSectionTitle("دسته‌های شخصی", "قابل ساخت و بایگانی در همین گوشی")
        if (custom.isEmpty()) {
            JibikSurfaceCard {
                Text(
                    "هنوز دسته اختصاصی نداری. از دکمه بالا یک دسته یا زیر‌دسته بساز.",
                    color = JibikStyle.muted, fontSize = 13.sp, lineHeight = 20.sp,
                )
            }
        } else {
            custom.sortedWith(compareBy<CustomCategoryEntity> { it.archived }
                .thenBy { it.displayName }).forEach { item ->
                LiquidGlassCard(tone = GlassTone.CRYSTAL) {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                item.title, fontSize = 14.sp,
                                color = if (item.archived) JibikStyle.muted else JibikStyle.ink,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (item.archived) "بایگانی شده • محفوظ برای سوابق"
                                else item.parentPath.ifEmpty { "دسته اصلی" },
                                color = JibikStyle.muted, fontSize = 11.sp,
                                maxLines = 2,
                            )
                        }
                        TextButton(
                            onClick = { onArchive(item.id, !item.archived) },
                            enabled = !isSaving,
                        ) {
                            Text(
                                if (item.archived) "بازیابی" else "بایگانی",
                                color = if (item.archived)
                                    JibikStyle.forest else JibikStyle.coral,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
        JibikSectionTitle("دسته‌های پیش‌فرض", "ساختار کامل سه‌سطحی و آماده استفاده")
        roots.forEach { root ->
            LiquidGlassCard(
                tone = GlassTone.CRYSTAL,
                padding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 13.dp, vertical = 11.dp,
                ),
            ) {
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(root.title, color = JibikStyle.ink,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        root.children.size.toString() + " زیر‌دسته",
                        fontSize = 11.sp, color = JibikStyle.muted,
                    )
                }
            }
        }
        JibikFormHint(
            "برای افزودن زیر‌دسته به هرکدام، روی «افزودن» بزن و دسته والد رو انتخاب کن. " +
                "دسته‌های پیش‌فرض حذف نمی‌شن تا گزارش‌های مالی سازگار بمونن."
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
internal fun NewCustomCategoryDialog(
    existing: List<CustomCategoryEntity>,
    isSaving: Boolean = false,
    onSave: (CustomCategoryEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var income by rememberSaveable { mutableStateOf(false) }
    var parentPath by rememberSaveable { mutableStateOf("") }
    var showParentPicker by rememberSaveable { mutableStateOf(false) }
    val kind = if (income) "INCOME" else "EXPENSE"
    val candidate = CustomCategoryEntity(
        id = "__pending__", kind = kind, parentPath = parentPath,
        title = title.trim(), createdAtMillis = 0L,
    )
    val error = customCategoryValidation(candidate, existing)
    AlertDialog(
        modifier = Modifier.liquidGlass(
            tone = GlassTone.FROSTED, radius = 26.dp, elevation = 15.dp,
        ),
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = Color.Transparent,
        title = {
            Text("دسته‌بندی شخصی جدید", fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold, color = JibikStyle.ink)
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Text("نوع دسته", color = JibikStyle.muted, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    LiquidGlassButton(
                        text = "هزینه", onClick = { income = false; parentPath = "" },
                        tone = if (!income) GlassTone.EMERALD else GlassTone.CRYSTAL,
                        modifier = Modifier.weight(1f),
                    )
                    LiquidGlassButton(
                        text = "درآمد", onClick = { income = true; parentPath = "" },
                        tone = if (income) GlassTone.EMERALD else GlassTone.CRYSTAL,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(40) },
                    label = { Text("نام دسته جدید") },
                    placeholder = { Text("مثلاً لوازم باغچه") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isSaving,
                )
                Text("والد یا محل قرارگیری", color = JibikStyle.muted, fontSize = 12.sp)
                LiquidGlassCard(onClick = {
                    if (!isSaving) showParentPicker = true
                }, tone = GlassTone.CRYSTAL) {
                    Text(
                        if (parentPath.isBlank()) "دسته اصلی جدید"
                        else parentPath,
                        color = JibikStyle.ink, fontSize = 13.sp,
                        maxLines = 3,
                    )
                    Text("تغییر جایگاه ‹", color = JibikStyle.forest, fontSize = 12.sp)
                }
                JibikFormHint(
                    "حداکثر سه سطح. با بایگانی دسته، تراکنش‌های قبلی حذف نمی‌شن."
                )
                if (title.isNotBlank() && error != null) {
                    JibikFormHint(error, isWarning = true)
                }
            }
        },
        confirmButton = {
            LiquidGlassButton(
                text = if (isSaving) "در حال ذخیره..." else "ذخیره دسته",
                onClick = {
                    if (error == null) {
                        onSave(candidate.copy(
                            id = UUID.randomUUID().toString(),
                            createdAtMillis = System.currentTimeMillis(),
                        ))
                    }
                },
                enabled = !isSaving && error == null,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("انصراف", color = JibikStyle.muted)
            }
        },
    )
    if (showParentPicker && !isSaving) {
        CategoryPickerDialog(
            income = income,
            selected = parentPath,
            custom = existing,
            parentOnly = true,
            onSelected = {
                parentPath = it
                showParentPicker = false
            },
            onDismiss = { showParentPicker = false },
        )
    }
}
