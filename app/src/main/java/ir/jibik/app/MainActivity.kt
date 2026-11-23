package ir.jibik.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibik.core.finance.Account
import ir.jibik.core.finance.FinanceEngine
import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord
import ir.jibik.core.finance.MoneySummary
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

private val Forest = Color(0xFF07574C)
private val Deep = Color(0xFF102D31)
private val Mint = Color(0xFF21C79B)
private val Orange = Color(0xFFFF8A1F)
private val Cream = Color(0xFFFFFAF5)
private val Coral = Color(0xFFE95C50)
private val Paper = Color.White
private val Quiet = Color(0xFF677A78)

private val colors = lightColorScheme(
    primary = Forest, onPrimary = Paper, background = Cream, onBackground = Deep,
    surface = Paper, onSurface = Deep, secondary = Orange,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = LocalLedgerStore(applicationContext)
        setContent {
            MaterialTheme(colorScheme = colors) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    JibikApp(store)
                }
            }
        }
    }
}

private enum class Section(val title: String, val glyph: String) {
    HOME("خانه", "⌂"),
    TRANSACTIONS("تراکنش‌ها", "≡"),
    BUDGET("بودجه", "◉"),
}

@Composable
private fun JibikApp(store: LocalLedgerStore) {
    var welcomed by remember { mutableStateOf(store.hasFinishedWelcome()) }
    val records = remember { mutableStateListOf<LedgerRecord>().also { it.addAll(store.load()) } }
    val accounts = remember { listOf(Account("wallet", "کیف پول جیبیک")) }
    val summary = FinanceEngine.summary(accounts, records.toList())
    var section by rememberSaveable { mutableStateOf(Section.HOME) }
    var showAdd by remember { mutableStateOf(false) }
    var budget by remember { mutableStateOf(store.budgetToman()) }
    var showEditBudget by remember { mutableStateOf(false) }

    if (!welcomed) {
        WelcomeScreen(onStart = {
            store.finishWelcome()
            welcomed = true
        })
        return
    }

    Scaffold(
        containerColor = Cream,
        bottomBar = {
            NavigationBar(containerColor = Paper) {
                Section.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = section == destination,
                        onClick = { section = destination },
                        icon = { Text(destination.glyph, fontSize = 22.sp) },
                        label = { Text(destination.title) },
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = Orange,
                contentColor = Deep,
                shape = RoundedCornerShape(22.dp),
            ) {
                Text("＋  ثبت تراکنش", modifier = Modifier.padding(horizontal = 18.dp), fontWeight = FontWeight.Bold)
            }
        },
    ) { insets ->
        when (section) {
            Section.HOME -> Home(
                summary = summary,
                records = records,
                budget = budget,
                modifier = Modifier.padding(insets),
                onAllTransactions = { section = Section.TRANSACTIONS },
                onBudget = { section = Section.BUDGET },
            )
            Section.TRANSACTIONS -> Transactions(
                records = records, modifier = Modifier.padding(insets),
            )
            Section.BUDGET -> Budget(
                spent = summary.expenseToman,
                budget = budget,
                modifier = Modifier.padding(insets),
                onEdit = { showEditBudget = true },
            )
        }
    }

    if (showAdd) {
        AddTransactionDialog(
            onDismiss = { showAdd = false },
            onSave = { record ->
                // Persist first so UI never presents an uncommitted financial entry.
                store.append(record)
                records.add(0, record)
                showAdd = false
            },
        )
    }
    if (showEditBudget) {
        BudgetDialog(
            current = budget,
            onDismiss = { showEditBudget = false },
            onSave = { newValue ->
                store.updateBudgetToman(newValue)
                budget = newValue
                showEditBudget = false
            },
        )
    }
}

@Composable
private fun WelcomeScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Cream).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("جیبیک", fontSize = 27.sp, color = Forest, fontWeight = FontWeight.ExtraBold)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Temporary glyph only; approved fox illustrations will be integrated as optimized WebP.
            Text("🦊", fontSize = 106.sp)
            Spacer(Modifier.height(18.dp))
            Text("سلام! من جیبو هستم", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Deep)
            Spacer(Modifier.height(12.dp))
            Text(
                "خرج‌هات رو با خیال راحت ثبت کن، برای آینده‌ات برنامه بریز و پولت رو بهتر بشناس.",
                textAlign = TextAlign.Center, color = Quiet, lineHeight = 28.sp,
            )
        }
        Button(
            onClick = onStart, modifier = Modifier.fillMaxWidth().height(58.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Forest),
            shape = RoundedCornerShape(18.dp),
        ) { Text("بزن بریم!", fontSize = 18.sp) }
    }
}

@Composable
private fun Home(
    summary: MoneySummary,
    records: List<LedgerRecord>,
    budget: Long,
    modifier: Modifier,
    onAllTransactions: () -> Unit,
    onBudget: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(17.dp),
    ) {
        BrandHeader()
        Text("حساب و کتابت، مرتب و روشن ✨", color = Quiet)
        BalanceCard(summary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("درآمد کل", summary.incomeToman, Mint, Modifier.weight(1f))
            MetricCard("هزینه کل", summary.expenseToman, Coral, Modifier.weight(1f))
        }
        Card(
            onClick = onBudget,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Paper),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("بودجه ثبت‌شده", fontWeight = FontWeight.Bold)
                    Text(money(budget), color = Forest, fontSize = 13.sp)
                }
                LinearProgressIndicator(
                    progress = { (summary.expenseToman.toFloat() / budget.coerceAtLeast(1L)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(9.dp),
                    color = if (summary.expenseToman > budget) Coral else Mint,
                    trackColor = Cream,
                )
                Text("این نسخه مجموع هزینه‌های ثبت‌شده را نمایش می‌دهد.", color = Quiet, fontSize = 12.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("تراکنش‌های اخیر", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            TextButton(onClick = onAllTransactions) { Text("مشاهده همه") }
        }
        if (records.isEmpty()) {
            EmptyTransactions()
        } else {
            records.take(4).forEach { TransactionCard(it) }
        }
        Spacer(Modifier.height(65.dp))
    }
}

@Composable
private fun BrandHeader() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("جیبیک", fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = Forest)
            Text("دستیار مالی مهربونت", color = Quiet, fontSize = 13.sp)
        }
        Surface(color = Color(0xFFFFF0DD), shape = RoundedCornerShape(20.dp)) {
            Text("🦊", modifier = Modifier.padding(10.dp), fontSize = 32.sp)
        }
    }
}

@Composable
private fun BalanceCard(summary: MoneySummary) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Forest),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(Modifier.padding(23.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Text("موجودی کل", color = Color(0xFFCCEEE5), fontSize = 14.sp)
            Text(money(summary.balanceToman), color = Paper, fontWeight = FontWeight.ExtraBold, fontSize = 29.sp)
            HorizontalDivider(color = Color(0xFF438F7F))
            Text("هر هزینه کوچیک هم ارزش ثبت کردن داره 🌱", color = Color(0xFFE9F8F3), fontSize = 13.sp)
        }
    }
}

@Composable
private fun MetricCard(title: String, amount: Long, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(10.dp).background(accent, RoundedCornerShape(5.dp)))
            Text(title, color = Quiet, fontSize = 13.sp)
            Text(money(amount), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Deep)
        }
    }
}

@Composable
private fun Transactions(records: List<LedgerRecord>, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("تراکنش‌ها", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = Deep)
        Text("همه درآمدها و هزینه‌هایی که ثبت کردی", color = Quiet)
        if (records.isEmpty()) EmptyTransactions() else records.forEach { TransactionCard(it) }
        Spacer(Modifier.height(68.dp))
    }
}

@Composable
private fun TransactionCard(record: LedgerRecord) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Paper),
        shape = RoundedCornerShape(17.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(record.note.ifBlank { record.category.ifBlank { "تراکنش" } }, fontWeight = FontWeight.Bold)
                Text(
                    when (record.kind) {
                        LedgerKind.EXPENSE -> "هزینه · " + record.category
                        LedgerKind.INCOME -> "درآمد · " + record.category
                        LedgerKind.TRANSFER -> "انتقال"
                    },
                    color = Quiet, fontSize = 12.sp,
                )
            }
            Text(
                (if (record.kind == LedgerKind.EXPENSE) "− " else "+ ") + money(record.amountToman),
                fontWeight = FontWeight.Bold,
                color = if (record.kind == LedgerKind.EXPENSE) Coral else Forest,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun EmptyTransactions() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("🦊", fontSize = 42.sp)
        Text("هنوز تراکنشی ثبت نکردی", color = Deep, fontWeight = FontWeight.Bold)
        Text("با دکمه نارنجی، اولین درآمد یا خرجت رو ثبت کن.", color = Quiet, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Budget(spent: Long, budget: Long, modifier: Modifier, onEdit: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("بودجه", fontWeight = FontWeight.ExtraBold, fontSize = 25.sp)
        Text("در نسخه اولیه، بودجه روی کل تراکنش‌های ثبت‌شده محاسبه می‌شود؛ بودجه ماه شمسی در فاز بعد.", color = Quiet)
        BalanceCard(MoneySummary(budget - spent, 0, spent))
        Card(colors = CardDefaults.cardColors(containerColor = Paper), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("سقف هزینه: " + money(budget))
                Text("هزینه ثبت‌شده: " + money(spent))
                LinearProgressIndicator(
                    progress = { (spent.toFloat() / budget.coerceAtLeast(1L)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = if (spent > budget) Coral else Mint,
                    trackColor = Cream,
                )
                OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                    Text("ویرایش سقف بودجه")
                }
            }
        }
    }
}

@Composable
private fun AddTransactionDialog(onDismiss: () -> Unit, onSave: (LedgerRecord) -> Unit) {
    var isExpense by rememberSaveable { mutableStateOf(true) }
    var amount by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("خوراک") }
    var note by rememberSaveable { mutableStateOf("") }
    val numericAmount = parseMoney(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ثبت تراکنش جدید", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { isExpense = true; category = "خوراک" }, colors = ButtonDefaults.buttonColors(containerColor = if (isExpense) Forest else Quiet)) { Text("هزینه") }
                    Button(onClick = { isExpense = false; category = "حقوق" }, colors = ButtonDefaults.buttonColors(containerColor = if (!isExpense) Forest else Quiet)) { Text("درآمد") }
                }
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text("مبلغ (تومان)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = category, onValueChange = { category = it },
                    label = { Text("دسته‌بندی") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("توضیحات (اختیاری)") }, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = numericAmount != null && numericAmount > 0,
                onClick = {
                    val value = numericAmount ?: return@Button
                    onSave(
                        LedgerRecord(
                            id = UUID.randomUUID().toString(),
                            kind = if (isExpense) LedgerKind.EXPENSE else LedgerKind.INCOME,
                            amountToman = value,
                            fromAccountId = if (isExpense) "wallet" else null,
                            toAccountId = if (isExpense) null else "wallet",
                            category = category.trim(),
                            note = note.trim(),
                            timestampMillis = System.currentTimeMillis(),
                        )
                    )
                },
            ) { Text("ثبت") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
        shape = RoundedCornerShape(24.dp),
    )
}

@Composable
private fun BudgetDialog(current: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var value by rememberSaveable { mutableStateOf(current.toString()) }
    val parsed = parseMoney(value)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ویرایش بودجه") },
        text = {
            OutlinedTextField(
                value = value, onValueChange = { value = it },
                label = { Text("بودجه (تومان)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(enabled = parsed != null && parsed > 0, onClick = { parsed?.let(onSave) }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("لغو") } },
    )
}

private fun parseMoney(raw: String): Long? {
    val normalized = raw.trim().map { ch ->
        when (ch) {
            in '۰'..'۹' -> '0' + (ch - '۰')
            in '٠'..'٩' -> '0' + (ch - '٠')
            else -> ch
        }
    }.joinToString("").filterNot { it == ',' || it == '٬' || it == ' ' }
    return normalized.toLongOrNull()
}

private fun money(amount: Long): String =
    NumberFormat.getNumberInstance(Locale("fa", "IR")).format(amount) + " تومان"
