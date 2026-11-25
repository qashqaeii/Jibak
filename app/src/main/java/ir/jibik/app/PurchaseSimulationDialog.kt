package ir.jibik.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.jibik.core.finance.Account

@Composable
internal fun PurchaseSimulationDialog(accounts: List<Account>, balances: Map<String, Long>,
    goals: List<SavingsGoalEntity>, envelopes: List<MoneyEnvelope>, forecast: CashForecast,
    onDismiss: () -> Unit) {
    var account by rememberSaveable { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }
    var draft by rememberSaveable { mutableStateOf("") }
    val amount = parseTomanDraft(draft)
    val impact = if (amount != null && amount > 0 && balances.containsKey(account))
        simulatePurchase(forecast, account, amount, balances, goals, envelopes) else null
    JibikModalDialog(properties = JibikWideDialogProperties, modifier = Modifier.jibikDialogSurface(),
        onDismissRequest = onDismiss,
        title = { JibikDialogHeading("شبیه‌ساز خرید", "بررسی پیش از پرداخت · بدون ثبت تراکنش") },
        text = {
            Column(Modifier.fillMaxWidth().jibikScrollableBody(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                JibikTomanField(draft, { draft = it }, "مبلغ خرید احتمالی")
                JibikAccountChoice("از کدام حساب؟", accounts, account, { account = it })
                impact?.let { row ->
                    JibikSurfaceCard {
                        JibikSectionTitle(if (row.fits) "با برنامه فعلی سازگار است" else "نیازمند بررسی",
                            "تا " + formatJalaliDueDate(forecast.endExclusive - 1))
                        JibikFinancialDetail("موجودی آزاد حساب انتخابی", formatToman(row.accountFree))
                        JibikFinancialDetail("مانده قابل‌خرج پس از خرید", forecastMoney(row.after), emphasis = true)
                        JibikFinancialDetail("سقف روزانه بعد از خرید", forecastMoney(row.dailyAfter))
                        if (row.accountShortfall.signum() > 0)
                            JibikFormHint("کمبود موجودی آزاد این حساب: " + forecastMoney(row.accountShortfall), isWarning = true)
                        if (row.planShortfall.signum() > 0)
                            JibikFormHint("کسری برنامه تا تاریخ انتخابی: " + forecastMoney(row.planShortfall), isWarning = true)
                    }
                }
                JibikFormHint("فقط شبیه‌سازی است. هیچ پرداختی انجام نمی‌شود، پاکتی آزاد نمی‌شود و حقوق آینده نیز به موجودی اضافه نمی‌شود.")
            }
        },
        confirmButton = { LiquidGlassButton("بازگشت", onClick = onDismiss) })
}
