package ir.jibik.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibik.core.finance.ExpenseCategoryCatalog
import ir.jibik.core.finance.IncomeCategoryCatalog

@Composable
internal fun CategoryPickerDialog(
    income: Boolean,
    selected: String,
    custom: List<CustomCategoryEntity> = emptyList(),
    parentOnly: Boolean = false,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var path by rememberSaveable(income, parentOnly) { mutableStateOf("") }
    var search by rememberSaveable(income, parentOnly) { mutableStateOf("") }
    val choices = categoryOptions(income, custom, parentPath = path, search = search)
        .filter { !parentOnly || it.depth < 3 }

    AlertDialog(
        modifier = Modifier.liquidGlass(
            tone = GlassTone.FROSTED, radius = 26.dp, elevation = 15.dp,
        ),
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        title = {
            JibikDialogHeading(if (parentOnly) "انتخاب دسته والد" else if (income) "دسته‌بندی درآمد" else "دسته‌بندی هزینه", "دسته اصلی ← زیر‌دسته ← جزئیات")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it.take(70) },
                    placeholder = { Text("جستجوی دسته یا زیر‌دسته...") },
                    leadingIcon = { Text("⌕", fontSize = 23.sp, color = JibikStyle.forest) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (search.isBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (path.isNotEmpty()) {
                            TextButton(onClick = {
                                path = path.substringBeforeLast(" / ", "")
                            }) {
                                Text("‹ بازگشت", color = JibikStyle.forest)
                            }
                        }
                        Text(
                            if (path.isEmpty()) "همه دسته‌های اصلی"
                            else path,
                            color = JibikStyle.muted, fontSize = 12.sp,
                            maxLines = 2, modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Text(
                        "${choices.size} نتیجه مرتبط",
                        color = JibikStyle.muted, fontSize = 12.sp,
                    )
                }
                if (choices.isEmpty()) {
                    JibikFormHint(
                        "دسته‌ای پیدا نشد. عبارت کوتاه‌تری جستجو کن."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 335.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        items(choices, key = { it.path }) { item ->
                            val selectedNow = item.path == selected
                            val colour = if (selectedNow) Color(0xFFE1F4EA)
                                else Color(0xF6FFFFFF)
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .background(colour, RoundedCornerShape(15.dp))
                                    .clickable {
                                        if (item.hasChildren && search.isBlank()) {
                                            path = item.path
                                        } else {
                                            onSelected(item.path)
                                        }
                                    }
                                    .padding(horizontal = 13.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column(Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(
                                        item.title,
                                        color = JibikStyle.ink, fontSize = 14.sp,
                                        fontWeight = if (selectedNow)
                                            FontWeight.ExtraBold else FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (search.isNotBlank()) {
                                        Text(
                                            item.path,
                                            color = JibikStyle.muted,
                                            fontSize = 11.sp, maxLines = 2,
                                        )
                                    }
                                }
                                Text(
                                    when {
                                        selectedNow -> "✓"
                                        item.hasChildren && search.isBlank() -> "‹"
                                        else -> "انتخاب"
                                    },
                                    color = JibikStyle.forest,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
                if (search.isBlank() && path.isNotBlank()) {
                    LiquidGlassButton(
                        text = "انتخاب همین سطح: " + path.substringAfterLast(" / "),
                        onClick = { onSelected(path) },
                        modifier = Modifier.fillMaxWidth(),
                        tone = GlassTone.HONEY,
                    )
                }
                if (parentOnly && search.isBlank() && path.isEmpty()) {
                    TextButton(onClick = { onSelected("") }) {
                        Text("بدون والد؛ ساخت دسته اصلی", color = JibikStyle.forest)
                    }
                }
                if (selected.isNotBlank()) {
                    HorizontalDivider(color = JibikStyle.line)
                    Text(
                        "انتخاب فعلی: " + selected,
                        color = JibikStyle.muted,
                        fontSize = 11.sp, maxLines = 2,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("بستن", color = JibikStyle.forest)
            }
        },
    )
}
