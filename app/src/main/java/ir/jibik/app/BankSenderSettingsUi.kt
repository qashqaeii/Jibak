package ir.jibik.app

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.jibik.core.finance.Account
import kotlinx.coroutines.launch

@Composable
internal fun BankSenderSettingsPanel(accounts: List<Account>, busy: Boolean, onScan: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf(BankSenderRegistry.list(context)) }
    var allowed by remember { mutableStateOf(BankSmsSettings.permissionGranted(context)) }
    var monitoring by remember { mutableStateOf(BankSmsSettings.isEnabled(context)) }
    var add by remember { mutableStateOf(false) }
    var discovered by remember { mutableStateOf(emptyList<String>()) }
    var discovering by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var remove by remember { mutableStateOf<BankSenderProfile?>(null) }
    var training by remember { mutableStateOf<BankSenderProfile?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        allowed = BankSmsSettings.permissionGranted(context)
        monitoring = BankSmsSettings.isEnabled(context)
        if (!allowed) error = "مجوز کامل پیامک صادر نشد. می‌توانی سرشماره را دستی معرفی کنی."
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun refresh() {
        profiles = BankSenderRegistry.list(context)
        allowed = BankSmsSettings.permissionGranted(context)
        monitoring = BankSmsSettings.isEnabled(context)
    }
    JibikSurfaceCard {
        JibikSectionTitle("دسترسی و پایش بانکی", if (monitoring) "پایش فعال · فقط سرشماره‌های مجاز" else "پایش غیرفعال")
        if (!BuildConfig.BANK_SMS_AVAILABLE) JibikFormHint("این APK بدون مجوز پیامک ساخته شده است. معرفی دستی بانک امکان‌پذیر است؛ برای دریافت واقعی از نسخه آزمایشی بانکی استفاده کن.")
        else {
            JibikFormHint("خواندن پیامک فقط برای یافتن فرستنده و تحلیل پیام بانک‌های معرفی‌شده است؛ رمزها ذخیره نمی‌شوند.")
            if (!allowed) LiquidGlassButton("اعطای دسترسی پیامک", modifier = Modifier.fillMaxWidth(), onClick = {
                launcher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
            })
            else JibikStatusText("دسترسی پیامک برقرار است")
            if (android.os.Build.VERSION.SDK_INT >= 33 && allowed &&
                !androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                TextButton(onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("فعال‌سازی اعلان‌ها") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("پایش پیام‌های جدید", modifier = Modifier.weight(1f))
                Switch(checked = monitoring, enabled = allowed && profiles.any { it.enabled },
                    onCheckedChange = { value ->
                        if (!BankSmsSettings.setEnabled(context, value)) error = "ابتدا مجوز و سرشماره فعال را مشخص کن."
                        refresh()
                    })
            }
            JibikFormHint("فقط قالب‌هایی که جداگانه برای ثبت خودکار تأیید می‌کنی می‌توانند موجودی را تغییر دهند؛ سایر پیام‌ها وارد صندوق بررسی می‌شوند.")
            JibikFormHint("با باز شدن برنامه، حداکثر ۴۰۰ پیام اخیر از ۷ روز گذشته و پس از فعال‌سازی پایش بررسی می‌شوند. پیام جامانده فقط پیشنهاد می‌شود.")
            LiquidGlassButton("اسکن پیامک‌های گذشته", onClick = onScan,
                enabled = allowed && profiles.any { it.enabled } && !busy, modifier = Modifier.fillMaxWidth(), tone = GlassTone.CRYSTAL)
        }
    }
    JibikSurfaceCard {
        JibikSectionTitle("بانک‌های من", "سرشماره دقیق و حساب مقصد یا مبدأ")
        if (profiles.isEmpty()) JibikFormHint("هنوز سرشماره‌ای معرفی نکرده‌ای.")
        profiles.forEach { profile ->
            JibikSurfaceCard {
                JibikSectionTitle(BankSmsParser.banks.first { it.id == profile.bankId }.title,
                    profile.sender + " · " + if (profile.accountLast4.isBlank()) "پیام بدون شناسه حساب" else "حساب ••••" + profile.accountLast4)
                val account = accounts.firstOrNull { it.id == profile.accountId && !it.archived }
                JibikFinancialDetail("حساب جیبیک", account?.title ?: "حساب در دسترس نیست")
                JibikStatusText(if (profile.autoTemplates.isEmpty()) "فقط بررسی" else "ثبت خودکار: " + profile.autoTemplates.size + " قالب")
                if ((profile.accountLast4.isNotBlank() || profile.bankId == "blu" &&
                    profiles.count { it.sender == profile.sender } == 1) && account != null && profile.enabled) {
                    TextButton(onClick = { training = profile }) { Text("آموزش قالب ثبت خودکار") }
                    if (profile.autoTemplates.isNotEmpty()) TextButton(onClick = {
                        runCatching { BankSenderRegistry.stopAuto(context, profile.id); refresh() }
                            .onFailure { error = "توقف ثبت خودکار انجام نشد." }
                    }) { Text("توقف ثبت خودکار این حساب") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    TextButton(onClick = { remove = profile }) { Text("حذف") }
                    Switch(checked = profile.enabled, onCheckedChange = {
                        runCatching { BankSenderRegistry.setEnabled(context, profile.id, it); refresh() }
                            .onFailure { error = "تنظیمات ذخیره نشد." }
                    })
                }
            }
        }
        LiquidGlassButton("افزودن سرشماره بانک", modifier = Modifier.fillMaxWidth(), onClick = { discovered = emptyList(); add = true })
        if (BuildConfig.BANK_SMS_AVAILABLE && allowed) TextButton(enabled = !discovering, onClick = {
            discovering = true
            scope.launch {
                try { discovered = discoverSmsSenders(context); add = true }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { error = "فهرست فرستنده‌ها خوانده نشد." }
                finally { discovering = false }
            }
        }) { Text(if (discovering) "در حال بررسی…" else "انتخاب از فرستنده‌های پیامک") }
        if (error.isNotBlank()) JibikFormHint(error, isWarning = true)
    }
    if (add) BankSenderEditor(accounts, discovered, onDismiss = { add = false; discovered = emptyList() }) { bank, sender, last4, account ->
        runCatching { BankSenderRegistry.save(context, bank, sender, last4, account); refresh() }
            .onSuccess { add = false; discovered = emptyList(); error = "" }
            .onFailure { error = "این سرشماره یا حساب تکراری است یا اطلاعات معتبر نیست."; add = false; discovered = emptyList() }
    }
    training?.let { profile ->
        BankAutoTrainingDialog(profile, onDismiss = { training = null }, onApprove = { template ->
            runCatching { BankSenderRegistry.approveAutoTemplate(context, profile.id, template); refresh() }
                .onFailure { error = "الگو ذخیره نشد." }
            training = null
        })
    }
    remove?.let { profile ->
        JibikModalDialog(onDismissRequest = { remove = null }, modifier = Modifier.jibikDialogSurface(),
            properties = JibikWideDialogProperties,
            title = { JibikDialogHeading("حذف سرشماره", "تراکنش‌های قبلی محفوظ می‌مانند") },
            text = { Text("پایش این ارتباط متوقف می‌شود.") },
            confirmButton = { LiquidGlassButton("حذف ارتباط", onClick = {
                runCatching { BankSenderRegistry.remove(context, profile.id); refresh() }
                    .onFailure { error = "حذف انجام نشد." }
                remove = null
            }) },
            dismissButton = { TextButton(onClick = { remove = null }) { Text("انصراف") } })
    }
}

@Composable
private fun BankSenderEditor(accounts: List<Account>, discovered: List<String>, onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit) {
    var sender by remember { mutableStateOf("") }
    var bank by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var last4 by remember { mutableStateOf("") }
    var noIdentifier by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var bankExpanded by remember { mutableStateOf(false) }
    val eligible = accounts.filterNot { it.archived }
    val valid = bankSenderKey(sender) != null && bank.isNotBlank() && eligible.any { it.id == account } &&
        (noIdentifier || last4.length == 4)
    JibikModalDialog(onDismissRequest = onDismiss, modifier = Modifier.jibikDialogSurface(),
        properties = JibikWideDialogProperties,
        title = { JibikDialogHeading("معرفی سرشماره بانکی", "اتصال دقیق پیام به حساب") },
        text = {
            Column(Modifier.fillMaxWidth().jibikScrollableBody(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (discovered.isNotEmpty()) {
                    OutlinedTextField(query, { query = it.take(48) }, label = { Text("جست‌وجوی فرستنده") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    discovered.filter { it.contains(BankSmsParser.normalise(query), ignoreCase = true) }.take(15).forEach {
                        JibikGlassChip(it, selected = sender == it, onClick = { sender = it }, modifier = Modifier.fillMaxWidth())
                    }
                    JibikFormHint("فهرست فقط شامل نشانی فرستنده است. برای موارد بیشتر جست‌وجو کن.")
                }
                OutlinedTextField(sender, { sender = it.take(48) }, label = { Text("سرشماره یا نام دقیق فرستنده") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                TextButton(onClick = { bankExpanded = !bankExpanded }) {
                    Text(BankSmsParser.banks.firstOrNull { it.id == bank }?.title ?: "انتخاب بانک")
                }
                if (bankExpanded) BankSmsParser.banks.forEach { item ->
                    JibikGlassChip(item.title, bank == item.id, onClick = { bank = item.id; bankExpanded = false },
                        modifier = Modifier.fillMaxWidth())
                }
                JibikAccountChoice("حساب مرتبط در جیبیک", eligible, account, { account = it })
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(noIdentifier, onCheckedChange = { noIdentifier = it; last4 = "" })
                    Text("پیام‌های این حساب شناسه حساب ندارند", modifier = Modifier.weight(1f))
                }
                if (!noIdentifier) OutlinedTextField(last4, { raw ->
                    last4 = BankSmsParser.normalise(raw).filter { it in '0'..'9' }.take(4)
                }, label = { Text("چهار رقم انتهایی حساب در پیامک") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                JibikFormHint("فقط همین فرستنده و همین شناسه بررسی می‌شود. بدون شناسه، فقط پیام‌هایی که شماره حساب ندارند تطبیق می‌یابند.")
            }
        },
        confirmButton = { LiquidGlassButton("ذخیره ارتباط", enabled = valid, onClick = {
            onSave(bank, sender, if (noIdentifier) "" else last4, account)
        }) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}

@Composable
private fun BankAutoTrainingDialog(profile: BankSenderProfile, onDismiss: () -> Unit,
    onApprove: (String) -> Unit) {
    val context = LocalContext.current
    var sample by remember { mutableStateOf("") }
    var match by remember { mutableStateOf<BankSmsMatch?>(null) }
    var template by remember { mutableStateOf<String?>(null) }
    var confirmed by remember { mutableStateOf(false) }
    JibikModalDialog(onDismissRequest = onDismiss, modifier = Modifier.jibikDialogSurface(),
        properties = JibikWideDialogProperties,
        title = { JibikDialogHeading("آموزش ثبت خودکار", "نمونه بانک معرفی‌شده · فقط روی گوشی") },
        text = {
            Column(Modifier.fillMaxWidth().jibikScrollableBody(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                JibikFormHint("یک نمونه خرید، برداشت یا واریز موفق از همین حساب را وارد کن. انتقال و پیام مبهم خودکار ثبت نمی‌شوند.")
                OutlinedTextField(sample, { sample = it.take(1500); match = null; template = null; confirmed = false },
                    label = { Text("متن نمونه پیامک") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                TextButton(enabled = sample.isNotBlank(), onClick = {
                    val parsed = BankSmsParser.parse(profile.sender, sample, System.currentTimeMillis(),
                        BankSmsSettings.fingerprintSalt(context), profile.bankId)
                    match = parsed?.takeIf { it.accountLast4 == profile.accountLast4 }
                    template = match?.let { strictBankAutoTemplate(sample, it) }
                    confirmed = false
                }) { Text("تحلیل نمونه") }
                match?.let {
                    JibikFinancialDetail("نوع", if (it.income) "واریز" else "برداشت")
                    JibikFinancialDetail("مبلغ", it.amountToman?.let(::formatToman) ?: "نامعتبر")
                    JibikFinancialDetail("حساب", if (it.accountLast4.isBlank()) "بدون شماره در پیام؛ حساب معرفی‌شده" else "••••" + it.accountLast4)
                    if (it.accountLast4.isBlank()) JibikFormHint("فقط اگر تمام پیام‌های این سرشماره به همین یک حساب تعلق دارند، ثبت خودکار را تأیید کن.", isWarning = true)
                    JibikFormHint(if (template != null) "این قالب قابل تأیید است؛ واحد " + (if (template?.endsWith(":تومان") == true) "تومان" else "ریال") + " است." else "این قالب فعلاً فقط برای بررسی دستی مناسب است.")
                }
                if (template != null) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(confirmed, onCheckedChange = { confirmed = it })
                    Text("مبلغ، واحد، نوع و حساب درست است و اجازه ثبت پیام‌های جدید مشابه را می‌دهم.", modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = { LiquidGlassButton("تأیید ثبت خودکار", enabled = template != null && confirmed,
            onClick = { template?.let(onApprove) }) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}
