package ir.jibik.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibik.core.finance.Account
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

private val Green = Color(0xFF07574C)
private val Ink = Color(0xFF102D31)
private val Subtle = Color(0xFF677A78)
private val WarmBackground = Color(0xFFFFFAF5)
private val White = Color.White

private fun accountMoney(amount: Long): String = formatToman(amount)

internal fun parseAccountAmount(raw: String): Long? {
    val normalized = raw.trim().map { char ->
        when (char) {
            in '۰'..'۹' -> '0' + (char - '۰')
            in '٠'..'٩' -> '0' + (char - '٠')
            else -> char
        }
    }.joinToString("").replace(",", "").replace("٬", "").replace(" ", "")
    return normalized.toLongOrNull()
}

@Composable
internal fun AccountsScreen(
    accounts: List<Account>,
    balances: Map<String, Long>,
    modifier: Modifier = Modifier,
    onAdd: () -> Unit,
    onTransfer: () -> Unit,
    onGoals: () -> Unit,
    onCategories: () -> Unit,
    onObligations: () -> Unit,
) {
    val totalBalance = balances.values.fold(0L) { total, value ->
        Math.addExact(total, value)
    }
    Column(
        modifier.fillMaxSize().liquidBackdrop()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("حساب‌های من", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                Text("هر حساب، یک تصویر شفاف از پولت", fontSize = 12.sp, color = Subtle)
            }
            JibikBrandLogo(Modifier.size(44.dp))
        }
        JibikHero(Modifier.fillMaxWidth(), illustration = JiboArtwork.ACCOUNTS) {
            Text("موجودی کل حساب‌ها", fontSize = 13.sp, color = Color(0xFFDAF1E7))
            JibikHeroAmount(accountMoney(totalBalance))
            Text(
                "${accounts.size} حساب ثبت‌شده · بدون دوباره‌شماری انتقال‌ها",
                fontSize = 12.sp, color = Color(0xFFD4EEE6),
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LiquidGlassButton(
                text = "＋ حساب جدید",
                onClick = onAdd,
                tone = GlassTone.HONEY,
                modifier = Modifier.weight(1f).height(50.dp),
            )
            LiquidGlassButton(
                text = "انتقال پول",
                onClick = onTransfer,
                enabled = accounts.size > 1,
                tone = GlassTone.CRYSTAL,
                modifier = Modifier.weight(1f).height(50.dp),
            )
        }
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            tone = GlassTone.CRYSTAL,
            onClick = onGoals,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "هدف‌های پس‌انداز", fontSize = 15.sp,
                        color = Green, fontWeight = FontWeight.ExtraBold,
                    )
                    Text("پس‌انداز واقعی، بر پایه حساب‌های تو", color = Subtle, fontSize = 12.sp)
                }
                Text("←", fontSize = 23.sp, color = Green)
            }
        }
        LiquidGlassCard(onClick = onObligations, tone = GlassTone.CRYSTAL) {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("تعهدات، بدهی‌ها و اقساط", color = Green,
                        fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Text("وام، قبض، طلب و تسویه‌های واقعی با سررسید شمسی",
                        color = Subtle, fontSize = 12.sp)
                }
                Text("‹", fontSize = 23.sp, color = Green)
            }
        }
        LiquidGlassCard(onClick = onCategories, tone = GlassTone.CRYSTAL) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("دسته‌ها و زیر‌دسته‌ها", color = Green,
                        fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "مدیریت دسته‌بندی هزینه، درآمد و دسته‌های شخصی",
                        color = Subtle, fontSize = 12.sp,
                    )
                }
                Text("‹", fontSize = 23.sp, color = Green)
            }
        }
        JibikSectionTitle(
            title = "حساب‌های ثبت‌شده",
            subtitle = "جزئیات موجودی هر حساب",
        )
        accounts.forEachIndexed { index, account ->
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tone = GlassTone.CRYSTAL,
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                Modifier.size(42.dp).background(
                                    if (index % 2 == 0) Color(0xFFD0EBDD) else Color(0xFFFFEBD7),
                                    RoundedCornerShape(14.dp),
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (account.id == "wallet") "◉" else "▤",
                                    fontSize = 20.sp, color = Green,
                                )
                            }
                            Column(
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    account.title, fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp, color = Ink,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text("حساب شخصی", fontSize = 11.sp, color = Subtle)
                            }
                        }
                        Text(
                            if (account.id == "wallet") "اصلی" else "فعال",
                            fontSize = 11.sp, color = Green,
                        )
                    }
                    HorizontalDivider(color = Color(0xFFE6EAE4))
                    JibikFinancialDetail(
                        label = "موجودی حساب",
                        value = accountMoney(
                            balances[account.id] ?: account.openingBalanceToman
                        ),
                        emphasis = true,
                        valueColor = Green,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
internal fun CreateAccountDialog(
    existing: List<Account>,
    onDismiss: () -> Unit,
    onSave: (Account) -> Unit,
    isSaving: Boolean = false,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var opening by rememberSaveable { mutableStateOf("0") }
    val amount = parseTomanDraft(opening)
    val cleanTitle = title.trim()
    val duplicated = existing.any { it.title.trim() == cleanTitle }
    val valid = !isSaving && cleanTitle.isNotBlank() &&
        cleanTitle.length <= 48 && !duplicated &&
        amount != null && amount >= 0L
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
                JibikBrandLogo(Modifier.size(38.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "حساب مالی جدید",
                        color = Ink, fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "برای هر کارت یا پس‌انداز، یک حساب",
                        color = Subtle, fontSize = 11.sp,
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                JibikFormHint(
                    "موجودی حساب‌هات جدا ثبت می‌شه تا انتقال پول دوباره‌شماری نشه.",
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(48) },
                    label = { Text("نام حساب") },
                    placeholder = { Text("مثلاً کارت بانکی یا پس‌انداز") },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp),
                )
                if (duplicated && cleanTitle.isNotEmpty()) {
                    JibikFormHint(
                        "حسابی با این نام قبلاً ثبت شده.",
                        isWarning = true,
                    )
                }
                JibikTomanField(
                    value = opening,
                    onValueChange = { opening = it },
                    label = "موجودی اولیه",
                    allowZero = true,
                )
                JibikFormHint(
                    "موجودی اولیه درآمد محسوب نمی‌شه؛ فقط مقدار شروع حسابه.",
                )
            }
        },
        confirmButton = {
            LiquidGlassButton(
                text = if (isSaving) "در حال ایجاد..." else "ایجاد حساب",
                enabled = valid,
                tone = GlassTone.EMERALD,
                onClick = {
                    onSave(
                        Account(
                            UUID.randomUUID().toString(),
                            cleanTitle,
                            amount ?: 0L,
                        )
                    )
                },
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("انصراف")
            }
        },
    )
}
