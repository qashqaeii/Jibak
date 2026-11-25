package ir.jibik.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibik.core.finance.LedgerRecord
import kotlinx.coroutines.launch

/** On-device insights and explicit-consent, authenticated AI in one premium sheet. */
@Composable
internal fun JiboAiDialog(
    feature: JiboAiFeature,
    records: List<LedgerRecord>,
    onDismiss: () -> Unit,
) {
    val snapshot = remember(records.toList()) { buildJiboSnapshot(records.toList()) }
    val scope = rememberCoroutineScope()
    val client = remember(BuildConfig.JIBIK_API_BASE_URL) {
        runCatching { JiboAiClient(BuildConfig.JIBIK_API_BASE_URL) }.getOrNull()
    }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    // JWT lives only in memory for this dialog; never in SavedState or local storage.
    var session by remember { mutableStateOf<String?>(null) }
    var consent by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var answer by remember { mutableStateOf<JiboAiAnswer?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        properties = JibikWideDialogProperties,
        onDismissRequest = { if (!working) onDismiss() },
        modifier = Modifier.jibikDialogSurface(),
        containerColor = Color.Transparent,
        title = {
            JibikDialogHeading("جیبو • " + feature.title, "دستیار مالی، نه تصمیم‌گیرنده")
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 510.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("خلاصه محلی • ۳۰ روز گذشته", color = JibikStyle.ink,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold)
                LiquidGlassCard(tone = GlassTone.CRYSTAL) {
                    Text("درآمد: " + formatToman(snapshot.incomeToman),
                        color = JibikStyle.forest, fontSize = 13.sp)
                    Text("هزینه: " + formatToman(snapshot.expenseToman),
                        color = JibikStyle.ink, fontSize = 13.sp)
                    Text(snapshot.incomeCount.toString() + " دریافتی و " +
                        snapshot.expenseCount.toString() + " هزینه",
                        color = JibikStyle.muted, fontSize = 12.sp)
                    Text(localFinancialHint(feature, snapshot),
                        color = JibikStyle.muted, fontSize = 12.sp, lineHeight = 20.sp)
                }
                if (client == null) {
                    Text(
                        "تحلیل ابری پس از راه‌اندازی سرور جیبیک فعال می‌شود. " +
                            "خلاصه بالا فقط روی گوشی محاسبه شده و هیچ داده‌ای ارسال نشده است.",
                        color = JibikStyle.muted, fontSize = 12.sp, lineHeight = 20.sp,
                    )
                } else if (answer != null) {
                    LiquidGlassCard {
                        Text("پیشنهاد هوشمند جیبو", color = JibikStyle.forest,
                            fontWeight = FontWeight.Bold)
                        Text(answer!!.summary, color = JibikStyle.ink,
                            fontSize = 13.sp, lineHeight = 21.sp)
                        answer!!.tips.forEachIndexed { index, tip ->
                            Text((index + 1).toString() + ". " + tip,
                                color = JibikStyle.ink, fontSize = 12.sp, lineHeight = 20.sp)
                        }
                        Text(if (answer!!.cached) "پاسخ ذخیره‌شده" else "تحلیل تازه",
                            color = JibikStyle.muted, fontSize = 10.sp)
                    }
                } else if (session == null) {
                    Text("برای تحلیل ابری، با حساب سرور جیبیک وارد شو.",
                        color = JibikStyle.muted, fontSize = 12.sp)
                    OutlinedTextField(
                        value = username, onValueChange = { username = it.take(80) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("نام کاربری جیبیک") },
                    )
                    OutlinedTextField(
                        value = password, onValueChange = { password = it.take(128) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("رمز حساب جیبیک") },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    LiquidGlassButton(
                        text = if (working) "در حال اتصال..." else "ورود امن",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !working && username.isNotBlank() && password.isNotBlank(),
                        onClick = {
                            scope.launch {
                                working = true
                                error = null
                                try {
                                    session = client.login(username, password)
                                    password = ""
                                } catch (e: JiboAiNetworkException) {
                                    error = e.message
                                } finally {
                                    working = false
                                }
                            }
                        },
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = consent, onCheckedChange = { consent = it },
                            enabled = !working)
                        Text(
                            "اجازه می‌دهم فقط جمع درآمد، هزینه و سهم دسته‌ها به ابر ارسال شود؛ " +
                                "نه نام حساب، یادداشت یا ریز تراکنش.",
                            color = JibikStyle.ink, fontSize = 12.sp, lineHeight = 19.sp,
                        )
                    }
                    LiquidGlassButton(
                        text = if (working) "جیبو در حال بررسی..." else "تحلیل ابری",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !working && consent && snapshot.hasData,
                        onClick = {
                            val token = session
                            if (token != null) scope.launch {
                                working = true
                                error = null
                                try {
                                    answer = client.analyze(token, feature, snapshot)
                                } catch (e: JiboAiNetworkException) {
                                    error = e.message
                                    if (error?.contains("منقضی") == true) session = null
                                } finally {
                                    working = false
                                }
                            }
                        },
                    )
                }
                if (working) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp), color = JibikStyle.forest,
                    )
                }
                error?.let { Text(it, color = JibikStyle.coral, fontSize = 12.sp) }
                Text(
                    "پیشنهادها آموزشی هستند؛ جیبو هیچ حساب، بودجه یا تراکنشی را تغییر نمی‌دهد.",
                    color = JibikStyle.muted, fontSize = 11.sp, lineHeight = 18.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !working) {
                Text("بستن", color = JibikStyle.forest)
            }
        },
    )
}

private fun localFinancialHint(feature: JiboAiFeature, snapshot: JiboFinancialSnapshot): String {
    if (!snapshot.hasData) return "هنوز داده‌ای برای این بازه وجود ندارد."
    return when (feature) {
        JiboAiFeature.SPENDING -> {
            val top = snapshot.categories.firstOrNull()?.code
            if (top == null) "هزینه‌ای در این بازه ثبت نشده است."
            else "بیشترین هزینه در دسته «" + when (top) {
                "food" -> "خوراک"
                "housing" -> "مسکن"
                "transport" -> "حمل‌ونقل"
                "bills" -> "قبوض"
                "health" -> "سلامت"
                "education" -> "آموزش"
                "shopping" -> "خرید"
                "entertainment" -> "تفریح"
                else -> "سایر"
            } + "» بوده است."
        }
        JiboAiFeature.BUDGET ->
            if (snapshot.incomeCount > 0 && snapshot.expenseToman > snapshot.incomeToman)
                "در این بازه هزینه بیشتر از درآمد ثبت شده؛ خرج‌های قابل تغییر را مرور کن."
            else "سقف هزینه هفتگی را براساس مخارج واقعی تعیین کن."
        JiboAiFeature.SAVINGS ->
            if (snapshot.incomeToman > snapshot.expenseToman)
                "درآمد از هزینه بیشتر است؛ برای اختلاف یک هدف پس‌انداز مشخص کن."
            else "قبل از هدف جدید، درآمدها و هزینه‌های این بازه را کامل‌تر ثبت کن."
    }
}
