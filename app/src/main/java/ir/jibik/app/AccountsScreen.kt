package ir.jibik.app

import androidx.compose.foundation.background
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

private fun accountMoney(amount: Long) =
    NumberFormat.getNumberInstance(Locale("fa", "IR")).format(amount) + " تومان"

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
) {
    Column(
        modifier.fillMaxSize().background(WarmBackground).verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("حساب‌های من", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                Text("موجودی هر کارت و کیف پول، جدا و شفاف", fontSize = 13.sp, color = Subtle)
            }
            JibikBrandLogo(Modifier.size(46.dp))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onAdd, modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)) {
                Text("＋ حساب جدید")
            }
            OutlinedButton(
                onClick = onTransfer,
                enabled = accounts.size > 1,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("انتقال بین حساب‌ها")
            }
        }
        OutlinedButton(
            onClick = onGoals,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
        ) { Text("اهداف پس‌انداز و برنامه‌های مالی") }
        if (accounts.size < 2) {
            Text("برای انتقال پول، اول یک حساب دیگر اضافه کن.",
                fontSize = 12.sp, color = Subtle)
        }
        accounts.forEachIndexed { index, account ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = White),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                Modifier.size(38.dp).background(
                                    if (index % 2 == 0) Color(0xFFE0F3ED) else Color(0xFFFFEBD9),
                                    RoundedCornerShape(12.dp),
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (account.id == "wallet") "◉" else "▤",
                                    fontSize = 20.sp, color = Green)
                            }
                            Column {
                                Text(account.title, fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp, color = Ink)
                                Text("حساب شخصی", fontSize = 11.sp, color = Subtle)
                            }
                        }
                        Text(if (account.id == "wallet") "اصلی" else "فعال",
                            fontSize = 11.sp, color = Green)
                    }
                    HorizontalDivider(color = Color(0xFFF1EEE8))
                    Text("موجودی ثبت‌شده", color = Subtle, fontSize = 12.sp)
                    Text(accountMoney(balances[account.id] ?: account.openingBalanceToman),
                        fontWeight = FontWeight.ExtraBold, fontSize = 23.sp, color = Green)
                }
            }
        }
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
internal fun CreateAccountDialog(
    existing: List<Account>,
    onDismiss: () -> Unit,
    onSave: (Account) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var opening by rememberSaveable { mutableStateOf("0") }
    val amount = parseAccountAmount(opening)
    val cleanTitle = title.trim()
    val valid = cleanTitle.isNotBlank() && cleanTitle.length <= 48 &&
        existing.none { it.title == cleanTitle } && amount != null && amount >= 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        shape = RoundedCornerShape(26.dp),
        title = { Text("حساب مالی جدید", color = Ink, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("برای هر کارت بانکی، کیف نقدی یا پس‌انداز حساب جدا داشته باش.",
                    fontSize = 13.sp, color = Subtle)
                OutlinedTextField(
                    value = title, onValueChange = { title = it.take(48) },
                    label = { Text("نام حساب") },
                    placeholder = { Text("مثلاً کارت بانک یا پس‌انداز") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                OutlinedTextField(
                    value = opening, onValueChange = { opening = it },
                    label = { Text("موجودی اولیه (تومان)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                Text("موجودی اولیه درآمد محسوب نمی‌شود؛ فقط مقدار شروع حساب است.",
                    fontSize = 12.sp, color = Subtle)
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = {
                onSave(Account(UUID.randomUUID().toString(), cleanTitle, amount ?: 0L))
            }) { Text("ایجاد حساب") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
