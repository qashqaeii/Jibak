package ir.jibik.app

import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowInsetsControllerCompat
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        // Keep system bars visually continuous with the light liquid-glass canvas.
        // Android 15 may manage the bar surfaces itself; icon contrast still applies.
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.rgb(244, 248, 242)
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.rgb(248, 251, 247)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
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
    val savedReceiptLines = remember { mutableStateMapOf<String, List<PurchaseLine>>() }
    val accounts = remember { mutableStateListOf<Account>() }
    val goals = remember { mutableStateListOf<SavingsGoalEntity>() }
    val customCategories = remember { mutableStateListOf<CustomCategoryEntity>() }
    val scope = rememberCoroutineScope()
    val notifications = remember { SnackbarHostState() }
    val context = LocalContext.current
    var isExporting by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null && !isExporting) {
            // Read a fresh persisted snapshot, not a potentially stale UI list.
            isExporting = true
            scope.launch {
                try {
                    val snapshot = store.load()
                    val snapshotAccounts = store.loadAccounts()
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(uri, "w")
                            ?: error("Unable to open selected export document")
                        output.use { LedgerCsvExport.write(it, snapshotAccounts, snapshot) }
                    }
                    notifications.showSnackbar("فایل گردش مالی ذخیره شد.")
                } catch (_: Exception) {
                    notifications.showSnackbar(
                        "ذخیره خروجی کامل نشد؛ فایل انتخاب‌شده را بررسی یا حذف کن."
                    )
                } finally {
                    isExporting = false
                }
            }
        }
    }
    var isLoaded by remember { mutableStateOf(false) }
    var loadingFailed by remember { mutableStateOf(false) }
    var retryLoad by remember { mutableIntStateOf(0) }
    var storageError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    LaunchedEffect(store, retryLoad) {
        try {
            val persisted = store.load()
            val loadedReceipts = store.loadAllPurchaseLines()
            val loadedAccounts = store.loadAccounts()
            val persistedGoals = store.loadSavingsGoals()
            val persistedCategories = store.loadCustomCategories()
            // Account definitions first, so the ledger can never reference a missing account.
            accounts.clear()
            accounts.addAll(loadedAccounts)
            goals.clear()
            goals.addAll(persistedGoals)
            customCategories.clear()
            customCategories.addAll(persistedCategories)
            records.clear()
            records.addAll(persisted)
            savedReceiptLines.clear()
            savedReceiptLines.putAll(loadedReceipts)
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
    val categorySpending = detailedCategoryExpenses(
        records.toList(), savedReceiptLines.toMap(),
        currentMonth.startMillis, currentMonth.endExclusiveMillis,
    )
    var section by rememberSaveable { mutableStateOf(Section.HOME) }
    var selectedTransactionId by rememberSaveable { mutableStateOf<String?>(null) }
    var receiptRows by remember { mutableStateOf<List<PurchaseLine>?>(null) }
    var receiptLoadError by remember { mutableStateOf(false) }
    var receiptRetry by remember { mutableIntStateOf(0) }
    LaunchedEffect(selectedTransactionId, receiptRetry) {
        val id = selectedTransactionId ?: return@LaunchedEffect
        receiptRows = null
        receiptLoadError = false
        try {
            receiptRows = store.loadPurchaseLines(id)
        } catch (_: Exception) {
            receiptLoadError = true
        }
    }
    var showAdd by remember { mutableStateOf(false) }
    var addKind by remember { mutableStateOf(LedgerKind.EXPENSE) }
    var addTargetAccountId by remember { mutableStateOf<String?>(null) }
    var showCreateAccount by remember { mutableStateOf(false) }
    var showGoals by rememberSaveable { mutableStateOf(false) }
    var showCategoryManager by rememberSaveable { mutableStateOf(false) }
    var showNewCategory by remember { mutableStateOf(false) }
    var showNewGoal by remember { mutableStateOf(false) }
    // Keep the editor composed after rotation so its SavedState draft can be restored.
    var showItemizedPurchase by rememberSaveable { mutableStateOf(false) }
    var budget by remember { mutableStateOf(store.budgetToman()) }
    var showEditBudget by remember { mutableStateOf(false) }
    var jiboAiFeature by remember { mutableStateOf<JiboAiFeature?>(null) }

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
        Box(
            Modifier.fillMaxSize().background(Cream).padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            JibikStatePanel(
                title = "امکان خواندن اطلاعات مالی وجود ندارد",
                description = "برای محافظت از اطلاعات قبلی، ثبت تراکنش جدید متوقف شده. دوباره تلاش کن.",
                actionLabel = "تلاش دوباره",
                onAction = { isLoaded = false; retryLoad++ },
            )
        }
        return
    }

    Scaffold(
        containerColor = GlassPalette.backgroundTop,
        snackbarHost = { SnackbarHost(hostState = notifications) },
        bottomBar = {
            Box(
                Modifier
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .liquidGlass(GlassTone.CRYSTAL, radius = 26.dp, elevation = 9.dp),
            ) {
                NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp) {
                    Section.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = section == destination,
                        onClick = { section = destination },
                        icon = { JibikNavigationIcon(destination.ordinal, section == destination) },
                        label = { Text(destination.title, fontSize = 12.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedTextColor = Forest,
                            unselectedTextColor = Quiet,
                            indicatorColor = Color(0xFFC6F2E4).copy(alpha = 0.72f),
                        ),
                    )
                    }
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
                onTransactionClick = { selectedTransactionId = it.id },
                onAi = { jiboAiFeature = JiboAiFeature.SPENDING },
            )
            Section.TRANSACTIONS -> Transactions(
                records = records,
                receiptLines = savedReceiptLines.toMap(),
                customCategories = customCategories,
                modifier = Modifier.padding(insets),
                onTransactionClick = { selectedTransactionId = it.id },
                onCreateTransaction = { addKind = LedgerKind.EXPENSE; showAdd = true },
            )
            Section.BUDGET -> Budget(
                spent = monthTotals.expenseToman,
                budget = budget,
                monthTitle = currentMonth.title,
                modifier = Modifier.padding(insets),
                onEdit = { showEditBudget = true },
                onAi = { jiboAiFeature = JiboAiFeature.BUDGET },
            )
            Section.REPORTS -> ReportsScreen(
                records = records,
                categorySpending = categorySpending,
                receiptLines = savedReceiptLines.toMap(),
                onTransactionClick = { selectedTransactionId = it.id },
                modifier = Modifier.padding(insets),
                onExportCsv = {
                    if (!isExporting) {
                        exportLauncher.launch("jibik-ledger.csv")
                    }
                },
                onAi = { jiboAiFeature = JiboAiFeature.SPENDING },
            )
            Section.ACCOUNTS -> when {
                showCategoryManager -> CategoryManagementScreen(
                    categories = customCategories,
                    modifier = Modifier.padding(insets),
                    onBack = { showCategoryManager = false },
                    onAdd = { showNewCategory = true },
                    isSaving = isSaving,
                    onArchive = { categoryId, archived ->
                        if (!isSaving) {
                            isSaving = true
                            scope.launch {
                                try {
                                    store.setCustomCategoryArchived(categoryId, archived)
                                    customCategories.clear()
                                    customCategories.addAll(store.loadCustomCategories())
                                    notifications.showSnackbar(
                                        if (archived) "دسته بایگانی شد." else "دسته بازیابی شد."
                                    )
                                } catch (_: Exception) {
                                    storageError = "وضعیت دسته تغییر نکرد؛ ممکن است زیر‌دسته فعالی داشته باشد."
                                } finally {
                                    isSaving = false
                                }
                            }
                        }
                    },
                )
                showGoals -> Box(Modifier.padding(insets)) {
                    GoalsScreen(
                        goals = goals,
                        accounts = accounts,
                        balances = FinanceEngine.balances(accounts, records.toList()),
                        onBack = { showGoals = false },
                        onAdd = { showNewGoal = true },
                        onAi = { jiboAiFeature = JiboAiFeature.SAVINGS },
                        onTransferTo = { destination ->
                            addKind = LedgerKind.TRANSFER
                            addTargetAccountId = destination
                            showAdd = true
                        },
                    )
                }
                else -> AccountsScreen(
                    accounts = accounts,
                    balances = FinanceEngine.balances(accounts, records.toList()),
                    modifier = Modifier.padding(insets),
                    onAdd = { showCreateAccount = true },
                    onTransfer = { addKind = LedgerKind.TRANSFER; showAdd = true },
                    onGoals = { showGoals = true },
                    onCategories = { showCategoryManager = true },
                )
            }
        }
    }

    jiboAiFeature?.let { feature ->
        JiboAiDialog(feature = feature, records = records,
            onDismiss = { jiboAiFeature = null })
    }

    selectedTransactionId?.let { id ->
        records.firstOrNull { it.id == id }?.let { record ->
            TransactionDetailDialog(
                record = record,
                accounts = accounts,
                lines = receiptRows,
                loadFailed = receiptLoadError,
                onRetry = { receiptRetry++ },
                onDismiss = { selectedTransactionId = null; receiptRows = null },
            )
        }
    }
    if (showNewCategory) {
        NewCustomCategoryDialog(
            existing = customCategories,
            isSaving = isSaving,
            onDismiss = { if (!isSaving) showNewCategory = false },
            onSave = { category ->
                if (!isSaving) {
                    isSaving = true
                    scope.launch {
                        try {
                            store.addCustomCategory(category)
                            customCategories.clear()
                            customCategories.addAll(store.loadCustomCategories())
                            showNewCategory = false
                            notifications.showSnackbar("دسته اختصاصی جدید ذخیره شد.")
                        } catch (_: Exception) {
                            storageError = "ذخیره دسته ناموفق بود؛ داده‌های قبلی محفوظ هستند."
                        } finally {
                            isSaving = false
                        }
                    }
                }
            },
        )
    }
    if (showNewGoal) {
        NewGoalDialog(
            accounts = accounts,
            goals = goals,
            onDismiss = { if (!isSaving) showNewGoal = false },
            isSaving = isSaving,
            onSave = { newGoal ->
                if (!isSaving) {
                    isSaving = true
                    scope.launch {
                        try {
                            store.addSavingsGoal(newGoal)
                            goals.clear()
                            goals.addAll(store.loadSavingsGoals())
                            showNewGoal = false
                            scope.launch { notifications.showSnackbar("هدف پس‌انداز ساخته شد.") }
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
            customCategories = customCategories,
            onDismiss = { if (!isSaving) showItemizedPurchase = false },
            isSaving = isSaving,
            onSave = { purchase, lines ->
                if (!isSaving) {
                    isSaving = true
                    scope.launch {
                        try {
                            store.appendItemizedPurchase(purchase, lines)
                            savedReceiptLines[purchase.id] = lines.toList()
                            records.add(0, purchase)
                            showItemizedPurchase = false
                            // Show the immutable saved receipt immediately after commit.
                            selectedTransactionId = purchase.id
                            scope.launch { notifications.showSnackbar("خرید با موفقیت ثبت شد.") }
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
            onDismiss = { if (!isSaving) showCreateAccount = false },
            isSaving = isSaving,
            onSave = { newAccount ->
                if (!isSaving) {
                    isSaving = true
                    scope.launch {
                        try {
                            store.addAccount(newAccount)
                            accounts.clear()
                            accounts.addAll(store.loadAccounts())
                            showCreateAccount = false
                            scope.launch { notifications.showSnackbar("حساب جدید ساخته شد.") }
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
            customCategories = customCategories,
            initialKind = addKind,
            initialTargetAccountId = addTargetAccountId,
            isSaving = isSaving,
            onDismiss = { if (!isSaving) { showAdd = false; addTargetAccountId = null } },
            onSave = { record ->
                if (!isSaving) {
                    isSaving = true
                    scope.launch {
                        try {
                            // Store on background dispatcher, then update the visible ledger.
                            store.append(record)
                            records.add(0, record)
                            showAdd = false
                            addTargetAccountId = null
                            scope.launch { notifications.showSnackbar("تراکنش ثبت شد.") }
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
            modifier = Modifier.liquidGlass(tone = GlassTone.FROSTED, radius = 28.dp, elevation = 15.dp),
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
                runCatching { store.updateBudgetToman(newValue) }
                    .onSuccess {
                        budget = newValue
                        showEditBudget = false
                        scope.launch { notifications.showSnackbar("بودجه ماهانه ذخیره شد.") }
                    }
                    .onFailure {
                        storageError = "بودجه ذخیره نشد؛ مقدار قبلی محفوظ است."
                    }
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
        LiquidGlassButton(
            text = "شروع با جیبیک",
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(58.dp),
            tone = GlassTone.EMERALD,
        )
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
    onTransactionClick: (LedgerRecord) -> Unit,
    onAi: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().liquidBackdrop().verticalScroll(rememberScrollState())
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
        LiquidGlassCard(onClick = onAi, tone = GlassTone.CRYSTAL) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("✦ جیبو، همراه مالی هوشمند", color = Forest,
                        fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text("بررسی خرج‌ها، بدون دست‌کاری اطلاعات", color = Quiet,
                        fontSize = 12.sp)
                }
                Text("مشاهده", color = Forest, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
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
        LiquidGlassCard(
            onClick = onBudget,
            modifier = Modifier.fillMaxWidth(),
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
        if (records.isEmpty()) EmptyTransactions(onNewExpense)
        else records.take(3).forEach { record ->
            TransactionCard(record, onClick = { onTransactionClick(record) })
        }
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
    LiquidGlassCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        tone = GlassTone.CRYSTAL,
    ) {
        Column(Modifier.padding(0.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                            Text(
                                entry.category, fontSize = 13.sp, color = Deep,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                money(entry.amountToman), fontSize = 12.sp, color = Quiet,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(0.9f),
                                textAlign = TextAlign.End,
                            )
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
    LiquidGlassCard(
        modifier = modifier,
        tone = GlassTone.CRYSTAL,
        padding = PaddingValues(14.dp),
    ) {
        Column(Modifier.fillMaxWidth(),
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
private fun Transactions(
    records: List<LedgerRecord>,
    receiptLines: Map<String, List<PurchaseLine>>,
    customCategories: List<CustomCategoryEntity>,
    modifier: Modifier,
    onTransactionClick: (LedgerRecord) -> Unit,
    onCreateTransaction: () -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var kindFilter by rememberSaveable { mutableStateOf("همه") }
    var categoryPath by rememberSaveable { mutableStateOf("") }
    var showCategoryPicker by rememberSaveable { mutableStateOf(false) }
    val filtered = filterLocalTransactions(
        records = records, receiptLines = receiptLines,
        kindFilter = kindFilter, categoryPath = categoryPath, query = search,
    )
    LazyColumn(
        modifier = modifier.fillMaxSize().liquidBackdrop(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            JibikSectionTitle(
                "تراکنش‌ها",
                "تمام درآمدها، هزینه‌ها و انتقال‌های ثبت‌شده",
                trailing = {
                    LiquidGlassButton(
                        text = "＋ ثبت جدید",
                        onClick = onCreateTransaction,
                        tone = GlassTone.HONEY,
                    )
                },
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
                    JibikGlassChip(
                        text = option,
                        selected = kindFilter == option,
                        onClick = { kindFilter = option; categoryPath = "" },
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LiquidGlassButton(
                    text = if (categoryPath.isBlank()) "◉ فیلتر دسته‌بندی"
                        else categoryPath.substringAfterLast(" / "),
                    onClick = { showCategoryPicker = true },
                    tone = if (categoryPath.isBlank()) GlassTone.CRYSTAL else GlassTone.HONEY,
                    enabled = kindFilter != "انتقال",
                    modifier = Modifier.weight(1f),
                )
                if (categoryPath.isNotBlank()) {
                    TextButton(onClick = { categoryPath = "" }) {
                        Text("پاک‌کردن", color = Forest, fontSize = 12.sp)
                    }
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
                if (records.isEmpty()) EmptyTransactions(onCreateTransaction)
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
                    TextButton(onClick = {
                        search = ""
                        kindFilter = "همه"
                        categoryPath = ""
                    }) {
                        Text("پاک‌کردن فیلترها")
                    }
                }
            }
        } else {
            items(items = filtered, key = { it.id }) { record ->
                TransactionCard(record, onClick = { onTransactionClick(record) })
            }
        }
    }
    if (showCategoryPicker && kindFilter != "انتقال") {
        CategoryPickerDialog(
            income = kindFilter == "درآمد",
            selected = categoryPath,
            custom = customCategories,
            onSelected = {
                categoryPath = it
                kindFilter = if (kindFilter == "درآمد") "درآمد" else "هزینه"
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false },
        )
    }
}

@Composable
private fun TransactionCard(record: LedgerRecord, onClick: () -> Unit) {
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
    LiquidGlassCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        tone = GlassTone.CRYSTAL,
        padding = PaddingValues(horizontal = 13.dp, vertical = 13.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
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
private fun EmptyTransactions(onCreateTransaction: () -> Unit) {
    JibikStatePanel(
        title = "هنوز تراکنشی ثبت نکردی",
        description = "با ثبت اولین درآمد یا هزینه، گزارش مالی‌ات ساخته می‌شه.",
        actionLabel = "＋ ثبت اولین تراکنش",
        onAction = onCreateTransaction,
    )
}

@Composable
private fun Budget(
    spent: Long,
    budget: Long,
    monthTitle: String,
    modifier: Modifier,
    onEdit: () -> Unit,
    onAi: () -> Unit,
) {
    val remaining = budget - spent
    val usage = (spent.toDouble() / budget.coerceAtLeast(1L).toDouble()).toFloat()
    Column(
        modifier = modifier.fillMaxSize().liquidBackdrop().verticalScroll(rememberScrollState())
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
            LiquidGlassButton(
                text = "ویرایش سقف بودجه",
                onClick = onEdit,
                tone = GlassTone.HONEY,
                modifier = Modifier.fillMaxWidth(),
            )
            LiquidGlassButton(
                text = "✦ پیشنهاد هوشمند بودجه با جیبو",
                onClick = onAi,
                tone = GlassTone.CRYSTAL,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AddTransactionDialog(
    accounts: List<Account>,
    customCategories: List<CustomCategoryEntity>,
    initialKind: LedgerKind,
    initialTargetAccountId: String? = null,
    isSaving: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (LedgerRecord) -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf(initialKind) }
    var amount by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable(initialKind) {
        mutableStateOf(if (initialKind == LedgerKind.INCOME) "حقوق و دستمزد" else "خوراک")
    }
    var showCategoryPicker by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }
    var sourceId by rememberSaveable {
        mutableStateOf(accounts.firstOrNull()?.id.orEmpty())
    }
    var targetId by rememberSaveable(initialTargetAccountId) {
        mutableStateOf(
            initialTargetAccountId?.takeIf { id -> accounts.any { it.id == id } }
                ?: accounts.firstOrNull { it.id != sourceId }?.id.orEmpty(),
        )
    }
    val numericAmount = parseTomanDraft(amount)
    val sourceValid = accounts.any { it.id == sourceId }
    val targetValid = accounts.any { it.id == targetId }
    val categoryValid = when (kind) {
        LedgerKind.EXPENSE -> isSelectableCategory(category, false, customCategories)
        LedgerKind.INCOME -> isSelectableCategory(category, true, customCategories)
        LedgerKind.TRANSFER -> true
    }
    val canSave = !isSaving && numericAmount != null && numericAmount > 0L &&
        sourceValid && (kind != LedgerKind.TRANSFER ||
            (accounts.size > 1 && targetValid && sourceId != targetId)) &&
        categoryValid
    AlertDialog(
        modifier = Modifier.liquidGlass(tone = GlassTone.FROSTED, radius = 28.dp, elevation = 15.dp),
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                JibikBrandLogo(Modifier.size(39.dp))
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        if (kind == LedgerKind.TRANSFER) "انتقال بین حساب‌ها" else "تراکنش جدید",
                        color = Deep, fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                    )
                    Text(
                        "ثبت ساده و مطمئن",
                        color = Quiet, fontSize = 11.sp,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    listOf(
                        LedgerKind.EXPENSE to "هزینه",
                        LedgerKind.INCOME to "درآمد",
                        LedgerKind.TRANSFER to "انتقال",
                    ).forEach { (option, label) ->
                        LiquidGlassButton(
                            text = label,
                            onClick = {
                                kind = option
                                category = if (option == LedgerKind.INCOME) "حقوق و دستمزد" else "خوراک"
                                if (option == LedgerKind.TRANSFER && targetId == sourceId) {
                                    targetId = accounts.firstOrNull {
                                        it.id != sourceId
                                    }?.id.orEmpty()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !isSaving &&
                                (option != LedgerKind.TRANSFER || accounts.size > 1),
                            tone = if (kind == option) GlassTone.EMERALD else GlassTone.CRYSTAL,
                        )
                    }
                }
                JibikTomanField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = "مبلغ تراکنش",
                )
                JibikAccountChoice(
                    title = if (kind == LedgerKind.INCOME)
                        "حساب دریافت‌کننده" else "برداشت از حساب",
                    accounts = accounts,
                    selectedId = sourceId,
                    onSelect = { selected ->
                        sourceId = selected
                        if (selected == targetId) {
                            targetId = accounts.firstOrNull {
                                it.id != selected
                            }?.id.orEmpty()
                        }
                    },
                )
                if (kind == LedgerKind.TRANSFER) {
                    JibikAccountChoice(
                        title = "انتقال به حساب",
                        accounts = accounts.filter { it.id != sourceId },
                        selectedId = targetId,
                        onSelect = { targetId = it },
                        emptyHint = "برای انتقال ابتدا حساب دوم بساز.",
                    )
                    JibikFormHint("انتقال بین حساب‌ها درآمد یا هزینه محسوب نمی‌شه.")
                } else {
                    JibikSectionTitle(
                        title = if (kind == LedgerKind.INCOME) "دسته درآمد" else "دسته هزینه",
                        subtitle = "برای گزارش دقیق‌تر، جزئیات رو انتخاب کن",
                    )
                    LiquidGlassCard(
                        onClick = { if (!isSaving) showCategoryPicker = true },
                        tone = GlassTone.CRYSTAL,
                        padding = PaddingValues(horizontal = 14.dp, vertical = 13.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                category.ifBlank { "انتخاب دسته‌بندی" },
                                color = if (categoryValid) Deep else Coral,
                                modifier = Modifier.weight(1f),
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 3,
                            )
                            Text("‹ تغییر", color = Forest, fontSize = 12.sp)
                        }
                    }
                    if (!categoryValid) {
                        JibikFormHint("یک دسته معتبر انتخاب کن.", isWarning = true)
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(180) },
                    label = { Text("توضیحات (اختیاری)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSaving,
                    shape = RoundedCornerShape(15.dp),
                )
                if (!canSave && numericAmount == null && amount.isNotBlank()) {
                    JibikFormHint("مبلغ معتبر وارد کن.", isWarning = true)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = canSave,
                shape = RoundedCornerShape(15.dp),
                onClick = {
                    val value = numericAmount ?: return@Button
                    val destination = if (kind == LedgerKind.TRANSFER) {
                        accounts.firstOrNull {
                            it.id == targetId && it.id != sourceId
                        }?.id ?: return@Button
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
                        ),
                    )
                },
            ) {
                Text(
                    if (isSaving) "در حال ثبت..."
                    else if (kind == LedgerKind.TRANSFER) "ثبت انتقال"
                    else "ثبت تراکنش",
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("انصراف")
            }
        },
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(26.dp),
    )
    if (showCategoryPicker && kind != LedgerKind.TRANSFER && !isSaving) {
        CategoryPickerDialog(
            income = kind == LedgerKind.INCOME,
            selected = category,
            custom = customCategories,
            onSelected = {
                category = it
                showCategoryPicker = false
            },
            onDismiss = { showCategoryPicker = false },
        )
    }
}

@Composable
private fun BudgetDialog(
    current: Long,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(current.toString()) }
    val parsed = parseTomanDraft(value)
    AlertDialog(
        modifier = Modifier.liquidGlass(tone = GlassTone.FROSTED, radius = 28.dp, elevation = 15.dp),
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(26.dp),
        title = {
            Text("ویرایش سقف بودجه", color = Deep, fontWeight = FontWeight.ExtraBold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                JibikFormHint(
                    "این سقف برای هر ماه شمسی تکرار می‌شه. " +
                        "تراکنش‌های گذشته حذف یا ویرایش نمی‌شن.",
                )
                JibikTomanField(
                    value = value,
                    onValueChange = { value = it },
                    label = "سقف ماهانه",
                )
            }
        },
        confirmButton = {
            Button(
                enabled = parsed != null && parsed > 0L,
                onClick = { parsed?.let(onSave) },
                shape = RoundedCornerShape(15.dp),
            ) { Text("ذخیره بودجه") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        },
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

private fun money(amount: Long): String = formatToman(amount)
