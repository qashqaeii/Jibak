package ir.jibik.app

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
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
import ir.jibik.core.finance.Account
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

private fun goalMoney(amount: Long) =
    NumberFormat.getNumberInstance(Locale("fa", "IR")).format(amount) + " تومان"

/** Real balances, not a fabricated savings counter. */
@Composable
internal fun GoalsScreen(
    goals: List<SavingsGoalEntity>,
    accounts: List<Account>,
    balances: Map<String, Long>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onTransferTo: (String) -> Unit,
) {
    val eligible = accounts.any { a ->
        a.id != "wallet" && goals.none { it.accountId == a.id }
    }
    val achieved = goals.count {
        (balances[it.accountId] ?: 0L) >= it.targetToman
    }
    Column(
        Modifier.fillMaxSize().liquidBackdrop()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "هدف‌های پس‌انداز", fontSize = 23.sp,
                    fontWeight = FontWeight.ExtraBold, color = JibikStyle.ink,
                )
                Text(
                    "برای آینده، قدم‌های کوچک اما واقعی", fontSize = 12.sp,
                    color = JibikStyle.muted,
                )
            }
            JiboMascot(Modifier.size(62.dp))
        }
        JibikHero(Modifier.fillMaxWidth()) {
            Text(
                "مسیر پس‌انداز", color = Color(0xFFDCF3EA), fontSize = 13.sp,
            )
            Text(
                "${goals.size} هدف مالی",
                color = Color.White, fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                if (goals.isEmpty()) "اولین قدم رو امروز بردار."
                else "${achieved} هدف به مقصد رسیده.",
                color = Color(0xFFE1F4EC), fontSize = 12.sp,
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LiquidGlassButton(
                text = "بازگشت",
                onClick = onBack,
                tone = GlassTone.CRYSTAL,
                modifier = Modifier.weight(0.35f).height(50.dp),
            )
            LiquidGlassButton(
                text = "＋ ساخت هدف",
                onClick = onAdd,
                enabled = eligible,
                tone = GlassTone.HONEY,
                modifier = Modifier.weight(0.65f).height(50.dp),
            )
        }
        if (goals.isEmpty()) {
            JibikSurfaceCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    JiboMascot(Modifier.size(72.dp))
                    Column(Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "اولین هدف پس‌اندازت رو بساز",
                            color = JibikStyle.ink, fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        JibikFormHint(
                            "ابتدا یک حساب جدا برای پس‌انداز بساز؛ " +
                                "بعد هدف رو به اون حساب وصل کن.",
                        )
                    }
                }
            }
        } else {
            JibikSectionTitle(
                "برنامه‌های مالی من",
                "پیشرفت بر پایه موجودی واقعی حساب‌ها",
            )
        }
        goals.forEach { goal ->
            val balance = (balances[goal.accountId] ?: 0L).coerceAtLeast(0L)
            val fraction = (balance.toDouble() / goal.targetToman.toDouble())
                .toFloat().coerceIn(0f, 1f)
            val accountName = accounts.firstOrNull { it.id == goal.accountId }?.title
                ?: "حساب نامشخص"
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                tone = GlassTone.CRYSTAL,
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(13.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            goal.title, color = JibikStyle.ink,
                            fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.weight(1f), maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (fraction >= 1f) "تکمیل" else "در حال پیشرفت",
                            color = JibikStyle.forest, fontSize = 11.sp,
                        )
                    }
                    Text(
                        "حساب: $accountName",
                        color = JibikStyle.muted, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        goalMoney(balance),
                        color = JibikStyle.forest,
                        fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().height(9.dp),
                        color = if (fraction >= 1f) JibikStyle.forest else JibikStyle.orange,
                        trackColor = JibikStyle.cream,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "هدف: " + goalMoney(goal.targetToman),
                            fontSize = 12.sp, color = JibikStyle.muted,
                            modifier = Modifier.weight(1f), maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${(fraction * 100).toInt()}٪",
                            fontSize = 12.sp, color = JibikStyle.forest,
                        )
                    }
                    LiquidGlassButton(
                        text = if (fraction >= 1f) "انتقال پول به این حساب"
                            else "افزودن پول به هدف",
                        onClick = { onTransferTo(goal.accountId) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = accounts.any { it.id != goal.accountId },
                        tone = GlassTone.HONEY,
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
internal fun NewGoalDialog(
    accounts: List<Account>,
    goals: List<SavingsGoalEntity>,
    onDismiss: () -> Unit,
    onSave: (SavingsGoalEntity) -> Unit,
    isSaving: Boolean = false,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var accountId by rememberSaveable { mutableStateOf("") }
    val eligible = accounts.filter { account ->
        account.id != "wallet" && goals.none { it.accountId == account.id }
    }
    val targetAmount = parseTomanDraft(target)
    val valid = !isSaving && title.trim().isNotBlank() && title.length <= 60 &&
        targetAmount != null && targetAmount > 0L &&
        eligible.any { it.id == accountId }

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
                JibikBrandLogo(Modifier.size(39.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        "هدف مالی جدید", color = JibikStyle.ink,
                        fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        "پس‌انداز با مسیر مشخص", fontSize = 11.sp,
                        color = JibikStyle.muted,
                    )
                }
            }
        },
        text = {
            Column(
                Modifier.heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                JibikFormHint(
                    "هر هدف به یک حساب اختصاصی وصل می‌شه؛ " +
                        "پس‌انداز رو دوبار حساب نمی‌کنیم.",
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(60) },
                    label = { Text("نام هدف") },
                    placeholder = { Text("مثلاً سفر خانوادگی") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp),
                )
                JibikTomanField(
                    value = target,
                    onValueChange = { target = it },
                    label = "مبلغ هدف",
                )
                JibikAccountChoice(
                    title = "حساب پس‌انداز",
                    accounts = eligible,
                    selectedId = accountId,
                    onSelect = { accountId = it },
                    emptyHint = "ابتدا یک حساب پس‌انداز جدا بساز.",
                )
                if (eligible.isNotEmpty() && accountId.isBlank()) {
                    JibikFormHint("حساب مربوط به هدف رو انتخاب کن.", isWarning = true)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                shape = RoundedCornerShape(15.dp),
                onClick = {
                    val amount = targetAmount ?: return@Button
                    onSave(
                        SavingsGoalEntity(
                            id = UUID.randomUUID().toString(),
                            title = title.trim(),
                            targetToman = amount,
                            accountId = accountId,
                            createdAtMillis = System.currentTimeMillis(),
                        )
                    )
                },
            ) { Text(if (isSaving) "در حال ذخیره..." else "ساخت هدف") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("انصراف")
            }
        },
    )
}
