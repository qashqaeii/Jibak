package ir.jibik.app

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
private val Coral = JibikStyle.coral
private val Paper = Color.White
private val Quiet = JibikStyle.muted

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
    private lateinit var protection: AppProtection
    override fun onStart() {
        super.onStart()
        if (::protection.isInitialized) protection.onForeground()
    }
    override fun onStop() {
        if (::protection.isInitialized) protection.onBackground()
        super.onStop()
    }
    override fun onDestroy() {
        if (::protection.isInitialized) protection.dispose()
        super.onDestroy()
    }
    private val calendarDay = androidx.compose.runtime.mutableLongStateOf(0L)
    private val dueReminderOpenSignal = mutableIntStateOf(0)
    private val bankSmsOpenSignal = mutableIntStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        calendarDay.longValue = intent.getLongExtra(LocalDueReminders.OPEN_CALENDAR_DAY, 0L)
        if (intent.getBooleanExtra(LocalDueReminders.OPEN_OBLIGATIONS, false)) {
            dueReminderOpenSignal.intValue++
        }
        if (intent.getBooleanExtra(BankSmsSettings.EXTRA_OPEN_BANK_SMS, false)) {
            bankSmsOpenSignal.intValue++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        calendarDay.longValue = intent?.getLongExtra(LocalDueReminders.OPEN_CALENDAR_DAY, 0L) ?: 0L
        if (intent?.getBooleanExtra(LocalDueReminders.OPEN_OBLIGATIONS, false) == true) {
            dueReminderOpenSignal.intValue++
        }
        if (intent?.getBooleanExtra(BankSmsSettings.EXTRA_OPEN_BANK_SMS, false) == true) {
            bankSmsOpenSignal.intValue++
        }
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
        protection = AppProtection(this)
        val store = LocalLedgerStore(applicationContext)
        setContent {
            MaterialTheme(colorScheme = colors, typography = JibikTypography,
                shapes = androidx.compose.material3.Shapes(
                    extraSmall = RoundedCornerShape(14.dp), small = RoundedCornerShape(14.dp),
                    medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(22.dp),
                    extraLarge = RoundedCornerShape(26.dp),
                )) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    if (protection.locked) AppLockedScreen(protection)
                    else JibikApp(
                        store, dueReminderOpenSignal.intValue,
                        bankSmsOpenSignal.intValue, calendarDay.longValue,
                        { calendarDay.longValue = 0; intent?.removeExtra(LocalDueReminders.OPEN_CALENDAR_DAY) },
                        protection,
                    )
                }
            }
        }
    }
}

internal enum class Section(val title: String) {
    HOME("خانه"),
    TRANSACTIONS("تراکنش‌ها"),
    BUDGET("بودجه"),
    REPORTS("گزارش‌ها"),
    ACCOUNTS("حساب‌ها"),
}

@Composable
private fun JibikApp(
    store: LocalLedgerStore,
    dueReminderOpenSignal: Int,
    bankSmsOpenSignal: Int, calendarDay: Long, onCalendarOpened: () -> Unit,
    protection: AppProtection,
) {
    var welcomed by remember { mutableStateOf(store.hasFinishedWelcome()) }
    var recurringRules by remember { mutableStateOf(emptyList<RecurringRule>()) }
    var recurringDecisions by remember { mutableStateOf(emptyList<RecurringDecision>()) }
    val voidedRecords = remember { mutableStateListOf<LedgerRecord>() }
    val records = remember { mutableStateListOf<LedgerRecord>() }
    val savedReceiptLines = remember { mutableStateMapOf<String, List<PurchaseLine>>() }
    val accounts = remember { mutableStateListOf<Account>() }
    val goals = remember { mutableStateListOf<SavingsGoalEntity>() }
    var envelopes by remember { mutableStateOf(emptyList<MoneyEnvelope>()) }
    var assets by remember { mutableStateOf(emptyList<AssetPosition>()) }
    val customCategories = remember { mutableStateListOf<CustomCategoryEntity>() }
    val categoryBudgets = remember { mutableStateListOf<CategoryBudgetEntity>() }
    val obligations = remember { mutableStateListOf<FinancialObligationEntity>() }
    val obligationPayments = remember { mutableStateListOf<ObligationPaymentEntity>() }
    val scope = rememberCoroutineScope()
    val notifications = remember { SnackbarHostState() }
    var homeAmountsVisible by rememberSaveable {
        mutableStateOf(store.areHomeAmountsVisible())
    }
    val context = LocalContext.current
    var dueRemindersOptedIn by remember {
        mutableStateOf(LocalDueReminders.isEnabled(context))
    }
    val activateDueReminders = {
        val enabled = runCatching { LocalDueReminders.enable(context) }.getOrDefault(false)
        dueRemindersOptedIn = enabled
        scope.launch {
            notifications.showSnackbar(
                if (enabled) "یادآوری محلی سررسیدها فعال شد."
                else "اعلان‌ها از تنظیمات گوشی مجاز نیستند."
            )
        }
    }
    val duePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) activateDueReminders()
        else scope.launch {
            notifications.showSnackbar("برای یادآوری، اجازه اعلان را از تنظیمات گوشی فعال کن.")
        }
    }
    var isExporting by remember { mutableStateOf(false) }
    var showEncryptedCsvDialog by remember { mutableStateOf(false) }
    // Ephemeral only: never saved to Bundle, preferences or Room.
    var pendingExportPassphrase by remember { mutableStateOf<CharArray?>(null) }
    val encryptedExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val secret = pendingExportPassphrase
        pendingExportPassphrase = null
        if (uri != null && secret != null && !isExporting) {
            isExporting = true
            scope.launch {
                try {
                    val snapshot = store.load()
                    val snapshotAccounts = store.loadAccounts()
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(uri, "w")
                            ?: error("Unable to create protected export file")
                        output.use {
                            EncryptedLedgerCsvExport.write(it, secret, snapshotAccounts, snapshot)
                        }
                    }
                    notifications.showSnackbar("فایل رمزدار گردش مالی ذخیره شد.")
                } catch (_: Exception) {
                    notifications.showSnackbar(
                        "خروجی رمزدار کامل نشد؛ فایل ناقص را حذف کن و دوباره تلاش کن."
                    )
                } finally {
                    secret.fill(Char.MIN_VALUE)
                    isExporting = false
                }
            }
        } else {
            secret?.fill(Char.MIN_VALUE)
        }
    }
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
    var budget by remember { mutableStateOf(0L) }
    val bankRevision by BankSmsUiEvents.changes.collectAsState()
    LaunchedEffect(store, retryLoad, bankRevision, bankSmsOpenSignal) {
        try {
            budget = store.budgetToman()
            val persisted = store.load()
            recurringRules = store.recurringRules()
            recurringDecisions = store.allRecurringDecisions()
            voidedRecords.clear()
            voidedRecords.addAll(store.voided())
            val loadedReceipts = store.loadAllPurchaseLines()
            val loadedAccounts = store.loadAccounts()
            val persistedGoals = store.loadSavingsGoals()
            val persistedEnvelopes = store.loadEnvelopes()
            val persistedAssets = store.loadAssets()
            val persistedCategories = store.loadCustomCategories()
            val persistedBudgets = store.loadCategoryBudgets()
            val persistedObligations = store.loadObligations()
            val persistedPayments = store.loadObligationPayments()
            // Account definitions first, so the ledger can never reference a missing account.
            accounts.clear()
            accounts.addAll(loadedAccounts)
            goals.clear()
            goals.addAll(persistedGoals)
            envelopes = persistedEnvelopes
            assets = persistedAssets
            customCategories.clear()
            customCategories.addAll(persistedCategories)
            categoryBudgets.clear()
            categoryBudgets.addAll(persistedBudgets)
            obligations.clear()
            obligations.addAll(persistedObligations)
            obligationPayments.clear()
            obligationPayments.addAll(persistedPayments)
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
    // Snapshot-derived finance summaries are recomputed only when ledger/account
    // data changes, not for every click, search query, or modal state update.
    // Reading SnapshotStateList/Map inside derivedStateOf preserves live updates.
    val summary by remember {
        androidx.compose.runtime.derivedStateOf {
            FinanceEngine.summary(accounts, records.toList())
        }
    }
    val currentMonth = currentJalaliMonth()
    val monthTotals by remember(currentMonth.startMillis, currentMonth.endExclusiveMillis) {
        androidx.compose.runtime.derivedStateOf {
            periodTotals(
                records.toList(), currentMonth.startMillis, currentMonth.endExclusiveMillis,
            )
        }
    }
    val categorySpending by remember(currentMonth.startMillis, currentMonth.endExclusiveMillis) {
        androidx.compose.runtime.derivedStateOf {
            detailedCategoryExpenses(
                records.toList(), savedReceiptLines.toMap(),
                currentMonth.startMillis, currentMonth.endExclusiveMillis,
            )
        }
    }
    var section by rememberSaveable { mutableStateOf(Section.HOME) }
    var showBankSms by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(bankSmsOpenSignal) {
        if (bankSmsOpenSignal > 0) {
            section = Section.TRANSACTIONS
            showBankSms = true
        }
    }
    var recurringSourceId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTransactionId by rememberSaveable { mutableStateOf<String?>(null) }
    var receiptRows by remember { mutableStateOf<List<PurchaseLine>?>(null) }
    var receiptLoadError by remember { mutableStateOf(false) }
    var revisions by remember { mutableStateOf(emptyList<LedgerRevision>()) }
    var financialEdits by remember { mutableStateOf(emptyList<FinancialEditHistory>()) }
    var bankLinked by remember { mutableStateOf(true) }
    var receiptRetry by remember { mutableIntStateOf(0) }
    LaunchedEffect(selectedTransactionId, receiptRetry) {
        val id = selectedTransactionId ?: return@LaunchedEffect
        receiptRows = null
        bankLinked = true
        financialEdits = emptyList()
        receiptLoadError = false
        try {
            bankLinked = store.isBankLinked(id)
            revisions = store.revisions(id)
            financialEdits = store.financialEdits(id)
            receiptRows = store.loadPurchaseLines(id)
        } catch (_: Exception) {
            receiptLoadError = true
        }
    }
    var showAdd by remember { mutableStateOf(false) }
    var editOriginal by remember { mutableStateOf<LedgerRecord?>(null) }
    var repeatRecord by remember { mutableStateOf<LedgerRecord?>(null) }
    var addKind by remember { mutableStateOf(LedgerKind.EXPENSE) }
    var addTargetAccountId by remember { mutableStateOf<String?>(null) }
    var showCreateAccount by remember { mutableStateOf(false) }
    var showGoals by rememberSaveable { mutableStateOf(false) }
    var showAssets by rememberSaveable { mutableStateOf(false) }
    var showCategoryManager by rememberSaveable { mutableStateOf(false) }
    var showNewCategory by remember { mutableStateOf(false) }
    var showNewGoal by remember { mutableStateOf(false) }
    // Keep the editor composed after rotation so its SavedState draft can be restored.
    var showItemizedPurchase by rememberSaveable { mutableStateOf(false) }
    var receiptEditId by rememberSaveable { mutableStateOf<String?>(null) }

    var showEditBudget by remember { mutableStateOf(false) }
    var editingCategoryBudget by remember { mutableStateOf<CategoryBudgetEntity?>(null) }
    var showCategoryBudgetDialog by remember { mutableStateOf(false) }
    var showObligations by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(calendarDay) { if (calendarDay > 0) section = Section.HOME }
    LaunchedEffect(dueReminderOpenSignal) {
        if (dueReminderOpenSignal > 0) {
            section = Section.ACCOUNTS
            showObligations = true
        }
    }
    var showNewObligation by remember { mutableStateOf(false) }
    var settlingObligationId by remember { mutableStateOf<String?>(null) }
    var jiboAiFeature by remember { mutableStateOf<JiboAiFeature?>(null) }

    // System gesture/Back button follows the same nested route hierarchy as
    // the visible back controls. No invisible subpage in another tab hijacks Back.
    val backRoute = resolveJibikBackRoute(
        section, showObligations, showCategoryManager, showGoals, showBankSms,
    )
    BackHandler(enabled = welcomed && isLoaded && !loadingFailed &&
        backRoute != JibikBackRoute.NONE) {
        if (section == Section.REPORTS && showAssets) showAssets = false
        else when (backRoute) {
            JibikBackRoute.OBLIGATIONS -> showObligations = false
            JibikBackRoute.CATEGORIES -> showCategoryManager = false
            JibikBackRoute.GOALS -> showGoals = false
            JibikBackRoute.BANK_SMS -> showBankSms = false
            JibikBackRoute.HOME -> section = Section.HOME
            JibikBackRoute.NONE -> Unit
        }
    }

    if (!welcomed) {
        WelcomeScreen(onStart = {
            try { store.finishWelcome(); welcomed = true }
            catch (_: Exception) { storageError = "شروع جیبیک کامل نشد؛ دوباره تلاش کن." }
        }, error = storageError)
        return
    }

    if (!isLoaded) {
        JibikLoadingScreen()
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

    val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Scaffold(
        containerColor = GlassPalette.backgroundTop,
        snackbarHost = {
            SnackbarHost(
                hostState = notifications,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                snackbar = { data ->
                    Snackbar(
                        snackbarData = data,
                        shape = RoundedCornerShape(18.dp),
                        containerColor = Forest,
                        contentColor = Color.White,
                    )
                },
            )
        },
        bottomBar = {
            // Navigation never competes with a text editor for the screen.
            if (!keyboardVisible) {
                Box(
                    Modifier.fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .liquidGlass(GlassTone.CRYSTAL, radius = 24.dp, elevation = 5.dp),
                ) {
                    NavigationBar(
                        modifier = Modifier.fillMaxWidth().height(70.dp),
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        containerColor = Color.Transparent,
                        tonalElevation = 0.dp,
                    ) {
                    Section.entries.forEach { destination ->
                        val active = section == destination
                        NavigationBarItem(
                            selected = active,
                            onClick = {
                                // Re-selecting a tab goes back to its main screen.
                                // This avoids trapping users in nested pages.
                                if (active) {
                                    when (destination) {
                                        Section.TRANSACTIONS -> showBankSms = false
                                        Section.REPORTS -> showAssets = false
                                        Section.ACCOUNTS -> {
                                            showGoals = false
                                            showCategoryManager = false
                                            showObligations = false
                                        }
                                        else -> Unit
                                    }
                                }
                                section = destination
                            },
                            icon = { JibikNavigationIcon(destination.ordinal, active) },
                            label = {
                                Text(
                                    destination.title,
                                    fontFamily = JibikFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                                    lineHeight = 16.sp,
                                    maxLines = 1,
                                )
                            },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedTextColor = Forest,
                                unselectedTextColor = Quiet,
                                indicatorColor = Color(0xFFDDF1E8),
                            ),
                        )
                    }
                    }
                }
            }
        },
    ) { insets ->
        when (section) {
            Section.HOME -> Home(
                spendableCard = {
                    val accountRows = accounts.toList()
                    val ledger = records.toList()
                    val balances = remember(accountRows, ledger) { FinanceEngine.balances(accountRows, ledger) }
                    SpendableBalanceCard(accountRows.filterNot { it.archived }, balances, goals.toList(), obligations.toList(),
                        obligationPayments.toList(), homeAmountsVisible, recurringRules, recurringDecisions,
                        envelopes = envelopes, onEnvelopes = { section = Section.ACCOUNTS; showObligations = false; showCategoryManager = false; showGoals = true })
                    FinancialReviewInbox(store, accountRows.filterNot { it.archived }, ledger,
                        onBank = { section = Section.TRANSACTIONS; showBankSms = true },
                        obligations = obligations.toList(), payments = obligationPayments.toList(),
                        customCategories = customCategories.toList(), showAmounts = homeAmountsVisible,
                         onChanged = { retryLoad++ })
                    FinancialCalendar(store, accountRows.filterNot { it.archived }, obligations.toList(), obligationPayments.toList(), calendarDay, onCalendarOpened,
                        records.firstOrNull { it.id == recurringSourceId }, { recurringSourceId = null },
                        records = ledger, allDecisions = recurringDecisions, customCategories = customCategories.toList(),
                        onPayObligation = { settlingObligationId = it }) { retryLoad++ }
                },
                summary = summary,
                records = records,
                budget = budget,
                monthTotals = monthTotals,
                monthTitle = currentMonth.title,
                categorySpending = categorySpending,
                amountsVisible = homeAmountsVisible,
                onToggleAmounts = {
                    val next = !homeAmountsVisible
                    runCatching { store.setHomeAmountsVisible(next) }
                        .onSuccess { homeAmountsVisible = next }
                        .onFailure {
                            scope.launch {
                                notifications.showSnackbar("ترجیح نمایش مبالغ ذخیره نشد.")
                            }
                        }
                },
                modifier = Modifier.padding(insets),
                onAllTransactions = { section = Section.TRANSACTIONS },
                onBudget = { section = Section.BUDGET },
                onItemizedPurchase = { showItemizedPurchase = true },
                onNewExpense = { addKind = LedgerKind.EXPENSE; showAdd = true },
                onNewIncome = { addKind = LedgerKind.INCOME; showAdd = true },
                onTransactionClick = { selectedTransactionId = it.id },
                onAi = { jiboAiFeature = JiboAiFeature.SPENDING },
            )
            Section.TRANSACTIONS -> if (showBankSms) {
                BankSmsScreen(
                    store = store,
                    accounts = accounts.filterNot { it.archived },
                    customCategories = customCategories,
                    existingRecords = records,
                    onBack = { showBankSms = false },
                    obligations = obligations.toList(), payments = obligationPayments.toList(),
                    rules = recurringRules, decisions = recurringDecisions,
                    onRegistered = {
                        retryLoad++
                        scope.launch {
                            notifications.showSnackbar("بررسی پیام بانکی انجام شد.")
                        }
                    },
                    modifier = Modifier.padding(insets),
                )
            } else Transactions(
                voided = voidedRecords,
                records = records,
                receiptLines = savedReceiptLines.toMap(),
                customCategories = customCategories,
                modifier = Modifier.padding(insets),
                onTransactionClick = { selectedTransactionId = it.id },
                onCreateTransaction = { addKind = LedgerKind.EXPENSE; showAdd = true },
                onBankSms = { showBankSms = true },
            )
            Section.BUDGET -> Budget(
                weeklyPanel = { WeeklyBudgetPanel(store, records.toList()) },
                spent = monthTotals.expenseToman,
                budget = budget,
                monthTitle = currentMonth.title,
                categoryBudgets = categoryBudgets,
                categoryUsage = monthlyCategoryBudgetUsage(categoryBudgets, categorySpending),
                isSaving = isSaving,
                onEditCategory = {
                    editingCategoryBudget = it
                    showCategoryBudgetDialog = true
                },
                onRemoveCategory = { path ->
                    if (!isSaving) {
                        isSaving = true
                        scope.launch {
                            try {
                                store.removeCategoryBudget(path)
                                categoryBudgets.clear()
                                categoryBudgets.addAll(store.loadCategoryBudgets())
                                notifications.showSnackbar("سقف این دسته حذف شد.")
                            } catch (_: Exception) {
                                storageError = "سقف بودجه تغییر نکرد."
                            } finally { isSaving = false }
                        }
                    }
                },
                modifier = Modifier.padding(insets),
                onEdit = { showEditBudget = true },
                onAi = { jiboAiFeature = JiboAiFeature.BUDGET },
            )
            Section.REPORTS -> if (showAssets) AssetsScreen(
                store = store, assets = assets, balances = FinanceEngine.balances(accounts, records.toList()),
                obligations = obligations.toList(), payments = obligationPayments.toList(), accounts = accounts.filterNot { it.archived },
                modifier = Modifier.padding(insets), onBack = { showAssets = false },
                onChanged = { assets = it; retryLoad++ },
            ) else ReportsScreen(
                records = records,
                wealth = netWorth(FinanceEngine.balances(accounts, records.toList()), assets, obligations.toList(), obligationPayments.toList()),
                onAssets = { showAssets = true },
                categorySpending = categorySpending,
                receiptLines = savedReceiptLines.toMap(),
                onTransactionClick = { selectedTransactionId = it.id },
                modifier = Modifier.padding(insets),
                backupActions = {
                    AppProtectionCard(protection)
                    BackupActions(store) { retryLoad++ }
                },
                onExportCsv = {
                    if (!isExporting) {
                        exportLauncher.launch("jibik-ledger.csv")
                    }
                },
                onExportEncryptedCsv = {
                    if (!isExporting && pendingExportPassphrase == null) {
                        showEncryptedCsvDialog = true
                    }
                },
                onAi = { jiboAiFeature = JiboAiFeature.SPENDING },
            )
            Section.ACCOUNTS -> when {
                showObligations -> ObligationsScreen(
                    store = store,
                    obligations = obligations,
                    payments = obligationPayments,
                    modifier = Modifier.padding(insets),
                    onBack = { showObligations = false },
                    onAdd = { showNewObligation = true },
                    onPay = { settlingObligationId = it },
                    onPaymentClick = { selectedTransactionId = it },
                    remindersEnabled = dueRemindersOptedIn &&
                        LocalDueReminders.permissionAvailable(context),
                    onEnableReminders = {
                        if (Build.VERSION.SDK_INT >= 33 &&
                            ContextCompat.checkSelfPermission(
                                context, Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            duePermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                        } else activateDueReminders()
                    },
                    onDisableReminders = {
                        if (runCatching { LocalDueReminders.disable(context) }.isSuccess) {
                            dueRemindersOptedIn = false
                            scope.launch { notifications.showSnackbar("یادآوری‌ها خاموش شدند.") }
                        } else scope.launch {
                            notifications.showSnackbar("خاموش‌کردن اعلان‌ها ناموفق بود.")
                        }
                    },
                    isSaving = isSaving,
                )
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
                        accounts = accounts.filterNot { it.archived },
                        balances = FinanceEngine.balances(accounts, records.toList()),
                        envelopes = envelopes, store = store,
                        onEnvelopesChanged = { envelopes = it },
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
                    store = store, onChanged = { retryLoad++ },
                    accounts = accounts,
                    balances = FinanceEngine.balances(accounts, records.toList()),
                    modifier = Modifier.padding(insets),
                    onAdd = { showCreateAccount = true },
                    onTransfer = { addKind = LedgerKind.TRANSFER; showAdd = true },
                    onGoals = { showGoals = true },
                    onCategories = { showCategoryManager = true },
                    onObligations = { showObligations = true },
                )
            }
        }
    }

    jiboAiFeature?.let { feature ->
        JiboAiDialog(feature = feature, records = records,
            onDismiss = { jiboAiFeature = null })
    }

    selectedTransactionId?.let { id ->
        val voided = voidedRecords.firstOrNull { it.id == id }
        (voided ?: records.firstOrNull { it.id == id })?.let { record ->
            val assetLinked = assets.any { item -> item.history.any { it.transactionId == id } }
            val archivedAccount = accounts.any { it.archived && (it.id == record.fromAccountId || it.id == record.toAccountId) }
            val lockedReason = when { assetLinked -> "این تراکنش متعلق به خرید یا فروش دارایی است و تغییر مستقیم آن مجاز نیست."; archivedAccount -> "برای تغییر این تراکنش ابتدا حساب بایگانی‌شده را بازیابی کن."; else -> null }
            TransactionDetailDialog(
                lockedReason = lockedReason,
                record = record,
                accounts = accounts,
                lines = receiptRows,
                loadFailed = receiptLoadError,
                onRetry = { receiptRetry++ },
                obligationTitle = obligationPayments.firstOrNull {
                    it.transactionId == record.id
                }?.let { payment ->
                    obligations.firstOrNull { it.id == payment.obligationId }?.title
                },
                revisions = revisions.filter { it.transactionId == id },
                recurringLinked = recurringDecisions.any { it.transactionId == id },
                financialEdits = financialEdits.filter { it.transactionId == id },
                onDetach = if (!isSaving && receiptRows != null && !receiptLoadError && (bankLinked || recurringDecisions.any { it.transactionId == id } || obligationPayments.any { it.transactionId == id })) ({
                    isSaving = true
                    scope.launch {
                        try {
                            store.detachPaymentLinks(record)
                            selectedTransactionId = null
                            retryLoad++
                            notifications.showSnackbar("ارتباط‌ها باز شدند؛ مبلغ حساب تغییر نکرد.")
                        } catch (_: Exception) { storageError = "قطع ارتباط انجام نشد؛ دوباره بررسی کن." }
                        finally { isSaving = false }
                    }
                }) else null,
                voided = voided != null,
                onSchedule = if (lockedReason == null && voided == null && recurringDecisions.none { it.transactionId == id } && record.kind != LedgerKind.TRANSFER &&
                    obligationPayments.none { it.transactionId == id }) ({
                    recurringSourceId = id
                    selectedTransactionId = null
                    section = Section.HOME
                }) else null,
                onToggleVoid = if (lockedReason == null && recurringDecisions.none { it.transactionId == id } && !bankLinked && receiptRows != null && obligationPayments.none { it.transactionId == id } && !isSaving) ({
                    isSaving = true
                    scope.launch {
                        try {
                            store.setVoided(record, voided == null)
                            selectedTransactionId = null
                            retryLoad++
                        } catch (_: Exception) {
                            storageError = "تغییر وضعیت ممکن نیست. تراکنش متصل به پرداخت بانکی، تعهد یا نوبت دوره‌ای باید حفظ شود."
                        } finally { isSaving = false }
                    }
                }) else null,
                onEdit = if (lockedReason == null && voided == null && recurringDecisions.none { it.transactionId == id } && !bankLinked && receiptRows != null && !receiptLoadError && !isSaving &&
                    obligationPayments.none { it.transactionId == id }) ({
                    if (receiptRows?.isNotEmpty() == true) {
                        receiptEditId = record.id
                        showItemizedPurchase = true
                    } else {
                        editOriginal = record
                        showAdd = true
                    }
                    selectedTransactionId = null
                    receiptRows = null
                }) else null,
                onRepeat = if (
                    lockedReason == null && voided == null && obligationPayments.none { it.transactionId == record.id } &&
                    receiptRows?.isEmpty() == true &&
                    (record.kind == LedgerKind.TRANSFER ||
                        isSelectableCategory(
                            record.category,
                            record.kind == LedgerKind.INCOME,
                            customCategories,
                        ))
                ) ({ ->
                    repeatRecord = record
                    addKind = record.kind
                    selectedTransactionId = null
                    receiptRows = null
                    showAdd = true
                }) else null,
                onDismiss = { selectedTransactionId = null; receiptRows = null },
            )
        }
    }
    if (showNewObligation) {
        NewObligationDialog(
            isSaving = isSaving,
            onDismiss = { if (!isSaving) showNewObligation = false },
            onSave = { obligation ->
                if (!isSaving) {
                    isSaving = true
                    scope.launch {
                        try {
                            store.addObligation(obligation)
                            obligations.clear()
                            obligations.addAll(store.loadObligations())
                            showNewObligation = false
                            notifications.showSnackbar("تعهد مالی ذخیره شد.")
                        } catch (_: Exception) {
                            storageError = "ثبت تعهد ممکن نشد؛ اطلاعات قبلی محفوظ ماند."
                        } finally { isSaving = false }
                    }
                }
            },
        )
    }

    settlingObligationId?.let { id ->
        obligations.firstOrNull { it.id == id }?.let { obligation ->
            SettleObligationDialog(
                obligation = obligation,
                payments = obligationPayments,
                accounts = accounts.filterNot { it.archived },
                records = records.filter { record -> recurringDecisions.none { it.transactionId == record.id } && assets.none { a -> a.history.any { it.transactionId == record.id } } },
                isSaving = isSaving,
                onDismiss = { if (!isSaving) settlingObligationId = null },
                onSave = { accountId, value, existingId ->
                    if (!isSaving) {
                        isSaving = true
                        scope.launch {
                            try {
                                val posted = if (existingId != null) {
                                    store.linkObligationPayment(id, accountId, existingId)
                                } else store.settleObligation(
                                    obligationId = id,
                                    accountId = accountId,
                                    amountToman = value,
                                    transactionId = UUID.randomUUID().toString(),
                                )
                                if (existingId == null) records.add(0, posted)
                                obligationPayments.clear()
                                obligationPayments.addAll(store.loadObligationPayments())
                                settlingObligationId = null
                                selectedTransactionId = posted.id
                                notifications.showSnackbar(if (existingId == null) "پرداخت واقعی ثبت شد." else "تراکنش به تعهد متصل شد.")
                            } catch (_: Exception) {
                                storageError = "پرداخت ثبت نشد؛ مانده حساب و تعهد تغییری نکرد."
                            } finally { isSaving = false }
                        }
                    }
                },
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
            accounts = accounts.filter { account -> !account.archived && envelopes.none { it.accountId == account.id } },
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
    val editingReceipt = receiptEditId?.let { id -> records.firstOrNull { it.id == id } }
    val editingReceiptLines = receiptEditId?.let { savedReceiptLines[it] }
    if (showItemizedPurchase && (receiptEditId == null || (editingReceipt != null && !editingReceiptLines.isNullOrEmpty()))) {
        ItemizedPurchaseDialog(
            accounts = accounts.filterNot { it.archived },
            customCategories = customCategories,
            onDismiss = { if (!isSaving) { showItemizedPurchase = false; receiptEditId = null } },
            original = editingReceipt,
            originalLines = editingReceiptLines.orEmpty(),
            isSaving = isSaving,
            onSave = { purchase, lines ->
                if (!isSaving) {
                    isSaving = true
                    scope.launch {
                        try {
                            if (editingReceipt == null) {
                                store.appendItemizedPurchase(purchase, lines)
                                savedReceiptLines[purchase.id] = lines.toList()
                                records.add(0, purchase)
                            } else {
                                store.correctReceipt(editingReceipt, purchase, editingReceiptLines.orEmpty(), lines)
                                records.clear(); records.addAll(store.load())
                                savedReceiptLines[purchase.id] = store.loadPurchaseLines(purchase.id)
                            }
                            receiptEditId = null
                            showItemizedPurchase = false
                            // Show the immutable saved receipt immediately after commit.
                            selectedTransactionId = purchase.id
                            scope.launch { notifications.showSnackbar(if (editingReceipt == null) "خرید با موفقیت ثبت شد." else "رسید اصلاح شد؛ نسخه قبلی محفوظ است.") }
                        } catch (_: Exception) {
                            storageError = "ذخیره انجام نشد؛ دفتر مالی را دوباره بررسی کن."
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
    if (showEncryptedCsvDialog) {
        EncryptedCsvExportDialog(
            onDismiss = { showEncryptedCsvDialog = false },
            onConfirm = { passphrase ->
                showEncryptedCsvDialog = false
                if (!isExporting) {
                    pendingExportPassphrase?.fill(Char.MIN_VALUE)
                    pendingExportPassphrase = passphrase
                    try {
                        encryptedExportLauncher.launch("jibik-ledger-encrypted.jbc")
                    } catch (_: Exception) {
                        pendingExportPassphrase = null
                        passphrase.fill(Char.MIN_VALUE)
                        scope.launch {
                            notifications.showSnackbar("انتخاب محل ذخیره‌سازی ممکن نشد.")
                        }
                    }
                } else {
                    passphrase.fill(Char.MIN_VALUE)
                }
            },
        )
    }
    if (showAdd) {
        AddTransactionDialog(
            accounts = accounts.filterNot { it.archived },
            customCategories = customCategories,
            initialKind = addKind,
            initialTargetAccountId = addTargetAccountId,
            repeatRecord = editOriginal ?: repeatRecord,
            editing = editOriginal != null,
            isSaving = isSaving,
            onDismiss = { if (!isSaving) {
                showAdd = false
                addTargetAccountId = null
                repeatRecord = null
                editOriginal = null
            } },
            onSave = { record ->
                if (!isSaving) {
                    isSaving = true
                    scope.launch {
                        try {
                            // Store on background dispatcher, then update the visible ledger.
                            val original = editOriginal
                            if (original == null) {
                                store.append(record)
                                records.add(0, record)
                            } else {
                                store.correct(original, record)
                                records.clear()
                                records.addAll(store.load())
                                editOriginal = null
                                selectedTransactionId = record.id
                            }
                            showAdd = false
                            addTargetAccountId = null
                            repeatRecord = null
                            notifications.showSnackbar(
                                if (original != null) "اصلاح تراکنش ذخیره شد." else when (record.kind) {
                                    LedgerKind.EXPENSE -> "هزینه ثبت شد."
                                    LedgerKind.INCOME -> "درآمد ثبت شد."
                                    LedgerKind.TRANSFER -> "انتقال بین حساب‌ها انجام شد."
                                },
                            )
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
        JibikModalDialog(
        properties = JibikWideDialogProperties,
            modifier = Modifier.jibikDialogSurface(),
            onDismissRequest = { storageError = null },
            title = { JibikDialogHeading("خطا در ذخیره اطلاعات", "اطلاعات قبلی محفوظ‌اند.") },
            text = { Text(storageError ?: "") },
            confirmButton = { TextButton(onClick = { storageError = null }) { Text("متوجه شدم") } },
        )
    }
    if (showCategoryBudgetDialog) {
        CategoryBudgetDialog(
            previous = editingCategoryBudget,
            existingBudgets = categoryBudgets,
            customCategories = customCategories,
            isSaving = isSaving,
            onDismiss = { if (!isSaving) showCategoryBudgetDialog = false },
            onSave = { cap ->
                if (!isSaving) {
                    isSaving = true
                    scope.launch {
                        try {
                            store.saveCategoryBudget(cap)
                            categoryBudgets.clear()
                            categoryBudgets.addAll(store.loadCategoryBudgets())
                            showCategoryBudgetDialog = false
                            notifications.showSnackbar("سقف دسته ذخیره شد.")
                        } catch (_: Exception) {
                            storageError = "سقف دسته ذخیره نشد؛ مقدار قبلی محفوظ است."
                        } finally { isSaving = false }
                    }
                }
            },
        )
    }
    if (showEditBudget) {
        BudgetDialog(
            current = budget,
            onDismiss = { showEditBudget = false },
            onSave = { newValue ->
                scope.launch {
                runCatching { store.updateBudgetToman(newValue) }
                    .onSuccess {
                        budget = newValue
                        showEditBudget = false
                        scope.launch {
                            notifications.showSnackbar(
                                if (newValue == 0L) "سقف بودجه برداشته شد؛ تراکنش‌ها محفوظ‌اند."
                                else "سقف بودجه ماهانه ذخیره شد.",
                            )
                        }
                    }
                    .onFailure {
                        storageError = "بودجه ذخیره نشد؛ مقدار قبلی محفوظ است."
                    }
                }
            },
        )
    }
}


/** Honest loading state: no fake balances or placeholder financial figures. */
@Composable
private fun JibikLoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize().liquidBackdrop()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            JiboArt(JiboArtwork.ASSISTANT, Modifier.size(112.dp))
            Text(
                "در حال آماده‌کردن اطلاعات مالی",
                color = JibikStyle.ink,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp,
            )
            Text(
                "اطلاعات ثبت‌شده از حافظه همین گوشی خوانده می‌شوند.",
                color = JibikStyle.muted,
                fontSize = 13.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
            )
            CircularProgressIndicator(
                color = Forest,
                strokeWidth = 3.dp,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

/** Keep the primary onboarding CTA visible on compact screens and large fonts. */
internal fun compactJibikWelcome(widthDp: Float, heightDp: Float, fontScale: Float): Boolean =
    widthDp < 360f || heightDp < 720f || fontScale >= 1.3f

@Composable
private fun WelcomeScreen(onStart: () -> Unit, error: String? = null) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFF4F8F2)).safeDrawingPadding()) {
        val compact = compactJibikWelcome(maxWidth.value, maxHeight.value, LocalDensity.current.fontScale)
        val wide = maxWidth >= 700.dp && LocalDensity.current.fontScale < 1.3f
        val side = if (compact) 20.dp else 28.dp
        Column(
            Modifier.widthIn(max = if (wide) 960.dp else 560.dp).fillMaxSize()
                .align(Alignment.Center).padding(horizontal = side),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = if (compact) 12.dp else 20.dp),
                verticalAlignment = Alignment.CenterVertically) {
                JibikBrandLogo(Modifier.size(40.dp))
                Spacer(Modifier.size(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("جیبیک", fontSize = 25.sp, color = Forest, fontWeight = FontWeight.ExtraBold)
                    Text("مدیریت مالی شخصی", fontSize = 11.sp, color = Quiet)
                }
                Text("به سبک خودت", color = Forest, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Column(Modifier.weight(1f, fill = true).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp)) {
                if (wide) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(22.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { WelcomeHero(false) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            WelcomeCopy(false)
                            WelcomeBenefits()
                        }
                    }
                } else {
                    WelcomeHero(compact)
                    WelcomeCopy(compact)
                    WelcomeBenefits()
                }
                Spacer(Modifier.height(4.dp))
            }
            // Primary action is not part of the scroll region; readable at larger text sizes.
            Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = if (compact) 10.dp else 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (error != null) JibikFormHint(error, isWarning = true)
                LiquidGlassButton(text = "شروع مدیریت مالی", onClick = onStart,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), tone = GlassTone.EMERALD)
                Text("بدون نیاز به ساخت حساب کاربری برای شروع",
                    color = Quiet, fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun WelcomeHero(compact: Boolean) {
    val largeText = LocalDensity.current.fontScale >= 1.3f
    Box(Modifier.fillMaxWidth().heightIn(min = if (compact) 180.dp else 260.dp)
        .clip(RoundedCornerShape(28.dp)).background(Color(0xFF07574C))) {
        Canvas(Modifier.matchParentSize()) {
            val r = size.minDimension
            drawCircle(Color(0xFF176C5C), r * .63f, Offset(size.width * .77f, size.height * .55f))
            drawCircle(Color(0xFF20816B), r * .43f, Offset(size.width * .77f, size.height * .55f))
            drawCircle(Color(0xFFDAF0DD).copy(alpha = .10f), r * .28f, Offset(size.width * .06f, size.height * .95f))
        }
        if (largeText) {
            Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                JiboMascot(Modifier.size(108.dp))
                WelcomeHeroCopy(compact, centered = true)
            }
        } else {
            Row(Modifier.fillMaxWidth().align(Alignment.Center).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { WelcomeHeroCopy(compact, centered = false) }
                JiboMascot(Modifier.weight(1f).height(if (compact) 140.dp else 214.dp))
            }
        }
    }
}

@Composable
private fun WelcomeHeroCopy(compact: Boolean, centered: Boolean) {
    Column(horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        val align = if (centered) TextAlign.Center else TextAlign.Start
        Text("همراه مالی تو", color = Color(0xFFD1EEE0), fontSize = 12.sp, textAlign = align)
        Text("سلام، من\nجیبو هستم!", color = Color.White,
            fontSize = if (compact) 22.sp else 30.sp, lineHeight = if (compact) 30.sp else 39.sp,
            fontWeight = FontWeight.ExtraBold, textAlign = align)
        Text("قدم‌به‌قدم کنار تو", color = Color(0xFFD1EEE0), fontSize = 11.sp, textAlign = align)
    }
}

@Composable
private fun WelcomeCopy(compact: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("پولت را بهتر بشناس.\nبا خیال راحت‌تر خرج کن.", color = Deep,
            fontSize = if (compact) 23.sp else 30.sp, lineHeight = if (compact) 33.sp else 42.sp,
            fontWeight = FontWeight.ExtraBold)
        Text("درآمد، هزینه، تعهدات و پس‌انداز؛ همه در یک دفتر مالی ساده و دقیق.",
            color = Quiet, fontSize = if (compact) 13.sp else 15.sp, lineHeight = 22.sp)
    }
}

@Composable
private fun WelcomeBenefits() {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple(JiboArtwork.TRANSACTIONS, "حساب‌وکتاب روزمره", "ثبت سریع درآمد و خرید"),
            Triple(JiboArtwork.GOALS, "پول برای هدف‌های تو", "بودجه و پاکت‌های پس‌انداز"),
            Triple(JiboArtwork.OBLIGATIONS, "آینده مالی روشن‌تر", "تقویم و پیش‌بینی مانده"),
        ).forEachIndexed { index, (art, title, subtitle) ->
            if (index > 0) HorizontalDivider(color = Color(0xFFE9EEEA))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFEAF4EE)),
                    contentAlignment = Alignment.Center) { JiboArt(art, Modifier.size(35.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Deep)
                    Text(subtitle, color = Quiet, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun Home(
    spendableCard: @Composable () -> Unit,
    summary: MoneySummary,
    records: List<LedgerRecord>,
    budget: Long,
    monthTotals: PeriodTotals,
    monthTitle: String,
    categorySpending: List<CategoryExpense>,
    amountsVisible: Boolean,
    onToggleAmounts: () -> Unit,
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
        BalanceCard(summary, amountsVisible, onToggleAmounts)
        spendableCard()
        JibikSectionTitle("ثبت سریع", "کارهای روزمره، بدون فرم‌های طولانی")
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val fontScale = LocalDensity.current.fontScale
            val stacked = maxWidth < 315.dp || fontScale >= 1.65f
            val compact = maxWidth < 430.dp || fontScale >= 1.25f
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (stacked) {
                    JibikWideQuickAction(
                        title = "ثبت هزینه", subtitle = "مخارج روزانه",
                        artwork = JiboArtwork.TRANSACTIONS,
                        onClick = onNewExpense,
                    )
                    JibikWideQuickAction(
                        title = "ثبت درآمد", subtitle = "دریافتی‌ها",
                        artwork = JiboArtwork.ACCOUNTS,
                        onClick = onNewIncome,
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        JibikQuickAction(
                            title = "ثبت هزینه", subtitle = "مخارج روزانه",
                            artwork = JiboArtwork.TRANSACTIONS,
                            onClick = onNewExpense, modifier = Modifier.weight(1f),
                        )
                        JibikQuickAction(
                            title = "ثبت درآمد", subtitle = "دریافتی‌ها",
                            artwork = JiboArtwork.ACCOUNTS,
                            onClick = onNewIncome, modifier = Modifier.weight(1f),
                        )
                        if (!compact) {
                            JibikQuickAction(
                                title = "خرید منزل", subtitle = "چند قلم کالا",
                                artwork = JiboArtwork.PURCHASE,
                                onClick = onItemizedPurchase,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                if (compact || stacked) {
                    JibikWideQuickAction(
                        title = "ثبت خرید چندقلمی",
                        subtitle = "برای هر کالا، مبلغ و دسته جداگانه",
                        artwork = JiboArtwork.PURCHASE,
                        onClick = onItemizedPurchase,
                    )
                }
            }
        }
        JibikSectionTitle("خلاصه " + monthTitle, "درآمد و هزینه همین ماه")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("درآمد", monthTotals.incomeToman, Mint, Modifier.weight(1f), amountsVisible)
            MetricCard("هزینه", monthTotals.expenseToman, Coral, Modifier.weight(1f), amountsVisible)
        }
        LiquidGlassCard(
            onClick = onBudget,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text("بودجه " + monthTitle, fontWeight = FontWeight.ExtraBold,
                            color = Deep, fontSize = 16.sp, maxLines = 2)
                        Text("مدیریت سقف هزینه", color = Quiet, fontSize = 11.sp)
                    }
                    Text(
                        if (budget > 0L) jibikPrivateAmount(money(budget), amountsVisible)
                        else "بدون سقف",
                        modifier = Modifier.weight(1f),
                        color = Forest, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        maxLines = 2, lineHeight = 19.sp,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (budget <= 0L) {
                    Text(
                        "هنوز سقف هزینه‌ای تعیین نکردی. برای تنظیم بودجه این کارت را لمس کن.",
                        color = Quiet, fontSize = 13.sp, lineHeight = 21.sp,
                    )
                } else {
                    val used = (monthTotals.expenseToman.toDouble() /
                        budget.toDouble()).toFloat().coerceIn(0f, 1f)
                    if (amountsVisible) {
                        JibikProgressTrack(
                            progress = used,
                            color = if (monthTotals.expenseToman > budget) Coral else Mint,
                        )
                    }
                    val remaining = budget - monthTotals.expenseToman
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            if (remaining >= 0) "باقی‌مانده" else "بیش از بودجه",
                            color = Quiet, fontSize = 12.sp,
                        )
                        Text(
                            jibikPrivateAmount(
                                money(if (remaining >= 0) remaining else -remaining),
                                amountsVisible,
                            ),
                            color = if (remaining >= 0) Forest else Coral,
                            fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            maxLines = 2, lineHeight = 19.sp,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        SpendingByCategory(
            categories = categorySpending,
            total = monthTotals.expenseToman,
            monthTitle = monthTitle,
            amountsVisible = amountsVisible,
            onClick = onAllTransactions,
        )
        LiquidGlassCard(onClick = onAi, tone = GlassTone.CRYSTAL) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JiboArt(JiboArtwork.ASSISTANT, Modifier.size(48.dp))
                Column(Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("جیبو، همراه مالی هوشمند", color = Forest,
                        fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text("بررسی خرج‌ها بدون تغییر اطلاعات مالی",
                        color = Quiet, fontSize = 12.sp, lineHeight = 18.sp)
                }
                JibikChevronIcon(color = Forest)
            }
        }
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
            TransactionCard(
                record,
                onClick = { onTransactionClick(record) },
                amountsVisible = amountsVisible,
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SpendingByCategory(
    categories: List<CategoryExpense>,
    total: Long,
    monthTitle: String,
    amountsVisible: Boolean,
    onClick: () -> Unit,
) {
    val palette = listOf(Forest, Orange, Mint, Coral)
    LiquidGlassCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        tone = GlassTone.CRYSTAL,
    ) {
        Column(Modifier.padding(0.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text("خرج‌ها کجا رفتن؟", modifier = Modifier.weight(1f), color = Deep, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Text(monthTitle, modifier = Modifier.weight(0.7f), textAlign = TextAlign.End, color = Quiet, fontSize = 12.sp)
            }
            if (!amountsVisible) {
                Text(
                    "مبلغ‌ها و سهم دسته‌ها پنهان‌اند. برای دیدن آن‌ها از کارت موجودی استفاده کن.",
                    color = Quiet, fontSize = 13.sp, lineHeight = 20.sp,
                )
            } else if (categories.isEmpty()) {
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
                                maxLines = 2, lineHeight = 19.sp,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                money(entry.amountToman), fontSize = 12.sp, color = Quiet,
                                modifier = Modifier.weight(0.9f),
                                textAlign = TextAlign.End,
                                maxLines = 2, lineHeight = 19.sp,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        JibikProgressTrack(
                            progress = progress,
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
private fun BalanceCard(
    summary: MoneySummary,
    amountsVisible: Boolean,
    onToggleAmounts: () -> Unit,
) {
    JibikHero(Modifier.fillMaxWidth(), illustration = JiboArtwork.HOME) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text("مجموع موجودی حساب‌ها", color = Color(0xFFDCF3EA), fontSize = 13.sp)
                Text("دارایی ثبت‌شده تا امروز", color = Color(0xFFB9DDD1), fontSize = 11.sp)
            }
            TextButton(
                onClick = onToggleAmounts,
                modifier = Modifier.semantics {
                    stateDescription = if (amountsVisible) "مبالغ نمایان‌اند"
                        else "مبالغ پنهان‌اند"
                    contentDescription = if (amountsVisible)
                        "مخفی‌کردن مبالغ صفحه خانه" else "نمایش مبالغ صفحه خانه"
                },
            ) {
                Text(
                    if (amountsVisible) "مخفی‌کردن" else "نمایش",
                    color = Color.White, fontSize = 12.sp,
                )
            }
        }
        JibikHeroAmount(
            amount = jibikPrivateAmount(money(summary.balanceToman), amountsVisible),
            accessibilityLabel = if (amountsVisible) money(summary.balanceToman)
                else "مجموع موجودی حساب‌ها پنهان است",
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.18f))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text("همه حساب‌های شخصی", modifier = Modifier.weight(1f), color = Color(0xFFE4F4ED), fontSize = 11.sp)
            Text("به‌روزرسانی با هر تراکنش", modifier = Modifier.weight(1f), textAlign = TextAlign.End, color = Color(0xFFE4F4ED), fontSize = 11.sp)
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    amount: Long,
    accent: Color,
    modifier: Modifier = Modifier,
    amountsVisible: Boolean = true,
) {
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
            val formatted = jibikPrivateAmount(money(amount), amountsVisible)
            Text(
                formatted,
                fontFamily = JibikFontFamily,
                fontSize = when {
                    formatted.length >= 27 -> 12.sp
                    formatted.length >= 19 -> 13.sp
                    else -> 15.sp
                },
                fontWeight = FontWeight.ExtraBold,
                color = Deep,
                maxLines = 3,
                lineHeight = 21.sp,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Transactions(
    voided: List<LedgerRecord>,
    records: List<LedgerRecord>,
    receiptLines: Map<String, List<PurchaseLine>>,
    customCategories: List<CustomCategoryEntity>,
    modifier: Modifier,
    onTransactionClick: (LedgerRecord) -> Unit,
    onCreateTransaction: () -> Unit,
    onBankSms: () -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var kindFilter by rememberSaveable { mutableStateOf("همه") }
    var categoryPath by rememberSaveable { mutableStateOf("") }
    var showCategoryPicker by rememberSaveable { mutableStateOf(false) }
    // One derived snapshot per active filter. Unrelated modal/navigation state
    // should not re-run the full ledger search on every Compose recomposition.
    var showVoided by rememberSaveable { mutableStateOf(false) }
    val filtered by remember(records, voided, showVoided, receiptLines, kindFilter, categoryPath, search) {
        derivedStateOf {
            filterLocalTransactions(
                records = if (showVoided) voided else records, receiptLines = receiptLines,
                kindFilter = kindFilter, categoryPath = categoryPath, query = search,
            )
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().liquidBackdrop(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            JibikPageHeader(
                title = "تراکنش‌ها",
                subtitle = "تمام درآمدها، هزینه‌ها و انتقال‌های ثبت‌شده",
                action = {
                    LiquidGlassButton(
                        text = "＋ ثبت جدید",
                        onClick = onCreateTransaction,
                        tone = GlassTone.HONEY,
                    )
                },
            )
        }
        item {
            LiquidGlassCard(
                onClick = onBankSms,
                tone = GlassTone.CRYSTAL,
                padding = PaddingValues(horizontal = 14.dp, vertical = 13.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    JiboArt(JiboArtwork.TRANSACTIONS, Modifier.size(52.dp))
                    Column(Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("پیامک‌های بانکی",
                            color = Forest, fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold)
                        Text(
                            "آزمایش دستی پیام بانکی؛ دسترسی SMS در نسخه تست غیرفعال است",
                            color = Quiet, fontSize = 11.sp, lineHeight = 17.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    JibikChevronIcon(color = Forest)
                }
            }
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("جستجو در تراکنش‌ها") },
                placeholder = { Text("نام، دسته‌بندی یا مبلغ") },
                leadingIcon = { JibikSearchIcon() },
                trailingIcon = {
                    if (search.isNotBlank()) {
                        IconButton(onClick = { search = "" }) {
                            Text("×", color = Forest, fontSize = 22.sp)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
            )
        }
        item {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (voided.isNotEmpty() || showVoided) JibikGlassChip(
                    text = "باطل‌شده‌ها", selected = showVoided,
                    onClick = { showVoided = !showVoided },
                )
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
                else JibikStatePanel(
                    title = "تراکنشی با این فیلترها پیدا نشد",
                    description = "جستجو یا فیلترها رو پاک کن تا همه تراکنش‌های ثبت‌شده رو ببینی.",
                    actionLabel = "پاک‌کردن همه فیلترها",
                    onAction = {
                        search = ""
                        kindFilter = "همه"
                        categoryPath = ""
                    },
                )
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
private fun TransactionCard(
    record: LedgerRecord,
    onClick: () -> Unit,
    amountsVisible: Boolean = true,
) {
    val expense = record.kind == LedgerKind.EXPENSE
    val transfer = record.kind == LedgerKind.TRANSFER
    val highlight = when {
        expense -> Coral
        transfer -> Color(0xFFB26A12)
        else -> Forest
    }
    val amountLabel = (when (record.kind) {
        LedgerKind.EXPENSE -> "− "
        LedgerKind.INCOME -> "+ "
        LedgerKind.TRANSFER -> "⇄ "
    }) + jibikPrivateAmount(money(record.amountToman), amountsVisible)
    val title = record.note.ifBlank {
        record.category.substringAfterLast(" / ")
            .ifBlank { if (transfer) "انتقال بین حساب‌ها" else "تراکنش" }
    }
    val context = when (record.kind) {
        LedgerKind.EXPENSE -> "هزینه · " +
            record.category.substringBeforeLast(" / ", record.category)
        LedgerKind.INCOME -> "درآمد · " +
            record.category.substringBeforeLast(" / ", record.category)
        LedgerKind.TRANSFER -> "انتقال بین حساب‌ها"
    }
    LiquidGlassCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        tone = GlassTone.CRYSTAL,
        padding = PaddingValues(horizontal = 13.dp, vertical = 13.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 340.dp || LocalDensity.current.fontScale >= 1.2f
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JibikMoneyDirectionIcon(
                    kind = record.kind,
                    tint = highlight,
                    modifier = Modifier.size(42.dp),
                )
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        title, fontWeight = FontWeight.Bold, color = Deep,
                        fontSize = 13.sp, maxLines = 2,
                        lineHeight = 19.sp,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        context, color = Quiet, fontSize = 12.sp,
                        lineHeight = 18.sp, maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        formatJalaliDueDate(record.timestampMillis),
                        color = Quiet.copy(alpha = 0.90f),
                        fontSize = 11.sp, lineHeight = 17.sp,
                        maxLines = 1,
                    )
                    if (compact) {
                        Text(
                            amountLabel,
                            fontWeight = FontWeight.ExtraBold,
                            color = highlight,
                            fontSize = 14.sp,
                            maxLines = 2, lineHeight = 20.sp,
                        )
                    }
                }
                if (!compact) {
                    Text(
                        amountLabel,
                        modifier = Modifier.weight(0.9f),
                        fontWeight = FontWeight.ExtraBold,
                        color = highlight, fontSize = 13.sp,
                        maxLines = 2, lineHeight = 19.sp,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                    )
                }
            }
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

/**
 * Presentation-only budget guidance. A missing limit is not an overspend.
 * No ledger value or user budget is ever changed here.
 */
internal fun jibikBudgetGuidance(spent: Long, limit: Long): String {
    if (limit <= 0L) return "هنوز سقف ماهانه مشخص نکردی. برای شروع، یک سقف مناسب تعیین کن."
    val used = spent.toDouble() / limit.toDouble()
    return when {
        used > 1.0 -> "از سقف تعیین‌شده عبور کردی؛ می‌تونی بودجه رو بازبینی کنی."
        used >= 0.85 -> "به سقف بودجه نزدیک شدی. خرج‌های پیش‌رو رو بررسی کن."
        spent == 0L -> "با اولین هزینه، پیشرفت بودجه اینجا نمایش داده می‌شه."
        else -> "تا اینجا هزینه‌ها در محدوده بودجه‌ات هستند."
    }
}

@Composable
private fun Budget(
    weeklyPanel: @Composable () -> Unit,
    spent: Long,
    budget: Long,
    monthTitle: String,
    categoryBudgets: List<CategoryBudgetEntity>,
    categoryUsage: List<CategoryBudgetUsage>,
    isSaving: Boolean,
    onEditCategory: (CategoryBudgetEntity?) -> Unit,
    onRemoveCategory: (String) -> Unit,
    modifier: Modifier,
    onEdit: () -> Unit,
    onAi: () -> Unit,
) {
    val hasLimit = budget > 0L
    val remaining = if (hasLimit) budget - spent else 0L
    val usage = if (hasLimit) (spent.toDouble() / budget.toDouble()).toFloat() else 0f
    Column(
        modifier = modifier.fillMaxSize().liquidBackdrop().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        JibikPageHeader(
            title = "بودجه‌بندی",
            subtitle = "سقف هزینه در " + monthTitle,
        )
        JibikHero(Modifier.fillMaxWidth(), illustration = JiboArtwork.BUDGET) {
            Text(
                if (!hasLimit) "بودجه این ماه" else if (remaining >= 0L)
                    "باقی‌مانده بودجه" else "میزان عبور از بودجه",
                color = Color(0xFFDCF3EA), fontSize = 13.sp,
            )
            JibikHeroAmount(
                amount = if (!hasLimit) "بدون سقف"
                    else money(if (remaining >= 0L) remaining else -remaining),
            )
            HorizontalDivider(color = Paper.copy(alpha = 0.18f))
            Text(
                "براساس تراکنش‌های واقعی ثبت‌شده در " + monthTitle,
                color = Color(0xFFE3F5ED), fontSize = 12.sp,
            )
        }
        JibikSurfaceCard {
            JibikSectionTitle("وضعیت مصرف بودجه")
            JibikFinancialDetail(
                label = "سقف تعیین‌شده",
                value = if (hasLimit) money(budget) else "تعیین نشده",
            )
            JibikFinancialDetail(
                label = "هزینه‌های این ماه",
                value = money(spent),
                emphasis = true,
            )
            if (hasLimit) {
                JibikProgressTrack(
                    progress = usage,
                    color = if (usage > 1f) Coral else if (usage >= 0.85f) Orange else Mint,
                )
            }
            Text(
                jibikBudgetGuidance(spent, budget),
                fontSize = 12.sp, color = if (hasLimit && usage > 1f) Coral else Quiet,
                lineHeight = 20.sp,
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
        weeklyPanel()
        CategoryBudgetsPanel(
            budgets = categoryBudgets,
            usage = categoryUsage,
            onAddOrEdit = onEditCategory,
            onRemove = onRemoveCategory,
            isSaving = isSaving,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun AddTransactionDialog(
    accounts: List<Account>,
    customCategories: List<CustomCategoryEntity>,
    initialKind: LedgerKind,
    initialTargetAccountId: String? = null,
    editing: Boolean = false,
    repeatRecord: LedgerRecord? = null,
    isSaving: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (LedgerRecord) -> Unit,
) {
    var kind by rememberSaveable(repeatRecord?.id) {
        mutableStateOf(repeatRecord?.kind ?: initialKind)
    }
    var amount by rememberSaveable(repeatRecord?.id) {
        mutableStateOf(repeatRecord?.amountToman?.toString().orEmpty())
    }
    var category by rememberSaveable(repeatRecord?.id) {
        mutableStateOf(repeatRecord?.category
            ?: if (initialKind == LedgerKind.INCOME) "حقوق و دستمزد" else "خوراک")
    }
    var showCategoryPicker by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable(repeatRecord?.id) {
        mutableStateOf(repeatRecord?.note.orEmpty())
    }
    var sourceId by rememberSaveable(repeatRecord?.id) {
        mutableStateOf(
            repeatRecord?.fromAccountId ?: repeatRecord?.toAccountId
                ?: accounts.firstOrNull()?.id.orEmpty()
        )
    }
    var targetId by rememberSaveable(initialTargetAccountId, repeatRecord?.id) {
        mutableStateOf(
            (initialTargetAccountId ?: repeatRecord?.toAccountId)
                ?.takeIf { id -> id != sourceId && accounts.any { it.id == id } }
                ?: accounts.firstOrNull { it.id != sourceId }?.id.orEmpty(),
        )
    }
    val originalDay = repeatRecord?.let { formatJalaliDueDate(it.timestampMillis) }.orEmpty()
    var date by rememberSaveable(repeatRecord?.id) { mutableStateOf(originalDay) }
    val editedTime = if (date == originalDay) repeatRecord?.timestampMillis else parseJalaliDueDate(date)
    val numericAmount = parseTomanDraft(amount)
    val sourceValid = accounts.any { it.id == sourceId }
    val targetValid = accounts.any { it.id == targetId }
    val categoryValid = when (kind) {
        LedgerKind.EXPENSE -> isSelectableCategory(category, false, customCategories)
        LedgerKind.INCOME -> isSelectableCategory(category, true, customCategories)
        LedgerKind.TRANSFER -> true
    }
    val canSave = (!editing || editedTime != null) && !isSaving && numericAmount != null && numericAmount > 0L &&
        sourceValid && (kind != LedgerKind.TRANSFER ||
            (accounts.size > 1 && targetValid && sourceId != targetId)) &&
        categoryValid
    JibikModalDialog(
        properties = JibikWideDialogProperties,
        modifier = Modifier.jibikDialogSurface(),
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            JibikDialogHeading(
                title = if (editing) "اصلاح تراکنش" else when (kind) {
                    LedgerKind.EXPENSE -> "ثبت هزینه"
                    LedgerKind.INCOME -> "ثبت درآمد"
                    LedgerKind.TRANSFER -> "انتقال بین حساب‌ها"
                },
                subtitle = "مبلغ و حساب را مشخص کن؛ قبل از ذخیره قابل اصلاح است.",
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                JibikSegmentedControl(
                    items = listOf(
                        LedgerKind.EXPENSE to "هزینه",
                        LedgerKind.INCOME to "درآمد",
                        LedgerKind.TRANSFER to "انتقال",
                    ),
                    selected = kind,
                    enabled = { option ->
                        !isSaving && (option != LedgerKind.TRANSFER || accounts.size > 1)
                    },
                    onSelected = { option ->
                        kind = option
                        category = if (option == LedgerKind.INCOME)
                            "حقوق و دستمزد" else "خوراک"
                        if (option == LedgerKind.TRANSFER && targetId == sourceId) {
                            targetId = accounts.firstOrNull { it.id != sourceId }?.id.orEmpty()
                        }
                    },
                )
                if (accounts.size < 2) {
                    JibikFormHint("برای انتقال پول بین حساب‌ها باید حداقل دو حساب داشته باشی.")
                }
                JibikTomanField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = when (kind) {
                        LedgerKind.EXPENSE -> "مبلغ هزینه"
                        LedgerKind.INCOME -> "مبلغ درآمد"
                        LedgerKind.TRANSFER -> "مبلغ انتقال"
                    },
                    enabled = !isSaving,
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
                    enabled = !isSaving,
                )
                if (kind == LedgerKind.TRANSFER) {
                    JibikAccountChoice(
                        title = "انتقال به حساب",
                        accounts = accounts.filter { it.id != sourceId },
                        selectedId = targetId,
                        onSelect = { targetId = it },
                        enabled = !isSaving,
                        emptyHint = "برای انتقال ابتدا حساب دوم بساز.",
                    )
                    JibikFormHint("انتقال بین حساب‌ها درآمد یا هزینه محسوب نمی‌شه.")
                } else {
                    JibikSectionTitle(
                        title = if (kind == LedgerKind.INCOME) "دسته درآمد" else "دسته هزینه",
                        subtitle = "برای گزارش دقیق‌تر، جزئیات رو انتخاب کن",
                    )
                    LiquidGlassCard(
                        onClick = if (isSaving) null else ({ showCategoryPicker = true }),
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
                            Text("تغییر", color = Forest, fontSize = 12.sp)
                        }
                    }
                    if (!categoryValid) {
                        JibikFormHint("یک دسته معتبر انتخاب کن.", isWarning = true)
                    }
                }
                if (editing) {
                    OutlinedTextField(value = date, onValueChange = { date = it },
                        label = { Text("تاریخ شمسی (سال/ماه/روز)") },
                        isError = editedTime == null, singleLine = true,
                        enabled = !isSaving, modifier = Modifier.fillMaxWidth())
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
                if (amount.isNotBlank() && (numericAmount == null || numericAmount <= 0L)) {
                    JibikFormHint("مبلغ باید عددی بزرگ‌تر از صفر باشد.", isWarning = true)
                }
                if (!sourceValid) {
                    JibikFormHint("ابتدا در بخش حساب‌ها یک حساب بساز.", isWarning = true)
                }
                if (kind == LedgerKind.TRANSFER && sourceValid && !targetValid) {
                    JibikFormHint("حساب مقصد را انتخاب کن.", isWarning = true)
                }
            }
        },
        confirmButton = {
            LiquidGlassButton(
                text = if (isSaving) "در حال ذخیره..."
                    else if (editing) "ذخیره اصلاحات" else when (kind) {
                        LedgerKind.EXPENSE -> "ثبت هزینه"
                        LedgerKind.INCOME -> "ثبت درآمد"
                        LedgerKind.TRANSFER -> "ثبت انتقال"
                    },
                enabled = canSave,
                tone = GlassTone.EMERALD,
                onClick = {
                    val value = numericAmount ?: return@LiquidGlassButton
                    val destination = if (kind == LedgerKind.TRANSFER) {
                        accounts.firstOrNull {
                            it.id == targetId && it.id != sourceId
                        }?.id ?: return@LiquidGlassButton
                    } else sourceId
                    onSave(
                        LedgerRecord(
                            id = if (editing) requireNotNull(repeatRecord).id else UUID.randomUUID().toString(),
                            kind = kind,
                            amountToman = value,
                            fromAccountId = if (kind == LedgerKind.INCOME) null else sourceId,
                            toAccountId = if (kind == LedgerKind.EXPENSE) null else destination,
                            category = if (kind == LedgerKind.TRANSFER) "" else category.trim(),
                            note = note.trim(),
                            timestampMillis = if (editing) requireNotNull(editedTime) else System.currentTimeMillis(),
                        ),
                    )
                },
            )
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
    var value by rememberSaveable(current) {
        mutableStateOf(if (current > 0L) current.toString() else "")
    }
    val parsed = parseTomanDraft(value)
    JibikModalDialog(
        properties = JibikWideDialogProperties,
        modifier = Modifier.jibikDialogSurface(),
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(26.dp),
        title = {
            JibikDialogHeading("ویرایش سقف بودجه", "کنترل خرج‌های همین ماه شمسی")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                JibikFormHint(
                    "سقف را خودت تعیین می‌کنی. برای هر ماه شمسی تکرار می‌شه " +
                        "و روی تراکنش‌های قبلی تأثیری نداره.",
                )
                JibikTomanField(
                    value = value,
                    onValueChange = { value = it },
                    label = "سقف ماهانه",
                )
                if (current > 0L) {
                    TextButton(
                        onClick = { onSave(0L) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "برداشتن سقف ماهانه",
                            color = JibikStyle.muted,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    JibikFormHint("برداشتن سقف فقط هشدار بودجه را غیرفعال می‌کند.")
                }
            }
        },
        confirmButton = {
            LiquidGlassButton(
                text = "ذخیره بودجه",
                enabled = parsed != null && parsed > 0L,
                onClick = { parsed?.let(onSave) },
                tone = GlassTone.EMERALD,
            )
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
