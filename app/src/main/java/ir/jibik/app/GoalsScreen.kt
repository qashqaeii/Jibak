package ir.jibik.app

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibik.core.finance.Account
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

private val ForestGoal = Color(0xFF07574C)
private val InkGoal = Color(0xFF102D31)
private val MutedGoal = Color(0xFF677A78)
private val CreamGoal = Color(0xFFFFFAF5)
private val OrangeGoal = Color(0xFFFF8A1F)
private fun goalMoney(amount: Long) =
    NumberFormat.getNumberInstance(Locale("fa", "IR")).format(amount) + " تومان"

/** Planned savings, backed by the designated account's real balance. */
@Composable
internal fun GoalsScreen(
    goals: List<SavingsGoalEntity>,
    accounts: List<Account>,
    balances: Map<String, Long>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(CreamGoal)
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("هدف‌های پس‌انداز", fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold, color = InkGoal)
                Text("هر هدف به یک حساب واقعی متصل است.", fontSize = 12.sp, color = MutedGoal)
            }
            JiboMascot(Modifier.size(52.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(0.35f),
                shape = RoundedCornerShape(15.dp)) {
                Text("بازگشت", fontSize = 12.sp)
            }
            Button(onClick = onAdd, modifier = Modifier.weight(0.65f),
                enabled = accounts.any { a ->
                    a.id != "wallet" && goals.none { it.accountId == a.id }
                }, shape = RoundedCornerShape(15.dp)) {
                Text("＋ ساخت هدف", fontSize = 13.sp)
            }
        }
        if (goals.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    JiboMascot(Modifier.size(110.dp))
                    Text("اولین هدف پس‌اندازت رو بساز", fontWeight = FontWeight.Bold,
                        color = InkGoal, fontSize = 18.sp)
                    Text(
                        "ابتدا در حساب‌ها، یک حساب اختصاصی پس‌انداز بساز. " +
                            "بعد اون رو به هدفی مثل سفر یا خرید خونه متصل کن.",
                        color = MutedGoal, fontSize = 13.sp, lineHeight = 22.sp,
                    )
                }
            }
        }
        goals.forEach { goal ->
            val balance = (balances[goal.accountId] ?: 0L).coerceAtLeast(0L)
            val progress = (balance.toDouble() / goal.targetToman.toDouble())
                .toFloat().coerceIn(0f, 1f)
            val accountName = accounts.firstOrNull { it.id == goal.accountId }?.title ?: "حساب نامشخص"
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(goal.title, color = InkGoal,
                            fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        Text(if (progress >= 1f) "تکمیل" else "در حال پیشرفت",
                            color = ForestGoal, fontSize = 12.sp)
                    }
                    Text("حساب: $accountName", color = MutedGoal, fontSize = 12.sp)
                    Text(goalMoney(balance), color = ForestGoal,
                        fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(9.dp),
                        color = if (progress >= 1f) ForestGoal else OrangeGoal,
                        trackColor = CreamGoal,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("هدف: " + goalMoney(goal.targetToman),
                            fontSize = 12.sp, color = MutedGoal)
                        Text("${(progress * 100).toInt()}٪", fontSize = 12.sp, color = ForestGoal)
                    }
                    Text("برای پیشرفت واقعی، به این حساب پول انتقال بده.",
                        fontSize = 12.sp, color = MutedGoal)
                }
            }
        }
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
internal fun NewGoalDialog(
    accounts: List<Account>,
    goals: List<SavingsGoalEntity>,
    onDismiss: () -> Unit,
    onSave: (SavingsGoalEntity) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable { mutableStateOf("") }
    var accountId by rememberSaveable { mutableStateOf("") }
    val eligible = accounts.filter { account ->
        account.id != "wallet" && goals.none { it.accountId == account.id }
    }
    val targetAmount = parseAccountAmount(target)
    val valid = title.trim().isNotBlank() && title.length <= 60 &&
        targetAmount != null && targetAmount > 0 && eligible.any { it.id == accountId }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(26.dp),
        title = { Text("هدف جدید", fontWeight = FontWeight.ExtraBold, color = InkGoal) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                Text("برای هر هدف، یک حساب اختصاصی انتخاب کن تا پول دوباره‌شماری نشه.",
                    fontSize = 12.sp, color = MutedGoal)
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(60) },
                    label = { Text("نام هدف") },
                    placeholder = { Text("مثلاً سفر تابستون") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                OutlinedTextField(
                    value = target, onValueChange = { target = it },
                    label = { Text("مبلغ هدف (تومان)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                )
                Text("حساب پس‌انداز", fontSize = 12.sp, color = MutedGoal)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    eligible.forEach { account ->
                        FilterChip(
                            selected = accountId == account.id,
                            onClick = { accountId = account.id },
                            label = { Text(account.title, maxLines = 1, fontSize = 12.sp) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    val amount = targetAmount ?: return@Button
                    onSave(SavingsGoalEntity(
                        id = UUID.randomUUID().toString(),
                        title = title.trim(),
                        targetToman = amount,
                        accountId = accountId,
                        createdAtMillis = System.currentTimeMillis(),
                    ))
                },
            ) { Text("ذخیره هدف") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
