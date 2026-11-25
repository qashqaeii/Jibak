package ir.jibik.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
internal fun AssetsScreen(store: LocalLedgerStore, assets: List<AssetPosition>,
    balances: Map<String, Long>, obligations: List<FinancialObligationEntity>,
    payments: List<ObligationPaymentEntity>, accounts: List<ir.jibik.core.finance.Account>, modifier: Modifier = Modifier,
    onBack: () -> Unit, onChanged: (List<AssetPosition>) -> Unit) {
    val worth = remember(assets, balances, obligations, payments) { netWorth(balances, assets, obligations, payments) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    var create by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var action by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val selected = assets.firstOrNull { it.holding.id == selectedId }
    fun save(operation: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = ""
        scope.launch {
            try {
                operation()
                create = false
                action = null
                selectedId = null
                onChanged(store.loadAssets())
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "عملیات کامل نشد؛ اطلاعات را بررسی و دوباره تلاش کن." }
            finally { busy = false }
        }
    }
    BackHandler(enabled = !create && selected == null && !busy, onBack = onBack)
    Column(modifier.fillMaxSize().liquidBackdrop().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        JibikPageHeader(title = "دارایی و ثروت", subtitle = "تصویر یکپارچه از وضعیت مالی تو", onBack = onBack)
        JibikHero(Modifier.fillMaxWidth(), illustration = JiboArtwork.GOALS) {
            Text("ارزش خالص ثبت‌شده", color = Color(0xFFDAF1E9))
            JibikHeroAmount(forecastMoney(worth.net))
            HorizontalDivider(color = Color.White.copy(alpha = .2f))
            Text("دارایی‌ها: " + forecastMoney(worth.assets), color = Color.White)
            Text("بدهی‌ها: " + forecastMoney(worth.liabilities), color = Color.White)
        }
        JibikSurfaceCard {
            JibikSectionTitle("ترکیب ثروت", "مبالغ ثبت‌شده، نه قیمت لحظه‌ای بازار")
            JibikFinancialDetail("موجودی مثبت حساب‌ها", forecastMoney(worth.cash))
            JibikFinancialDetail("دارایی‌های غیرنقدی", forecastMoney(worth.nonCash))
            JibikFinancialDetail("مطالبات باقی‌مانده", forecastMoney(worth.receivables))
            AssetAllocationBar(worth, assets)
            HorizontalDivider()
            JibikFinancialDetail("مانده منفی حساب‌ها", forecastMoney(worth.overdrafts))
            JibikFinancialDetail("تعهدات پرداخت‌نشده", forecastMoney(worth.obligations))
            JibikFormHint("ارزش خالص بر اساس تعهدات ثبت‌شده است؛ اقساط ممکن است شامل سود آینده باشند. پاکت‌ها و پس‌اندازها دوباره به دارایی اضافه نمی‌شوند.")
        }
        WealthHistoryCard(store)
        JibikSectionTitle("سبد دارایی", "طلا، ارز، ملک، خودرو و سرمایه‌گذاری")
        LiquidGlassButton("＋ ثبت دارایی جدید", modifier = Modifier.fillMaxWidth(),
            onClick = { error = ""; create = true }, enabled = !busy)
        JibikSegmentedControl(items = listOf(false to "فعال", true to "بایگانی"),
            selected = showArchived, onSelected = { showArchived = it })
        val displayed = assets.filter { it.archived == showArchived }
        if (displayed.isEmpty()) JibikStatePanel(
            title = if (showArchived) "دارایی بایگانی‌شده‌ای نیست" else "سبد دارایی را بساز",
            description = if (showArchived) "سوابق دارایی‌های بایگانی‌شده اینجا می‌مانند." else "ارزش فعلی دارایی‌هایی که خارج از حساب‌های نقدی داری را وارد کن.",
        )
        displayed.forEach { item ->
            LiquidGlassCard(onClick = { selectedId = item.holding.id; action = null; error = "" }, tone = GlassTone.CRYSTAL) {
                JibikSectionTitle(item.holding.title, ASSET_KINDS.getValue(item.holding.kind))
                Text(formatToman(item.valueToman), style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold, color = JibikStyle.forest)
                Text("آخرین ارزش‌گذاری: " + formatJalaliDueDate(item.valuationAtMillis), color = JibikStyle.muted)
                item.valuationChange?.let { JibikFinancialDetail(if (item.sold) "اختلاف دریافتی فروش و بهای خرید" else "تغییر نسبت به بهای ثبت‌شده", forecastMoney(it)) }
                Text(when { item.sold -> "فروخته‌شده · مشاهده سوابق"; item.archived -> "مشاهده سوابق و بازیابی"; else -> "جزئیات و به‌روزرسانی ارزش" }, color = JibikStyle.forest)
            }
        }
        if (error.isNotBlank() && !create && selected == null) JibikFormHint(error, isWarning = true)
    }
    if (create) NewAssetDialog(busy, error, accounts, onDismiss = { if (!busy) create = false }) { holding, value, account ->
        save { if (account == null) store.addAsset(holding, value) else store.buyAsset(holding, value, account, UUID.randomUUID().toString()) }
    }
    selected?.let { item ->
        if (action == null) JibikModalDialog(properties = JibikWideDialogProperties,
            modifier = Modifier.jibikDialogSurface(), onDismissRequest = { if (!busy) selectedId = null },
            title = { JibikDialogHeading(item.holding.title, ASSET_KINDS.getValue(item.holding.kind)) },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    JibikFinancialDetail("آخرین ارزش ثبت‌شده", formatToman(item.valueToman), emphasis = true)
                    item.holding.costBasisToman?.let { JibikFinancialDetail("بهای ثبت‌شده", formatToman(it)) }
                    JibikSectionTitle("تاریخچه", "آخرین ۲۰ تغییر")
                    item.history.asReversed().take(20).forEach { event ->
                        Text(formatJalaliDueDate(event.atMillis) + " · " + when (event.kind) {
                            "ARCHIVE" -> "بایگانی"; "RESTORE" -> "بازگشت به سبد"; "SALE" -> "فروش: " + formatToman(event.valueToman); else -> formatToman(event.valueToman)
                        })
                        if (event.note.isNotBlank()) Text(event.note, color = JibikStyle.muted)
                        HorizontalDivider()
                    }
                    if (!item.archived) LiquidGlassButton("ارزش‌گذاری جدید", onClick = { action = "VALUE" })
                    if (!item.archived) TextButton(onClick = { action = "SALE" }) { Text("ثبت فروش کامل") }
                    if (!item.sold) TextButton(onClick = { action = if (item.archived) "RESTORE" else "ARCHIVE" }) {
                        Text(if (item.archived) "بازگرداندن به سبد" else "بایگانی دارایی")
                    }
                }
            }, confirmButton = { LiquidGlassButton("بستن", onClick = { selectedId = null }) })
        else if (action == "SALE") AssetSaleDialog(item, accounts, busy, error, onDismiss = { if (!busy) action = null }) { amount, account ->
            save { store.sellAsset(item, amount, account, UUID.randomUUID().toString()) }
        }
        else AssetChangeDialog(item, requireNotNull(action), busy, error,
            onDismiss = { if (!busy) action = null },
            onSave = { value, note -> save { store.changeAsset(item, requireNotNull(action), value, note) } })
    }
}

@Composable
private fun NewAssetDialog(busy: Boolean, error: String, accounts: List<ir.jibik.core.finance.Account>, onDismiss: () -> Unit,
    onSave: (AssetHolding, Long, String?) -> Unit) {
    var title by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("GOLD") }
    var menu by remember { mutableStateOf(false) }
    var value by rememberSaveable { mutableStateOf("") }
    var cost by rememberSaveable { mutableStateOf("") }
    val amount = parseTomanDraft(value)
    val basis = parseTomanDraft(cost)
    var purchase by rememberSaveable { mutableStateOf(false) }
    var account by rememberSaveable { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }
    JibikModalDialog(properties = JibikWideDialogProperties, modifier = Modifier.jibikDialogSurface(),
        onDismissRequest = onDismiss, title = { JibikDialogHeading("دارایی جدید", "ثبت ارزش دستی") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it.take(60) }, label = { Text("نام دارایی") }, singleLine = true, enabled = !busy)
                Box {
                    OutlinedButton(onClick = { menu = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Text("نوع: " + ASSET_KINDS.getValue(kind))
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        ASSET_KINDS.forEach { (key, label) -> DropdownMenuItem(text = { Text(label) },
                            onClick = { kind = key; menu = false }) }
                    }
                }
                JibikTomanField(value, { value = it }, "ارزش کل فعلی", allowZero = true, enabled = !busy)
                JibikTomanField(cost, { cost = it }, "بهای خرید (اختیاری)", allowZero = true, enabled = !busy)
                JibikSegmentedControl(listOf(false to "دارایی موجود", true to "خرید جدید"), purchase, onSelected = { if (!busy) purchase = it })
                if (purchase) JibikAccountChoice("حساب پرداخت", accounts, account, { account = it }, enabled = !busy)
                JibikFormHint(if (purchase) "بهای خرید یک بار از حساب کسر می‌شود. اگر قبلاً پرداخت را ثبت کرده‌ای، حالت دارایی موجود را انتخاب کن." else "فقط ارزش دارایی موجود ثبت می‌شود؛ مانده حساب تغییر نمی‌کند.")
                if (error.isNotBlank()) JibikFormHint(error, isWarning = true)
            }
        },
        confirmButton = { LiquidGlassButton("ثبت دارایی", enabled = !busy && title.isNotBlank() &&
            amount != null && (cost.isBlank() || basis != null) && (!purchase || (basis != null && basis > 0 && accounts.any { it.id == account })), onClick = {
            onSave(AssetHolding(UUID.randomUUID().toString(), title.trim(), kind, basis, System.currentTimeMillis()), requireNotNull(amount), account.takeIf { purchase })
        }) },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("انصراف") } })
}

@Composable
private fun AssetChangeDialog(item: AssetPosition, action: String, busy: Boolean, error: String,
    onDismiss: () -> Unit, onSave: (Long, String) -> Unit) {
    var value by rememberSaveable(item.holding.id, action) { mutableStateOf(item.valueToman.toString()) }
    var note by rememberSaveable(item.holding.id, action) { mutableStateOf("") }
    val amount = if (action == "VALUE") parseTomanDraft(value) else item.valueToman
    val heading = when (action) { "VALUE" -> "ارزش‌گذاری جدید"; "ARCHIVE" -> "بایگانی دارایی"; else -> "بازگرداندن دارایی" }
    JibikModalDialog(properties = JibikWideDialogProperties, modifier = Modifier.jibikDialogSurface(),
        onDismissRequest = onDismiss, title = { JibikDialogHeading(heading, item.holding.title) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (action == "VALUE") JibikTomanField(value, { value = it }, "ارزش کل جدید", allowZero = true, enabled = !busy)
                OutlinedTextField(note, { note = it.take(200) }, label = { Text("یادداشت (اختیاری)") }, enabled = !busy)
                JibikFormHint(when (action) {
                    "ARCHIVE" -> "از ارزش خالص خارج می‌شود؛ سوابق باقی می‌مانند. فروش یا دریافتی ثبت نمی‌شود."
                    "RESTORE" -> "با آخرین ارزش ثبت‌شده به سبد برمی‌گردد؛ می‌توانی سپس ارزش را به‌روز کنی."
                    else -> "فقط برآورد ارزش دارایی تغییر می‌کند؛ درآمد، هزینه و مانده حساب ثابت می‌مانند."
                })
                if (error.isNotBlank()) JibikFormHint(error, isWarning = true)
            }
        },
        confirmButton = { LiquidGlassButton(heading, enabled = !busy && amount != null,
            onClick = { onSave(requireNotNull(amount), note.trim()) }) },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("انصراف") } })
}

@Composable
private fun AssetSaleDialog(item: AssetPosition, accounts: List<ir.jibik.core.finance.Account>, busy: Boolean, error: String,
    onDismiss: () -> Unit, onSave: (Long, String) -> Unit) {
    var draft by rememberSaveable { mutableStateOf(item.valueToman.toString()) }
    var account by rememberSaveable { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }
    val amount = parseTomanDraft(draft)
    JibikModalDialog(properties = JibikWideDialogProperties, modifier = Modifier.jibikDialogSurface(), onDismissRequest = onDismiss,
        title = { JibikDialogHeading("ثبت فروش کامل", item.holding.title) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                JibikTomanField(draft, { draft = it }, "دریافتی خالص فروش", enabled = !busy)
                JibikAccountChoice("حساب دریافت", accounts, account, { account = it }, enabled = !busy)
                JibikFormHint("فقط اگر فروش و دریافت وجه هنوز ثبت نشده‌اند تأیید کن. دارایی از سبد خارج و وجه یک بار ثبت می‌شود. بازگردانی خودکار معامله فعلاً در دسترس نیست.")
                if (error.isNotBlank()) JibikFormHint(error, isWarning = true)
            }
        }, confirmButton = { LiquidGlassButton("تأیید فروش", enabled = !busy && amount != null && amount > 0 && accounts.any { it.id == account }, onClick = { onSave(requireNotNull(amount), account) }) },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("انصراف") } })
}
