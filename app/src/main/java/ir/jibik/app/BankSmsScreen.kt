package ir.jibik.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibik.core.finance.Account
import ir.jibik.core.finance.LedgerRecord
import kotlinx.coroutines.launch

/** Bank message inbox; all inference and review stays on this Android device. */
@Composable
internal fun BankSmsScreen(
    store: LocalLedgerStore,
    accounts: List<Account>,
    customCategories: List<CustomCategoryEntity>,
    existingRecords: List<LedgerRecord>,
    onBack: () -> Unit,
    onRegistered: (LedgerRecord) -> Unit,
    obligations: List<FinancialObligationEntity> = emptyList(),
    payments: List<ObligationPaymentEntity> = emptyList(),
    rules: List<RecurringRule> = emptyList(),
    decisions: List<RecurringDecision> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var approved by remember { mutableStateOf(BankSmsSettings.approvedBanks(context)) }
    var pendingItems by remember { mutableStateOf<List<BankSmsCandidateEntity>>(emptyList()) }
    var sample by remember { mutableStateOf("") }
    var sampleResult by remember { mutableStateOf<BankSmsMatch?>(null) }
    var showAllBanks by rememberSaveable { mutableStateOf(false) }
    var showAllPending by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<BankSmsCandidateEntity?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var reviewError by remember { mutableStateOf("") }
    var linkedIds by remember { mutableStateOf(emptySet<String>()) }
    var completing by remember { mutableStateOf<LedgerRecord?>(null) }

    LaunchedEffect(Unit) {
        runCatching { pendingItems = store.bankSmsSuggestions(); linkedIds = store.bankLinkedTransactionIds() }
            .onFailure { notice = "صف پیام‌های بانکی بارگذاری نشد." }
    }

    Column(
        modifier.fillMaxSize().liquidBackdrop()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        JibikSectionTitle(
            title = "پیامک‌های بانکی",
            subtitle = "مدیریت خصوصی پیام‌های واریز و برداشت",
            trailing = {
                TextButton(onClick = onBack) { Text("بازگشت", color = JibikStyle.forest) }
            },
        )
        JibikHero(Modifier.fillMaxWidth(), illustration = JiboArtwork.TRANSACTIONS) {
            Text("جیبو و پیام‌های بانک", color = Color(0xFFDAF2E8), fontSize = 13.sp)
            Text("پولِ تو، تصمیمِ تو", color = Color.White,
                fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "پیام‌های واریز و برداشت را می‌شناسیم؛ قالب تأییدشده می‌تواند خودکار ثبت شود و سایر پیام‌ها برای بررسی می‌مانند.",
                color = Color(0xFFE2F3E9), fontSize = 12.sp, lineHeight = 20.sp,
            )
        }
        JibikFormHint(
            "همه پردازش‌ها روی گوشی انجام می‌شود. متن پیام، نام افراد، " +
                "موجودی بانک و رمزهای یک‌بارمصرف ذخیره یا به سرور ارسال نمی‌شوند."
        )
        BankSenderSettingsPanel(accounts = accounts, busy = busy,
            onScan = {
                busy = true
                scope.launch {
                    try {
                        val count = store.importBankSms(BankSmsInboxScanner.scan(context))
                        pendingItems = store.bankSmsSuggestions()
                        notice = "$count پیشنهاد جدید پیدا شد؛ هنوز هیچ مبلغی ثبت نشده است."
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { notice = "اسکن انجام نشد؛ دسترسی و سرشماره‌ها را بررسی کن." }
                    finally { busy = false }
                }
            })
        LiquidGlassCard(tone = GlassTone.CRYSTAL) {
            JibikSectionTitle("آزمایش نمونه پیام",
                "پیام نمونه را جای‌گذاری کن؛ متن آن ذخیره نمی‌شود")
            OutlinedTextField(
                value = sample,
                onValueChange = { sample = it.take(1500); sampleResult = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("متن پیام بانک ملی، بلو یا بانک دیگر") },
                minLines = 3, maxLines = 7,
            )
            LiquidGlassButton(
                text = "تحلیل نمونه روی گوشی",
                enabled = sample.isNotBlank(),
                tone = GlassTone.CRYSTAL,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    sampleResult = BankSmsParser.parse(
                        "", sample, System.currentTimeMillis(),
                        BankSmsSettings.fingerprintSalt(context),
                    )
                    notice = if (sampleResult == null)
                        "پیام مبهم است؛ نمونه دیگری را امتحان کن."
                    else "فقط تحلیل شد؛ هیچ تراکنشی ثبت نشده است."
                },
            )
            sampleResult?.let { row ->
                Text(
                    row.bankTitle + " • " +
                        (if (row.income) "واریز" else "برداشت") + " • " +
                        (row.amountToman?.let(::formatToman) ?: "مبلغ نامعتبر"),
                    color = JibikStyle.forest,
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
                if (row.bankId !in approved) TextButton(onClick = {
                    BankSmsSettings.allowBank(context, row.bankId, true)
                    approved = BankSmsSettings.approvedBanks(context)
                }) { Text("انتخاب این بانک", color = JibikStyle.forest) }
            }
        }
        val autoPending = existingRecords.filter(::needsBankAutoDetails)
        if (autoPending.isNotEmpty()) {
            JibikSectionTitle("تکمیل تراکنش‌های خودکار", autoPending.size.toString() + " مورد بدون توضیح")
            autoPending.take(30).forEach { row ->
                JibikSurfaceCard {
                    JibikFinancialDetail(if (row.kind == ir.jibik.core.finance.LedgerKind.INCOME) "واریز ثبت‌شده" else "برداشت ثبت‌شده",
                        formatToman(row.amountToman), emphasis = true)
                    JibikFormHint(formatJalaliDueDate(row.timestampMillis))
                    LiquidGlassButton("بابت چه چیزی بود؟", modifier = Modifier.fillMaxWidth(), enabled = !busy,
                        onClick = { completing = row })
                }
            }
        }
        val pending = pendingItems.filter { it.state == "PENDING" }
        val handled = pendingItems.size - pending.size
        JibikSectionTitle(
            "صف بررسی تراکنش‌ها",
            pending.size.toString() + " پیشنهاد تأییدنشده",
            trailing = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { pendingItems = store.bankSmsSuggestions() }
                            .onFailure { notice = "بارگذاری مجدد ناموفق بود." }
                    }
                }) { Text("تازه‌سازی", color = JibikStyle.forest) }
            },
        )
        if (pending.isEmpty()) JibikStatePanel(
            title = "پیام جدیدی در صف نیست",
            description = "پیام‌های قابل تشخیص از سرشماره‌های معرفی‌شده پس از دریافت یا اسکن در این بخش نمایش داده می‌شوند.",
        )
        pending.take(if (showAllPending) 100 else 12).forEach { item ->
            LiquidGlassCard(tone = GlassTone.CRYSTAL) {
                Row(Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            item.bankTitle + " • " +
                                (if (item.income) "واریز" else "برداشت"),
                            color = JibikStyle.ink,
                            fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        )
                        Text(
                            formatJalaliDueDate(item.receivedAtMillis) +
                                (if (item.accountLast4.isNotEmpty())
                                    " • حساب ⋯" + item.accountLast4 else ""),
                            color = JibikStyle.muted, fontSize = 11.sp,
                        )
                    }
                    Text(
                        formatToman(item.amountRial / 10L),
                        color = JibikStyle.forest,
                        fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    )
                }
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LiquidGlassButton(
                        text = "بررسی و ثبت",
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        tone = GlassTone.EMERALD,
                        onClick = { reviewError = ""; selected = item },
                    )
                    TextButton(
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    store.ignoreBankSms(item.id)
                                    pendingItems = store.bankSmsSuggestions()
                                    notice = "پیشنهاد نادیده گرفته شد."
                                } catch (_: Exception) {
                                    notice = "تغییر وضعیت ناموفق بود."
                                } finally { busy = false }
                            }
                        },
                    ) { Text("نادیده‌گرفتن", color = JibikStyle.muted) }
                }
            }
        }
        if (pending.size > 12) TextButton(onClick = {
            showAllPending = !showAllPending
        }) {
            Text(
                if (showAllPending) "نمایش ۱۲ مورد اول" else "مشاهده موارد بیشتر",
                color = JibikStyle.forest,
            )
        }
        if (handled > 0) JibikFormHint(
            handled.toString() + " مورد قبلاً ثبت یا نادیده گرفته شده است."
        )
        if (busy) CircularProgressIndicator(
            color = JibikStyle.forest, modifier = Modifier.size(24.dp)
        )
        notice?.let { JibikFormHint(it) }
    }
    completing?.let { row ->
        BankAutoDetailsDialog(row, customCategories, busy, onDismiss = { if (!busy) completing = null },
            onSave = { note, category ->
                busy = true
                scope.launch {
                    try {
                        val updated = store.completeBankAutoDetails(row.id, note, category)
                        completing = null
                        onRegistered(updated)
                        notice = "توضیح و دسته‌بندی ذخیره شد؛ مبلغ و حساب تغییر نکرد."
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { notice = "تکمیل تراکنش انجام نشد." }
                    finally { busy = false }
                }
            })
    }
    selected?.let { candidate ->
        BankSmsReviewDialog(
            candidate = candidate,
            accounts = accounts,
            customCategories = customCategories,
            existingRecords = existingRecords,
            busy = busy,
            onDismiss = { if (!busy) selected = null },
            error = reviewError, linkedIds = linkedIds,
            suggestedAccountId = BankSenderRegistry.active(context)
                .filter { it.bankId == candidate.bankId && it.accountLast4 == candidate.accountLast4 }
                .map { it.accountId }.distinct().singleOrNull(),
            targetsFor = { record -> bankPaymentOptions(record, obligations, payments, rules, decisions, System.currentTimeMillis()) },
            onConfirm = { record, link, target ->
                busy = true; reviewError = ""
                scope.launch {
                    try {
                        val posted = store.reconcileBankPayment(candidate.id, record, link, target)
                        selected = null
                        onRegistered(posted)
                        pendingItems = store.bankSmsSuggestions()
                        linkedIds = store.bankLinkedTransactionIds()
                        BankSmsNotifications.clear(context)
                        notice = if (link) "پیام به تراکنش قبلی متصل شد." else "تراکنش پس از تأیید ثبت شد."
                    } catch (_: Exception) {
                        reviewError = "عملیات کامل نشد؛ وضعیت پرداخت و پیام را دوباره بررسی کن."
                        notice = reviewError
                    } finally { busy = false }
                }
            },
        )
    }
}

@Composable
private fun BankAutoDetailsDialog(row: LedgerRecord, custom: List<CustomCategoryEntity>, busy: Boolean,
    onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var note by remember(row.id) { mutableStateOf("") }
    var category by remember(row.id) { mutableStateOf(row.category) }
    var picker by remember { mutableStateOf(false) }
    val income = row.kind == ir.jibik.core.finance.LedgerKind.INCOME
    JibikModalDialog(onDismissRequest = onDismiss, modifier = Modifier.jibikDialogSurface(),
        properties = JibikWideDialogProperties,
        title = { JibikDialogHeading("تکمیل تراکنش خودکار", formatToman(row.amountToman)) },
        text = {
            Column(Modifier.fillMaxWidth().jibikScrollableBody(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(note, { note = it.take(250) }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (income) "بابت چه چیزی دریافت کردی؟" else "بابت چه چیزی پرداخت کردی؟") }, minLines = 2, enabled = !busy)
                TextButton(enabled = !busy, onClick = { picker = true }) { Text("دسته: " + category) }
                JibikFormHint("مبلغ، حساب و تاریخ از پیامک ثبت شده‌اند؛ فقط توضیح و دسته تکمیل می‌شوند.")
            }
        },
        confirmButton = { LiquidGlassButton("ذخیره توضیح", enabled = !busy && note.isNotBlank(), onClick = { onSave(note, category) }) },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("بعداً") } })
    if (picker) CategoryPickerDialog(income = income, selected = category, custom = custom,
        onSelected = { category = it; picker = false }, onDismiss = { picker = false })
}
