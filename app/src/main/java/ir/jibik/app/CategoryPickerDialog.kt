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

private data class CategoryChoice(
    val ids: List<String>,
    val title: String,
    val displayName: String,
    val hasChildren: Boolean,
)

@Composable
internal fun CategoryPickerDialog(
    income: Boolean,
    selected: String,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var path by rememberSaveable(income) { mutableStateOf(emptyList<String>()) }
    var search by rememberSaveable(income) { mutableStateOf("") }

    fun fullName(ids: List<String>): String = if (income) {
        IncomeCategoryCatalog.find(ids.last())?.displayName.orEmpty()
    } else {
        ExpenseCategoryCatalog.find(ids.last())?.displayName.orEmpty()
    }

    val items = if (search.isBlank()) {
        val nodes = if (income) IncomeCategoryCatalog.children(path)
            else ExpenseCategoryCatalog.children(path)
        nodes.map { child ->
            val ids = path + child.id
            CategoryChoice(
                ids = ids, title = child.title,
                displayName = fullName(ids),
                hasChildren = child.children.isNotEmpty(),
            )
        }
    } else if (income) {
        IncomeCategoryCatalog.search(search).map {
            CategoryChoice(it.ids, it.title, it.displayName,
                IncomeCategoryCatalog.children(it.ids).isNotEmpty())
        }
    } else {
        ExpenseCategoryCatalog.search(search).map {
            CategoryChoice(it.ids, it.title, it.displayName,
                ExpenseCategoryCatalog.children(it.ids).isNotEmpty())
        }
    }

    val selectedLevel = path.takeIf { it.isNotEmpty() }?.let(::fullName).orEmpty()
    val breadCrumbs = (if (income) IncomeCategoryCatalog.all().firstOrNull {
        it.ids == path
    }?.labels else ExpenseCategoryCatalog.all().firstOrNull {
        it.ids == path
    }?.labels).orEmpty()

    AlertDialog(
        modifier = Modifier.liquidGlass(
            tone = GlassTone.FROSTED, radius = 26.dp, elevation = 15.dp,
        ),
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    if (income) "دسته‌بندی درآمد" else "دسته‌بندی هزینه",
                    color = JibikStyle.ink, fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    "دسته اصلی ← زیر‌دسته ← جزئیات",
                    color = JibikStyle.muted, fontSize = 12.sp,
                )
            }
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
                            TextButton(onClick = { path = path.dropLast(1) }) {
                                Text("‹ بازگشت", color = JibikStyle.forest)
                            }
                        }
                        Text(
                            if (path.isEmpty()) "همه دسته‌های اصلی"
                            else breadCrumbs.joinToString(" / "),
                            color = JibikStyle.muted, fontSize = 12.sp,
                            maxLines = 2, modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Text(
                        "${items.size} نتیجه مرتبط",
                        color = JibikStyle.muted, fontSize = 12.sp,
                    )
                }
                if (items.isEmpty()) {
                    JibikFormHint(
                        "دسته‌ای پیدا نشد. عبارت کوتاه‌تری جستجو کن."
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 335.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        items(items, key = { it.ids.joinToString(":") }) { item ->
                            val selectedNow = item.displayName == selected
                            val colour = if (selectedNow) Color(0xFFE1F4EA)
                                else Color(0xF6FFFFFF)
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .background(colour, RoundedCornerShape(15.dp))
                                    .clickable {
                                        if (item.hasChildren && search.isBlank()) {
                                            path = item.ids
                                        } else {
                                            onSelected(item.displayName)
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
                                            item.displayName,
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
                if (search.isBlank() && selectedLevel.isNotBlank()) {
                    LiquidGlassButton(
                        text = "انتخاب همین سطح: " + breadCrumbs.last(),
                        onClick = { onSelected(selectedLevel) },
                        modifier = Modifier.fillMaxWidth(),
                        tone = GlassTone.HONEY,
                    )
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
