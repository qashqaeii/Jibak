package ir.jibik.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.jibik.core.finance.Account
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun SpendableBalanceCard(
    accounts: List<Account>, balances: Map<String, Long>, goals: List<SavingsGoalEntity>,
    obligations: List<FinancialObligationEntity>, payments: List<ObligationPaymentEntity>,
    visible: Boolean,
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("spending_horizon", Context.MODE_PRIVATE) }
    var chosen by remember { mutableLongStateOf(prefs.getLong("through_day", 0L)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay((jalaliDueDayEndExclusive(now) - now).coerceAtLeast(1000L))
        }
    }
    val today = remember(now) { parseJalaliDueDate(formatJalaliDueDate(now))!! }
    val end = remember(chosen, today) {
        if (chosen >= today) jalaliDueDayEndExclusive(chosen) else currentJalaliMonth(today).endExclusiveMillis
    }
    val result = remember(balances, goals, obligations, payments, end) {
        spendableBalance(balances, goals, obligations, payments, end)
    }
    var details by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    val date = formatJalaliDueDate(end - 1)
    val amount = if (visible) NumberFormat.getIntegerInstance(Locale("fa", "IR"))
        .format(result.available.abs()) + " تومان" else "••••"
    val label = if (visible && result.available.signum() < 0) "کسری تا تاریخ انتخابی" else "ماندهٔ قابل‌خرج"
    LiquidGlassCard(onClick = { details = true }, tone = GlassTone.CRYSTAL) {
        JibikSectionTitle(label, "تا $date")
        Text(amount, style = MaterialTheme.typography.titleLarge)
        Text("مشاهدهٔ محاسبه و تغییر تاریخ", color = JibikStyle.muted)
    }
    if (details) JibikModalDialog(
        modifier = Modifier.jibikDialogSurface(), properties = JibikWideDialogProperties,
        onDismissRequest = { details = false; editing = false },
        title = { JibikDialogHeading(label, "تا پایان روز $date") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(amount, style = MaterialTheme.typography.titleLarge)
                Text("از ماندهٔ فعلی حساب‌ها، پس‌انداز هدف‌ها تا سقف هر هدف و تعهدات پرداخت‌نشده تا این تاریخ کم می‌شود. حقوق آینده و طلب‌ها پس از دریافت وارد مانده می‌شوند.")
                if (visible) {
                    Text("موجودی حساب‌ها", style = MaterialTheme.typography.titleMedium)
                    accounts.forEach { Text(it.title + " · " + formatToman(balances.getOrDefault(it.id, 0))) }
                    Text("پس‌انداز کنارگذاشته‌شده", style = MaterialTheme.typography.titleMedium)
                    if (result.goals.isEmpty()) Text("مبلغی کنار گذاشته نشده است.")
                    result.goals.forEach { Text(it.title + " · " + formatToman(it.amount)) }
                    Text("اقساط و تعهدات تا این تاریخ", style = MaterialTheme.typography.titleMedium)
                    if (result.dues.isEmpty()) Text("تعهد پرداخت‌نشده‌ای در این بازه وجود ندارد.")
                    result.dues.forEach { Text(it.title + " · " + formatToman(it.amount)) }
                } else Text("برای دیدن مبالغ، نمایش موجودی را در خانه فعال کن.")
                if (editing) {
                    val day = parseJalaliDueDate(draft)
                    OutlinedTextField(value = draft, onValueChange = { draft = it.take(12) },
                        label = { Text("تاریخ شمسی، مثلاً ۱۴۰۵/۰۹/۱۰") }, singleLine = true,
                        isError = day == null || day < today)
                    TextButton(enabled = day != null && day >= today, onClick = {
                        chosen = requireNotNull(day)
                        prefs.edit().putLong("through_day", chosen).apply()
                        editing = false
                    }) { Text("اعمال تاریخ") }
                } else TextButton(onClick = { draft = date; editing = true }) { Text("انتخاب تاریخ حقوق یا تاریخ دلخواه") }
                if (chosen >= today) TextButton(onClick = {
                    chosen = 0; prefs.edit().remove("through_day").apply(); editing = false
                }) { Text("بازگشت به پایان ماه شمسی") }
            }
        },
        confirmButton = { LiquidGlassButton(text = "بستن", onClick = { details = false; editing = false }) },
    )
}
