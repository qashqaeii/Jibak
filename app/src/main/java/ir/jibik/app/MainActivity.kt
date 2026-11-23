package ir.jibik.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibik.core.finance.Account
import ir.jibik.core.finance.FinanceEngine
import ir.jibik.core.finance.ExpenseCategoryCatalog
import ir.jibik.core.finance.LedgerKind
import ir.jibik.core.finance.LedgerRecord
import ir.jibik.core.finance.MoneySummary
import ir.jibik.core.finance.PeriodTotals
import ir.jibik.core.finance.CategoryExpense
import ir.jibik.core.finance.categoryExpenses
import ir.jibik.core.finance.periodTotals
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
    REPORTS("گزارش‌ها"),
    ACCOUNTS("حساب‌ها"),
}

@Composable
private fun JibikApp(store: LocalLedgerStore) {
    var welcomed by remember { mutableStateOf(store.hasFinishedWelcome()) }
    val records = remember { mutableStateListOf<LedgerRecord>() }
    val accounts = remember { mutableStateListOf<Account>() }
    val goals = remember { mutableStateListOf<SavingsGoalEntity>() }
    val scope = rememberCoroutineScope()
    var isLoaded by remember { mutableStateOf(false) }
    var loadingFailed by remember { mutableStateOf(false) }
    var retryLoad by remember { mutableIntStateOf(0) }
    var storageError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    LaunchedEffect(store, retryLoad) {
        try {
            val persisted = store.load()
            val loadedAccounts = store.loadAccounts()
            val persistedGoals = store.loadSavingsGoals()
            // Account definitions first, so the ledger can never reference a missing account.
            accounts.clear()
            accounts.addAll(loadedAccounts)
            goals.clear()
            goals.addAll(persistedGoals)
            records.clear()
            records.addAll(persisted)
            loadingFailed = false
        } catch (_: Exception) {
            loadingFailed = true
        } finally {
            isLoaded = true
        }
    }
    val summary = FinanceEngine.summary(accounts, records.toList())
    val currentMonth = currentJalaliMonth()
    val monthTotals = periodTotals(
        records.toList(), currentMonth.startMillis, currentMonth.endExclusiveMillis
    )
    val categorySpending = categoryExpenses(
        records.toList(), currentMonth.startMillis, currentMonth.endExclusiveMillis
    )
    var section by rememberSaveable { mutableStateOf(Section.HOME) }
    var showAdd by remember { mutableStateOf(false) }
    var addKind by remember { mutableStateOf(LedgerKind.EXPENSE) }
    var showCreateAccount by remember { mutableStateOf(false) }
    var showGoals by rememberSaveable { mutableStateOf(false) }
    var showNewGoal by remember { mutableStateOf(false) }
    var showItemizedPurchase by remember { mutableStateOf(false) }
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
            if (section != Section.HOME) {
                FloatingActionButton(
                    onClick = { addKind = LedgerKind.EXPENSE; showAdd = true },
                    modifier = Modifier.height(54.dp),
                    containerColor = Orange,
                    contentColor = Deep,
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("＋  ثبت تراکنش", modifier = Modifier.padding(horizontal = 14.dp),
                        fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        },
    ) { insets ->
        when (section) {
            Section.HOME -> Home(
                summary = summary,
                records = records,
                budget = budget,
                monthTotals = monthTotals,
                monthTitle = currentMonth.title,
                categorySpending = categorySpending,
                modifier = Modifier.padding(insets),
                onAllTransactions = { section = Section.TRANSACTIONS },
                onBudget = { section = Section.BUDGET },
                onItemizedPurchase = { showItemizedPurchase = true },
                onNewExpense = { addKind = LedgerKind.EXPENSE; showAdd = true },
                onNewIncome = { addKind = LedgerKind.INCOME; showAdd = true },
            )
            Section.TRANSACTIONS -> Transactions(
                records = records, modifier = Modifier.padding(insets),
            )
            Section.BUDGET -> Budget(
                spent = monthTotals.expenseToman,
                budget = budget,
                monthTitle = currentMonth.title,
                modifier = Modifier.padding(insets),
                onEdit = { showEditBudget = true },
            )
            Section.REPORTS -> ReportsScreen(
                records = records,
                modifier = Modifier.padding(insets),
            )
            Section.ACCOUNTS -> if (showGoals) {
                Box(Modifier.padding(insets)) {
                    GoalsScreen(
                        goals = goals,
                        accounts = accounts,
                        balances = FinanceEngine.balances(accounts, records.toList()),
                        onBack = { showGoals = false },
                        onAdd = { showNewGoal = true },
                    )
                }
            } else AccountsScreen(
                accounts = accounts,
                balances = FinanceEngine.balances(accounts, records.toList()),
                modifier = Modifier.padding(insets),
                onAdd = { showCreateAccount = true },
                onTransfer = { addKind = LedgerKind.TRANSFER; showAdd = true },
                onGoals = { showGoals = true },
            )
        }
    }

    if (showNewGoal) {
        NewGoalDialog(
            accounts = accounts,
            goals = goals,
            onDismiss = { if (!isSaving) showNewGoal = false },
            onSave = { newGoal ->
                if (!isSaving) {
                    isSaving = true
                    scope.launch {
                        try {
                            store.addSavingsGoal(newGoal)
                            goals.clear()
                            goals.addAll(store.loadSavingsGoals())
                            showNewGoal = false
                        } catch (_: Exception) {
                            storageError = "هدف پس‌انداز ذخیره نشد؛ اطلاعات قبلی محفوظ است."
                        } finally {
                            isSaving = false
                        }
                    }
                }
            },
        )
    }
    if (showItemizedPurchase) {
        ItemizedPurchaseDialog(
            accounts = accounts,
            onDismiss = { if (!isSaving) showItemizedPurchase = false },
            onSave = { purchase, lines ->
                if (!isSaving) {
                    isSaving = true
                    scope.launch {
                        try {
                            store.appendItemizedPurchase(purchase, lines)
                            records.add(0, purchase)
                            showItemizedPurchase = false
                        } catch (_: Exception) {
                            storageError = "خرید ذخیره نشد؛ هیچ مبلغی از حساب کسر نشده است."
                        } finally {
                            isSaving = false
                        }
                    }
                }
            },
        )
    }
    if (showCreateAccount) {
        CreateAccountDialog(
            existing = accounts,
            onDismiss = { showCreateAccount = false },
            onSave = { newAccount ->
                if (!isSaving) {
                    isSaving = true
                    scope.launch {
                        try {
                            store.addAccount(newAccount)
                            accounts.clear()
                            accounts.addAll(store.loadAccounts())
                            showCreateAccount = false
                        } catch (_: Exception) {
                            storageError = "حساب جدید ذخیره نشد؛ اطلاعات قبلی محفوظ است."
                        } finally {
                            isSaving = false
                        }
                    }
                }
            },
        )
    }
    if (showAdd) {
        AddTransactionDialog(
            accounts = accounts,
            initialKind = addKind,
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
            JibikBrandLogo(Modifier.size(52.dp))
            Spacer(Modifier.size(8.dp))
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
    monthTotals: PeriodTotals,
    monthTitle: String,
    categorySpending: List<CategoryExpense>,
    modifier: Modifier,
    onAllTransactions: () -> Unit,
    onBudget: () -> Unit,
    onItemizedPurchase: () -> Unit,
    onNewExpense: () -> Unit,
    onNewIncome: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        BrandHeader()
        BalanceCard(summary)
        JibikSectionTitle("خلاصه " + monthTitle, "درآمد و هزینه همین ماه")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("درآمد", monthTotals.incomeToman, Mint, Modifier.weight(1f))
            MetricCard("هزینه", monthTotals.expenseToman, Coral, Modifier.weight(1f))
        }
        JibikSectionTitle("ثبت سریع", "کارهای روزمره، بدون فرم‌های طولانی")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            JibikQuickAction(
                title = "ثبت هزینه", subtitle = "مخارج روزانه",
                symbol = "−", accent = Coral,
                background = Color(0xFFFFEDE9), onClick = onNewExpense,
                modifier = Modifier.weight(1f),
            )
            JibikQuickAction(
                title = "ثبت درآمد", subtitle = "دریافتی‌ها",
                symbol = "+", accent = Forest,
                background = Color(0xFFE9F6EF), onClick = onNewIncome,
                modifier = Modifier.weight(1f),
            )
            JibikQuickAction(
                title = "خرید منزل", subtitle = "چند قلم کالا",
                symbol = "≡", accent = Color(0xFFBC620A),
                background = Color(0xFFFFF0DD), onClick = onItemizedPurchase,
                modifier = Modifier.weight(1f),
            )
        }
        Card(
            onClick = onBudget,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Paper),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(17.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("بودجه " + monthTitle, fontWeight = FontWeight.ExtraBold,
                            color = Deep, fontSize = 16.sp)
                        Text("مدیریت سقف هزینه", color = Quiet, fontSize = 11.sp)
                    }
                    Text(money(budget), color = Forest, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val used = (monthTotals.expenseToman.toDouble() /
                    budget.coerceAtLeast(1L)).toFloat().coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { used },
                    modifier = Modifier.fillMaxWidth().height(9.dp),
                    color = if (monthTotals.expenseToman > budget) Coral else Mint,
                    trackColor = Color(0xFFEAF2EC),
                )
                val remaining = budget - monthTotals.expenseToman
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (remaining >= 0) "باقی‌مانده" else "بیش از بودجه",
                        color = Quiet, fontSize = 12.sp,
                    )
                    Text(
                        money(if (remaining >= 0) remaining else -remaining),
                        color = if (remaining >= 0) Forest else Coral,
                        fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        SpendingByCategory(
            categories = categorySpending,
            total = monthTotals.expenseToman,
            monthTitle = monthTitle,
            onClick = onAllTransactions,
        )
        JibikSectionTitle(
            title = "آخرین تراکنش‌ها",
            subtitle = "مرور سریع فعالیت‌های مالی",
            trailing = {
                TextButton(onClick = onAllTransactions) {
                    Text("همه", fontSize = 12.sp)
                }
            },
        )
        if (records.isEmpty()) EmptyTransactions()
        else records.take(3).forEach { TransactionCard(it) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SpendingByCategory(
    categories: List<CategoryExpense>,
    total: Long,
    monthTitle: String,
    onClick: () -> Unit,
) {
    val palette = listOf(Forest, Orange, Mint, Coral)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Paper),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("خرج‌ها کجا رفتن؟", color = Deep, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Text(monthTitle, color = Quiet, fontSize = 12.sp)
            }
            if (categories.isEmpty()) {
                Text("هنوز هزینه‌ای در این ماه ثبت نشده.", color = Quiet, fontSize = 13.sp)
            } else {
                categories.take(3).forEachIndexed { index, entry ->
                    val progress = (entry.amountToman.toDouble() / total.coerceAtLeast(1L)).toFloat().coerceIn(0f, 1f)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(entry.category, fontSize = 13.sp, color = Deep, fontWeight = FontWeight.Medium)
                            Text(money(entry.amountToman), fontSize = 12.sp, color = Quiet)
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = palette[index % palette.size],
                            trackColor = Cream,
                        )
                    }
                }
                if (categories.size > 3) {
                    Text(
                        "${categories.size - 3} دسته دیگر هم در این ماه هزینه داشته‌اند.",
                        fontSize = 12.sp, color = Quiet,
                    )
                }
            }
        }
    }
}

@Composable
private fun BrandHeader() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            JibikBrandLogo(Modifier.size(50.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("جیبیک", fontWeight = FontWeight.ExtraBold, fontSize = 25.sp, color = Forest)
                Text("دستیار مالی مهربونت", color = Quiet, fontSize = 12.sp)
            }
        }
        JiboMascot(Modifier.size(54.dp))
    }
}


@Composable
private fun BalanceCard(summary: MoneySummary) {
    var balanceVisible by rememberSaveable { mutableStateOf(true) }
    JibikHero(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("مجموع موجودی حساب‌ها", color = Color(0xFFDCF3EA), fontSize = 13.sp)
                Text("دارایی ثبت‌شده تا امروز", color = Color(0xFFB9DDD1), fontSize = 11.sp)
            }
            TextButton(onClick = { balanceVisible = !balanceVisible }) {
                Text(
                    if (balanceVisible) "مخفی‌کردن" else "نمایش",
                    color = Color.White, fontSize = 12.sp,
                )
            }
        }
        Text(
            text = if (balanceVisible) money(summary.balanceToman) else "••••••",
            color = Paper,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 27.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.18f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("همه حساب‌های شخصی", color = Color(0xFFE4F4ED), fontSize = 11.sp)
            Text("به‌روزرسانی با هر تراکنش", color = Color(0xFFE4F4ED), fontSize = 11.sp)
        }
    }
}

@Composable
private fun MetricCard(title: String, amount: Long, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = Paper),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(9.dp).background(accent, RoundedCornerShape(5.dp)))
                Text(title + " ماه", color = Quiet, fontSize = 12.sp)
            }
            Text(
                money(amount),
                fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                color = Deep, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Transactions(records: List<LedgerRecord>, modifier: Modifier) {
    var search by rememberSaveable { mutableStateOf("") }
    var kindFilter by rememberSaveable { mutableStateOf("همه") }
    val filtered = records.filter { record ->
        val filterMatches = when (kindFilter) {
            "درآمد" -> record.kind == LedgerKind.INCOME
            "هزینه" -> record.kind == LedgerKind.EXPENSE
            "انتقال" -> record.kind == LedgerKind.TRANSFER
            else -> true
        }
        val searchMatches = search.isBlank() ||
            record.note.contains(search.trim(), ignoreCase = true) ||
            record.category.contains(search.trim(), ignoreCase = true) ||
            record.amountToman.toString().contains(search.trim())
        filterMatches && searchMatches
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 98.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            JibikSectionTitle(
                "تراکنش‌ها",
                "تمام درآمدها، هزینه‌ها و انتقال‌های ثبت‌شده",
            )
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("جستجو در تراکنش‌ها") },
                placeholder = { Text("نام، دسته‌بندی یا مبلغ") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("همه", "هزینه", "درآمد", "انتقال").forEach { option ->
                    FilterChip(
                        selected = kindFilter == option,
                        onClick = { kindFilter = option },
                        label = { Text(option, fontSize = 12.sp) },
                        shape = RoundedCornerShape(14.dp),
                    )
                }
            }
        }
        item {
            Text(
                "${filtered.size} تراکنش",
                color = Forest, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            )
        }
        if (filtered.isEmpty()) {
            item {
                if (records.isEmpty()) EmptyTransactions()
                else JibikSurfaceCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        JiboMascot(Modifier.size(58.dp))
                        Column(Modifier.weight(1f)) {
                            Text("چیزی پیدا نشد", fontWeight = FontWeight.Bold, color = Deep)
                            Text("جستجو یا فیلترت رو تغییر بده.", fontSize = 12.sp, color = Quiet)
                        }
                    }
                    TextButton(onClick = { search = ""; kindFilter = "همه" }) {
                        Text("پاک‌کردن فیلترها")
                    }
                }
            }
        } else {
            items(items = filtered, key = { it.id }) { record ->
                TransactionCard(record)
            }
        }
    }
}

@Composable
private fun TransactionCard(record: LedgerRecord) {
    val expense = record.kind == LedgerKind.EXPENSE
    val transfer = record.kind == LedgerKind.TRANSFER
    val highlight = when {
        expense -> Coral
        transfer -> Color(0xFFB26A12)
        else -> Forest
    }
    val tint = when {
        expense -> Color(0xFFFFEEEB)
        transfer -> Color(0xFFFFF0DD)
        else -> Color(0xFFE9F6EF)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Paper),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).background(tint, RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        expense -> "−"
                        transfer -> "⇄"
                        else -> "+"
                    },
                    color = highlight, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                )
            }
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    record.note.ifBlank {
                        record.category.ifBlank { if (transfer) "انتقال بین حساب‌ها" else "تراکنش" }
                    },
                    fontWeight = FontWeight.Bold, color = Deep,
                    fontSize = 13.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when (record.kind) {
                        LedgerKind.EXPENSE -> "هزینه · " + record.category
                        LedgerKind.INCOME -> "درآمد · " + record.category
                        LedgerKind.TRANSFER -> "انتقال بین حساب‌ها"
                    },
                    color = Quiet, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                (when (record.kind) {
                    LedgerKind.EXPENSE -> "− "
                    LedgerKind.INCOME -> "+ "
                    LedgerKind.TRANSFER -> "⇄ "
                }) + money(record.amountToman),
                fontWeight = FontWeight.ExtraBold,
                color = highlight,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(0.88f),
                textAlign = TextAlign.End,
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
private fun Budget(
    spent: Long,
    budget: Long,
    monthTitle: String,
    modifier: Modifier,
    onEdit: () -> Unit,
) {
    val remaining = budget - spent
    val usage = (spent.toDouble() / budget.coerceAtLeast(1L).toDouble()).toFloat()
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        JibikSectionTitle(
            title = "بودجه‌بندی",
            subtitle = "سقف هزینه در " + monthTitle,
        )
        JibikHero(Modifier.fillMaxWidth()) {
            Text(
                if (remaining >= 0L) "باقی‌مانده بودجه" else "میزان عبور از بودجه",
                color = Color(0xFFDCF3EA), fontSize = 13.sp,
            )
            Text(
                money(if (remaining >= 0L) remaining else -remaining),
                fontSize = 28.sp, color = Paper, fontWeight = FontWeight.ExtraBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            HorizontalDivider(color = Paper.copy(alpha = 0.18f))
            Text(
                "براساس تراکنش‌های واقعی ثبت‌شده در " + monthTitle,
                color = Color(0xFFE3F5ED), fontSize = 12.sp,
            )
        }
        JibikSurfaceCard {
            JibikSectionTitle("وضعیت مصرف بودجه")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("سقف تعیین‌شده", color = Quiet, fontSize = 13.sp)
                Text(
                    money(budget), color = Deep, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("هزینه‌های این ماه", color = Quiet, fontSize = 13.sp)
                Text(
                    money(spent), color = Deep, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LinearProgressIndicator(
                progress = { usage.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(10.dp),
                color = if (usage > 1f) Coral else if (usage >= 0.85f) Orange else Mint,
                trackColor = Color(0xFFE9F2EC),
            )
            Text(
                when {
                    usage > 1f -> "بودجه این ماه تمام شده؛ می‌تونی سقف رو بازبینی کنی."
                    usage >= 0.85f -> "به سقف بودجه نزدیک شدی. خرج‌های پیش رو رو بررسی کن."
                    spent == 0L -> "با اولین هزینه، پیشرفت بودجه اینجا نمایش داده می‌شه."
                    else -> "تا اینجا هزینه‌ها در محدوده بودجه‌ات هستند."
                },
                fontSize = 12.sp, color = Quiet, lineHeight = 20.sp,
            )
            OutlinedButton(
                onClick = onEdit, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("ویرایش سقف بودجه", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun AddTransactionDialog(
    accounts: List<Account>,
    initialKind: LedgerKind,
    onDismiss: () -> Unit,
    onSave: (LedgerRecord) -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf(initialKind) }
    var amount by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("خوراک") }
    var note by rememberSaveable { mutableStateOf("") }
    var sourceId by rememberSaveable { mutableStateOf(accounts.first().id) }
    var targetId by rememberSaveable { mutableStateOf(accounts.getOrNull(1)?.id ?: accounts.first().id) }
    val numericAmount = parseMoney(amount)
    val sourceValid = accounts.any { it.id == sourceId }
    val targetValid = accounts.any { it.id == targetId }
    val canSave = numericAmount != null && numericAmount > 0L &&
        sourceValid && (kind != LedgerKind.TRANSFER ||
            (accounts.size > 1 && targetValid && sourceId != targetId)) &&
        (kind == LedgerKind.TRANSFER || category.isNotBlank())
    val suggestedCategories = if (kind == LedgerKind.INCOME) {
        listOf("حقوق", "پاداش", "هدیه", "درآمد جانبی")
    } else {
        listOf("خوراک", "خرید منزل", "حمل‌ونقل", "قبوض", "درمان", "تفریح")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(
            if (kind == LedgerKind.TRANSFER) "انتقال پول" else "ثبت تراکنش",
            color = Deep, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp,
        ) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        LedgerKind.EXPENSE to "هزینه",
                        LedgerKind.INCOME to "درآمد",
                        LedgerKind.TRANSFER to "انتقال",
                    ).forEach { (option, label) ->
                        Button(
                            onClick = { kind = option; category = if (option == LedgerKind.INCOME) "حقوق" else "خوراک" },
                            modifier = Modifier.weight(1f),
                            enabled = option != LedgerKind.TRANSFER || accounts.size > 1,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (kind == option) Forest else Color(0xFFE9F1EC),
                                contentColor = if (kind == option) Paper else Forest,
                            ),
                            shape = RoundedCornerShape(13.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        ) { Text(label, fontSize = 12.sp, maxLines = 1) }
                    }
                }
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it },
                    label = { Text("مبلغ (تومان)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
                Text(
                    if (kind == LedgerKind.INCOME) "حساب دریافت‌کننده" else "برداشت از حساب",
                    color = Quiet, fontSize = 12.sp,
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    accounts.forEach { account ->
                        FilterChip(
                            selected = sourceId == account.id,
                            onClick = { sourceId = account.id },
                            label = { Text(account.title, fontSize = 12.sp, maxLines = 1) },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
                if (kind == LedgerKind.TRANSFER) {
                    Text("انتقال به حساب", color = Quiet, fontSize = 12.sp)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        accounts.filter { it.id != sourceId }.forEach { account ->
                            FilterChip(
                                selected = targetId == account.id,
                                onClick = { targetId = account.id },
                                label = { Text(account.title, fontSize = 12.sp, maxLines = 1) },
                                shape = RoundedCornerShape(12.dp),
                            )
                        }
                    }
                    Text("انتقال، درآمد یا هزینه محسوب نمی‌شود.", color = Quiet, fontSize = 12.sp)
                } else {
                    Text("دسته‌های پیشنهادی", color = Quiet, fontSize = 12.sp)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
                    if (kind == LedgerKind.EXPENSE) {
                        Text("جزئیات خرید منزل", color = Quiet, fontSize = 12.sp)
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ExpenseCategoryCatalog.leaves()
                                .filter { it.ids.size == 3 && it.ids.first() == "food" }
                                .forEach { option ->
                                    FilterChip(
                                        selected = category == option.displayName,
                                        onClick = { category = option.displayName },
                                        label = { Text(option.labels.last(), fontSize = 12.sp) },
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                }
                        }
                    }
                    OutlinedTextField(
                        value = category, onValueChange = { category = it },
                        label = { Text("دسته‌بندی") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    )
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text("توضیحات (اختیاری)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                shape = RoundedCornerShape(14.dp),
                onClick = {
                    val value = numericAmount ?: return@Button
                    val destination = if (kind == LedgerKind.TRANSFER) {
                        accounts.firstOrNull { it.id == targetId && it.id != sourceId }?.id
                            ?: return@Button
                    } else sourceId
                    onSave(
                        LedgerRecord(
                            id = UUID.randomUUID().toString(),
                            kind = kind,
                            amountToman = value,
                            fromAccountId = if (kind == LedgerKind.INCOME) null else sourceId,
                            toAccountId = if (kind == LedgerKind.EXPENSE) null else destination,
                            category = if (kind == LedgerKind.TRANSFER) "" else category.trim(),
                            note = note.trim(),
                            timestampMillis = System.currentTimeMillis(),
                        )
                    )
                },
            ) { Text(if (kind == LedgerKind.TRANSFER) "ثبت انتقال" else "ثبت تراکنش") }
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
                Text("این سقف برای هر ماه شمسی تکرار می‌شود.", fontSize = 13.sp, color = Quiet)
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
