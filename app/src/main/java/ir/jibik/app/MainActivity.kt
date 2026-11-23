package ir.jibik.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.launch

private val Forest = Color(0xFF07574C)
private val Deep = Color(0xFF102D31)
private val Mint = Color(0xFF21C79B)
private val Orange = Color(0xFFFF8A1F)
private val Cream = Color(0xFFFFFAF5)
private val Coral = Color(0xFFE95C50)
private val Paper = Color.White
private val Quiet = Color(0xFF677A78)

private val colors = lightColorScheme(
    primary = Forest, onPrimary = Paper,
    primaryContainer = Color(0xFFDFF3EA), onPrimaryContainer = Forest,
    secondary = Orange, onSecondary = Deep,
    secondaryContainer = Color(0xFFFFEBD4), onSecondaryContainer = Deep,
    background = Cream, onBackground = Deep,
    surface = Paper, onSurface = Deep, surfaceTint = Paper,
    surfaceVariant = Color(0xFFF5F6F3), onSurfaceVariant = Quiet,
    outline = Color(0xFFCBD6D0),
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

private enum class Section(val title: String) {
    HOME("خانه"),
    TRANSACTIONS("تراکنش‌ها"),
    BUDGET("بودجه"),
}

@Composable
private fun JibikApp(store: LocalLedgerStore) {
    var welcomed by remember { mutableStateOf(store.hasFinishedWelcome()) }
    val records = remember { mutableStateListOf<LedgerRecord>() }
    val scope = rememberCoroutineScope()
    var isLoaded by remember { mutableStateOf(false) }
    var loadingFailed by remember { mutableStateOf(false) }
    var retryLoad by remember { mutableIntStateOf(0) }
    var storageError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    LaunchedEffect(store, retryLoad) {
        try {
            val persisted = store.load()
            records.clear()
            records.addAll(persisted)
            loadingFailed = false
        } catch (_: Exception) {
            loadingFailed = true
        } finally {
            isLoaded = true
        }
    }
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

    if (!isLoaded) {
        Box(Modifier.fillMaxSize().background(Cream), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Forest)
        }
        return
    }
    if (loadingFailed) {
        Column(
            Modifier.fillMaxSize().background(Cream).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            ) {
            Text("امکان خواندن اطلاعات مالی وجود ندارد", fontWeight = FontWeight.Bold, color = Deep)
            Spacer(Modifier.height(12.dp))
            Text("برای حفظ اطلاعات قبلی، ثبت تراکنش جدید متوقف شده است.", color = Quiet, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Button(onClick = { isLoaded = false; retryLoad++ }) { Text("تلاش دوباره") }
        }
        return
    }

    Scaffold(
        containerColor = Cream,
        bottomBar = {
            NavigationBar(containerColor = Paper, tonalElevation = 0.dp) {
                Section.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = section == destination,
                        onClick = { section = destination },
                        icon = { JibikNavigationIcon(destination.ordinal, section == destination) },
                        label = { Text(destination.title, fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = Forest,
                            unselectedTextColor = Quiet,
                            indicatorColor = Color(0xFFE3F4EB),
                        ),
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                modifier = Modifier.height(56.dp),
                containerColor = Orange,
                contentColor = Deep,
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("＋  ثبت تراکنش", modifier = Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
                if (!isSaving) {
                    isSaving = true
                    scope.launch {
                        try {
                            // Store on background dispatcher, then update the visible ledger.
                            store.append(record)
                            records.add(0, record)
                            showAdd = false
                        } catch (_: Exception) {
                            storageError = "تراکنش ذخیره نشد؛ اطلاعات قبلی شما محفوظ است."
                        } finally {
                            isSaving = false
                        }
                    }
                }
            },
        )
    }
    if (storageError != null) {
        AlertDialog(
            onDismissRequest = { storageError = null },
            title = { Text("خطا در ثبت اطلاعات") },
            text = { Text(storageError ?: "") },
            confirmButton = { TextButton(onClick = { storageError = null }) { Text("متوجه شدم") } },
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("جیبیک", fontSize = 29.sp, color = Forest, fontWeight = FontWeight.ExtraBold)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(color = Color(0xFFFFEBD8), shape = RoundedCornerShape(36.dp)) {
                JiboMascot(Modifier.padding(20.dp).size(160.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text("سلام! من جیبو هستم", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = Deep)
            Text(
                "حساب‌وکتاب خونه و زندگی رو با هم ساده‌تر می‌کنیم.",
                textAlign = TextAlign.Center, color = Quiet, lineHeight = 27.sp, fontSize = 15.sp,
            )
            Text("ثبت سریع  •  بودجه‌بندی  •  آرامش بیشتر", color = Forest, fontSize = 12.sp)
        }
        Button(
            onClick = onStart, modifier = Modifier.fillMaxWidth().height(58.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Forest),
            shape = RoundedCornerShape(18.dp),
        ) { Text("شروع با جیبیک", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
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
        Text("حساب و کتابت، مرتب و روشن ✨", color = Quiet, fontSize = 14.sp)
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
                    Text("بودجه هزینه‌ها", fontWeight = FontWeight.Bold)
                    Text(money(budget), color = Forest, fontSize = 13.sp)
                }
                LinearProgressIndicator(
                    progress = { (summary.expenseToman.toFloat() / budget.coerceAtLeast(1L)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(9.dp),
                    color = if (summary.expenseToman > budget) Coral else Mint,
                    trackColor = Cream,
                )
                Text("هزینه ثبت‌شده: " + money(summary.expenseToman) + " · در تمام زمان‌ها", color = Quiet, fontSize = 12.sp)
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
        Spacer(Modifier.height(90.dp))
    }
}


@Composable
private fun BrandHeader() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("جیبیک", fontWeight = FontWeight.ExtraBold, fontSize = 27.sp, color = Forest)
            Text("دستیار مالی مهربونت", color = Quiet, fontSize = 13.sp)
        }
        Surface(color = Color(0xFFFFEBD8), shape = RoundedCornerShape(18.dp)) {
            JiboMascot(modifier = Modifier.padding(5.dp).size(51.dp))
        }
    }
}


@Composable
private fun BalanceCard(summary: MoneySummary) {
    var balanceVisible by rememberSaveable { mutableStateOf(true) }
    Card(
        colors = CardDefaults.cardColors(containerColor = Forest),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
    ) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("موجودی ثبت‌شده", color = Color(0xFFCCEEE5), fontSize = 14.sp)
                TextButton(onClick = { balanceVisible = !balanceVisible }) {
                    Text(if (balanceVisible) "مخفی‌کردن" else "نمایش", color = Color(0xFFE7F7EF), fontSize = 12.sp)
                }
            }
            Text(
                if (balanceVisible) money(summary.balanceToman) else "••••••",
                color = Paper,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 29.sp,
            )
            HorizontalDivider(color = Color(0xFF438F7F))
            Text("با ثبت هر خرج و درآمد، تصویر روشن‌تری از پولت داری.", color = Color(0xFFE9F8F3), fontSize = 12.sp, lineHeight = 20.sp)
        }
    }
}


@Composable
private fun MetricCard(title: String, amount: Long, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(9.dp).background(accent, RoundedCornerShape(5.dp)))
                Text(title, color = Quiet, fontSize = 13.sp)
            }
            Text(money(amount), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Deep, lineHeight = 23.sp)
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
        Text("همه درآمدها و هزینه‌هایی که تا امروز ثبت کردی", color = Quiet, fontSize = 14.sp)
        if (records.isEmpty()) EmptyTransactions() else records.forEach { TransactionCard(it) }
        Spacer(Modifier.height(90.dp))
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(color = Color(0xFFFFF0E0), shape = RoundedCornerShape(26.dp)) {
                JiboMascot(Modifier.padding(12.dp).size(78.dp))
            }
            Text("هنوز تراکنشی ثبت نکردی", color = Deep, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(
                "اولین درآمد یا هزینه‌ت رو ثبت کن تا گزارش مالی‌ت شکل بگیره.",
                color = Quiet, textAlign = TextAlign.Center, fontSize = 13.sp, lineHeight = 22.sp,
            )
        }
    }
}


@Composable
private fun Budget(spent: Long, budget: Long, modifier: Modifier, onEdit: () -> Unit) {
    val remaining = budget - spent
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("بودجه", fontWeight = FontWeight.ExtraBold, fontSize = 25.sp, color = Deep)
        Text("برای خرج‌هات سقف تعیین کن و ببین چقدر ازش باقی مونده.", color = Quiet, fontSize = 14.sp)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Forest),
            shape = RoundedCornerShape(26.dp),
        ) {
            Column(Modifier.padding(23.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (remaining >= 0L) "باقی‌مانده از سقف بودجه" else "میزان عبور از سقف بودجه", color = Color(0xFFCCEEE5), fontSize = 14.sp)
                Text(money(if (remaining >= 0L) remaining else -remaining), fontWeight = FontWeight.ExtraBold, fontSize = 29.sp, color = Paper)
                HorizontalDivider(color = Color(0xFF438F7F))
                Text("فعلاً بر اساس تمام هزینه‌های ثبت‌شده محاسبه می‌شود.", color = Color(0xFFE9F8F3), fontSize = 12.sp)
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Paper),
            shape = RoundedCornerShape(22.dp),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("سقف هزینه", color = Quiet)
                    Text(money(budget), color = Deep, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("خرج ثبت‌شده", color = Quiet)
                    Text(money(spent), color = Deep, fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = { (spent.toFloat() / budget.coerceAtLeast(1L)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(9.dp),
                    color = if (spent > budget) Coral else Mint,
                    trackColor = Cream,
                )
                OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Text("ویرایش سقف بودجه")
                }
            }
        }
        Spacer(Modifier.height(90.dp))
    }
}


@Composable
private fun AddTransactionDialog(onDismiss: () -> Unit, onSave: (LedgerRecord) -> Unit) {
    var isExpense by rememberSaveable { mutableStateOf(true) }
    var amount by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("خوراک") }
    var note by rememberSaveable { mutableStateOf("") }
    val numericAmount = parseMoney(amount)
    val suggestedCategories = if (isExpense) {
        listOf("خوراک", "خرید منزل", "حمل‌ونقل", "قبوض", "درمان", "تفریح")
    } else {
        listOf("حقوق", "پاداش", "هدیه", "درآمد جانبی")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ثبت تراکنش", color = Deep, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { isExpense = true; category = "خوراک" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isExpense) Forest else Color(0xFFE9F1EC),
                            contentColor = if (isExpense) Paper else Forest,
                        ),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("هزینه") }
                    Button(
                        onClick = { isExpense = false; category = "حقوق" },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isExpense) Forest else Color(0xFFE9F1EC),
                            contentColor = if (!isExpense) Paper else Forest,
                        ),
                        shape = RoundedCornerShape(14.dp),
                    ) { Text("درآمد") }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("مبلغ (تومان)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                Text("دسته‌های پیشنهادی", color = Quiet, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestedCategories.forEach { option ->
                        FilterChip(
                            selected = category == option,
                            onClick = { category = option },
                            label = { Text(option, fontSize = 12.sp) },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("دسته‌بندی") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("توضیحات (اختیاری)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = numericAmount != null && numericAmount > 0 && category.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
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
            ) { Text("ثبت تراکنش") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
        containerColor = Paper,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(26.dp),
    )
}


@Composable
private fun BudgetDialog(current: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var value by rememberSaveable { mutableStateOf(current.toString()) }
    val parsed = parseMoney(value)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ویرایش بودجه", color = Deep, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("برای خرج‌هات یه سقف مناسب انتخاب کن.", fontSize = 13.sp, color = Quiet)
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("سقف هزینه (تومان)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = parsed != null && parsed > 0,
                onClick = { parsed?.let(onSave) },
                shape = RoundedCornerShape(14.dp),
            ) { Text("ذخیره بودجه") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("لغو") } },
        containerColor = Paper,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(26.dp),
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
