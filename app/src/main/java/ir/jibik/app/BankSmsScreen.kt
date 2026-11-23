package ir.jibik.app

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    onBack: () -> Unit,
    onRegistered: (LedgerRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var approved by remember { mutableStateOf(BankSmsSettings.approvedBanks(context)) }
    var enabled by remember { mutableStateOf(BankSmsSettings.isEnabled(context)) }
    var permission by remember { mutableStateOf(BankSmsSettings.permissionGranted(context)) }
    var pendingItems by remember { mutableStateOf<List<BankSmsCandidateEntity>>(emptyList()) }
    var found by remember { mutableStateOf<List<BankSmsMatch>>(emptyList()) }
    var sample by rememberSaveable { mutableStateOf("") }
    var sampleResult by remember { mutableStateOf<BankSmsMatch?>(null) }
    var showAllBanks by rememberSaveable { mutableStateOf(false) }
    var showAllPending by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<BankSmsCandidateEntity?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    val smsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permission = BankSmsSettings.permissionGranted(context)
        notice = if (permission) "دسترسی برقرار شد؛ اکنون بانک‌ها را انتخاب کن."
            else "اجازه SMS داده نشد. می‌توانی نمونه پیام را بدون مجوز بررسی کنی."
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notice = if (granted) "اعلان بررسی پیام‌های بانکی فعال است."
            else "پیشنهادها ذخیره می‌شوند ولی اعلان گوشی نمایش داده نمی‌شود."
    }

    LaunchedEffect(Unit) {
        runCatching { pendingItems = store.bankSmsSuggestions() }
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
            subtitle = "واریز و برداشت، فقط با بررسی و تأیید",
            trailing = {
                TextButton(onClick = onBack) { Text("بازگشت", color = JibikStyle.forest) }
            },
        )
        JibikHero(Modifier.fillMaxWidth()) {
            Text("جیبو و پیام‌های بانک", color = Color(0xFFDAF2E8), fontSize = 13.sp)
            Text("پولِ تو، تصمیمِ تو", color = Color.White,
                fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "پیام‌های واریز و برداشت را می‌شناسیم؛ " +
                    "اما هیچ مبلغی بدون تأیید تو به حساب‌ها اضافه یا از آن‌ها کم نمی‌شود.",
                color = Color(0xFFE2F3E9), fontSize = 12.sp, lineHeight = 20.sp,
            )
        }
        JibikFormHint(
            "همه پردازش‌ها روی گوشی انجام می‌شود. متن پیام، نام افراد، " +
                "موجودی بانک و رمزهای یک‌بارمصرف ذخیره یا به سرور ارسال نمی‌شوند."
        )
        LiquidGlassCard(tone = GlassTone.CRYSTAL) {
            JibikSectionTitle(
                "۱. مجوز پیامک",
                if (permission) "خواندن و دریافت SMS مجاز است"
                else "تنها پس از درخواست خودت فعال می‌شود",
            )
            LiquidGlassButton(
                text = if (permission) "✓ بررسی وضعیت دسترسی"
                    else "دریافت اجازه خواندن پیامک‌ها",
                onClick = {
                    smsPermission.launch(arrayOf(
                        Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS
                    ))
                },
                tone = if (permission) GlassTone.CRYSTAL else GlassTone.HONEY,
                modifier = Modifier.fillMaxWidth(),
            )
            JibikFormHint("بدون این مجوز همچنان می‌توانی نمونه پیام بانک را دستی آزمایش کنی.")
        }
        LiquidGlassCard(tone = GlassTone.CRYSTAL) {
            JibikSectionTitle("۲. انتخاب بانک‌ها", "فقط بانک‌های تأییدشده بررسی می‌شوند")
            val visible = if (showAllBanks) BankSmsParser.banks
                else BankSmsParser.banks.take(6)
            visible.chunked(2).forEach { banks ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    banks.forEach { bank ->
                        JibikGlassChip(
                            text = bank.title,
                            selected = bank.id in approved,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                BankSmsSettings.allowBank(
                                    context, bank.id, bank.id !in approved,
                                )
                                approved = BankSmsSettings.approvedBanks(context)
                                if (approved.isEmpty() && enabled) {
                                    BankSmsSettings.setEnabled(context, false)
                                    enabled = false
                                }
                            },
                        )
                    }
                }
            }
            TextButton(onClick = { showAllBanks = !showAllBanks }) {
                Text(
                    if (showAllBanks) "نمایش بانک‌های پرکاربرد"
                    else "مشاهده تمام بانک‌های قابل شناسایی",
                    color = JibikStyle.forest,
                )
            }
        }
        LiquidGlassCard(tone = GlassTone.CRYSTAL) {
            JibikSectionTitle(
                "۳. بررسی پیامک‌های قبلی",
                "فقط با لمس دکمه، حداکثر ۴۰۰ پیام آخر اسکن می‌شود",
            )
            LiquidGlassButton(
                text = if (busy) "در حال بررسی..." else "شناسایی پیام‌های بانکی موجود",
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                tone = GlassTone.HONEY,
                onClick = {
                    if (!permission) {
                        notice = "ابتدا به برنامه اجازه خواندن پیامک بده."
                    } else {
                        busy = true
                        scope.launch {
                            try {
                                found = BankSmsInboxScanner.scan(context)
                                notice = found.size.toString() +
                                    " پیام مالی قابل‌تشخیص پیدا شد."
                            } catch (_: Exception) {
                                notice = "اسکن ممکن نشد؛ اجازه و تنظیمات گوشی را بررسی کن."
                            } finally { busy = false }
                        }
                    }
                },
            )
            if (found.isNotEmpty()) {
                found.groupBy { it.bankId }.forEach { (bankId, rows) ->
                    Text(
                        (BankSmsParser.banks.firstOrNull { it.id == bankId }?.title
                            ?: "بانک") + " • " + rows.size + " پیام",
                        color = JibikStyle.ink, fontSize = 12.sp,
                    )
                }
                val chosen = found.filter { it.bankId in approved }
                LiquidGlassButton(
                    text = "افزودن " + chosen.size + " پیام منتخب به صف بررسی",
                    enabled = chosen.isNotEmpty() && !busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                val added = store.importBankSms(chosen)
                                pendingItems = store.bankSmsSuggestions()
                                found = emptyList()
                                notice = added.toString() + " پیشنهاد جدید آماده بررسی شد."
                            } catch (_: Exception) {
                                notice = "انتقال پیشنهادها موفق نبود."
                            } finally { busy = false }
                        }
                    },
                )
            }
        }
        LiquidGlassCard(tone = GlassTone.CRYSTAL) {
            JibikSectionTitle("۴. آزمایش نمونه پیام",
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
        LiquidGlassCard(tone = GlassTone.CRYSTAL) {
            JibikSectionTitle(
                "۵. شناسایی خودکار پیامک‌های جدید",
                if (enabled) "روشن • هر پیام نیازمند تأیید است"
                else "خاموش • هیچ پیامکی در پس‌زمینه بررسی نمی‌شود",
            )
            LiquidGlassButton(
                text = if (enabled) "خاموش‌کردن پایش خودکار"
                    else "فعال‌کردن بانک‌های منتخب",
                modifier = Modifier.fillMaxWidth(),
                tone = if (enabled) GlassTone.CRYSTAL else GlassTone.EMERALD,
                onClick = {
                    if (enabled) {
                        BankSmsSettings.setEnabled(context, false)
                        BankSmsNotifications.clear(context)
                        enabled = false
                        notice = "پایش خاموش شد؛ پیشنهادهای قبلی محفوظ‌اند."
                    } else if (BankSmsSettings.setEnabled(context, true)) {
                        enabled = true
                        notice = "پایش فعال است؛ ثبت مالی همیشه به تأیید تو نیاز دارد."
                        if (Build.VERSION.SDK_INT >= 33) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else {
                        notice = "ابتدا مجوز SMS و دست‌کم یک بانک را فعال کن."
                    }
                },
            )
            JibikFormHint(
                "برای نمایش اعلان به مجوز اعلان Android نیاز است. " +
                    "قطع اجازه SMS از تنظیمات گوشی، پایش خودکار را متوقف می‌کند."
            )
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
            description = "پس از اسکن یا دریافت SMS از بانک منتخب، " +
                "پیشنهادها برای تأیید اینجا ظاهر می‌شوند.",
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
                        onClick = { selected = item },
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
    selected?.let { candidate ->
        BankSmsReviewDialog(
            candidate = candidate,
            accounts = accounts,
            customCategories = customCategories,
            busy = busy,
            onDismiss = { if (!busy) selected = null },
            onConfirm = { record ->
                busy = true
                scope.launch {
                    try {
                        val posted = store.registerBankSms(candidate.id, record)
                        pendingItems = store.bankSmsSuggestions()
                        selected = null
                        onRegistered(posted)
                        BankSmsNotifications.clear(context)
                        notice = "تراکنش پس از تأیید ثبت شد."
                    } catch (_: Exception) {
                        notice = "ثبت ناموفق بود؛ حساب و تکراری‌نبودن پیام را بررسی کن."
                    } finally { busy = false }
                }
            },
        )
    }
}
