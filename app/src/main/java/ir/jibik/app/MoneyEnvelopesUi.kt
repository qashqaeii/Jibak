package ir.jibik.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.jibik.core.finance.Account
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
internal fun MoneyEnvelopesSection(store: LocalLedgerStore, envelopes: List<MoneyEnvelope>,
    goals: List<SavingsGoalEntity>, accounts: List<Account>, balances: Map<String, Long>,
    onChanged: (List<MoneyEnvelope>) -> Unit) {
    val scope = rememberCoroutineScope()
    val eligible = accounts.filter { account -> goals.none { it.accountId == account.id } }
    val funding = remember(envelopes, balances, goals) { envelopeFunding(balances, goals, envelopes) }
    var create by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<MoneyEnvelope?>(null) }
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(emptyList<EnvelopeEvent>()) }
    var historyFailed by remember { mutableStateOf(false) }
    fun save(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = ""
        scope.launch {
            try {
                action()
                create = false; selected = null
                onChanged(store.loadEnvelopes())
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "ذخیره کامل نشد؛ موجودی و تخصیص قبلی را بررسی کن." }
            finally { busy = false }
        }
    }
    JibikSectionTitle("پاکت‌های مالی", "کنار گذاشتن پول در همان حساب")
    JibikSurfaceCard {
        Text("پشتوانه واقعی پاکت‌ها", color = JibikStyle.muted)
        Text(java.text.NumberFormat.getIntegerInstance(java.util.Locale("fa", "IR")).format(
            funding.fold(java.math.BigInteger.ZERO) { sum, row -> sum + row.fundedToman.toBigInteger() }) + " تومان",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        JibikFormHint("تخصیص و آزادسازی، پول را جابه‌جا نمی‌کند. اگر موجودی کم شود، کسری پاکت‌های جدیدتر نمایش داده می‌شود.")
        LiquidGlassButton("＋ ساخت پاکت", enabled = eligible.isNotEmpty() && !busy, onClick = {
            title = ""; amount = ""; account = eligible.firstOrNull()?.id.orEmpty(); error = ""; create = true
        })
    }
    if (funding.isEmpty()) Text("برای سفر، هزینه اضطراری یا هر هدف دیگری پاکت بساز.", color = JibikStyle.muted)
    funding.forEach { row ->
        val item = row.envelope
        JibikSurfaceCard {
            JibikSectionTitle(item.title, accounts.firstOrNull { it.id == item.accountId }?.title.orEmpty())
            Text(formatToman(row.fundedToman) + " از " + formatToman(item.targetToman), fontWeight = FontWeight.Bold)
            JibikProgressTrack(progress = (row.fundedToman.toDouble() / item.targetToman).toFloat().coerceIn(0f, 1f),
                color = JibikStyle.forest, trackColor = JibikStyle.cream)
            Text("تخصیص برنامه‌ریزی‌شده: " + formatToman(item.reservedToman), color = JibikStyle.muted)
            if (row.shortfallToman > 0) JibikFormHint("کسری پشتوانه: " + formatToman(row.shortfallToman), isWarning = true)
            LiquidGlassButton("مدیریت تخصیص", enabled = !busy, onClick = {
                selected = item; amount = item.reservedToman.toString(); history = emptyList(); historyFailed = false; error = ""
            })
        }
    }
    if (error.isNotEmpty() && !create && selected == null) JibikFormHint(error, isWarning = true)
    if (create) {
        val parsed = parseTomanDraft(amount)
        JibikModalDialog(properties = JibikWideDialogProperties, modifier = Modifier.jibikDialogSurface(),
            onDismissRequest = { if (!busy) create = false },
            title = { JibikDialogHeading("پاکت جدید", "بدون حساب بانکی جدید") },
            text = {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(title, { title = it.take(60) }, label = { Text("نام پاکت") }, singleLine = true, enabled = !busy)
                    JibikTomanField(amount, { amount = it }, "مبلغ هدف", enabled = !busy)
                    JibikAccountChoice("حساب پشتوانه", eligible, account, { account = it }, enabled = !busy)
                    JibikFormHint("پس از ساخت، مبلغ دلخواه را از موجودی آزاد همین حساب تخصیص بده.")
                    if (error.isNotEmpty()) JibikFormHint(error, isWarning = true)
                }
            },
            confirmButton = { LiquidGlassButton("ساخت پاکت", enabled = !busy && title.isNotBlank() && parsed != null && parsed > 0 &&
                eligible.any { it.id == account }, onClick = {
                save { store.addEnvelope(MoneyEnvelope(UUID.randomUUID().toString(), title.trim(), account,
                    requireNotNull(parsed), createdAtMillis = System.currentTimeMillis())) }
            }) },
            dismissButton = { TextButton(enabled = !busy, onClick = { create = false }) { Text("انصراف") } })
    }
    selected?.let { item ->
        LaunchedEffect(item.id) {
            try { history = store.envelopeHistory(item.id) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { historyFailed = true }
        }
        val free = envelopeFreeCapacity(item.accountId, balances, envelopes, item.id)
        val max = minOf(item.targetToman, maxOf(free, item.reservedToman))
        val parsed = if (amount.trim() == "0" || amount.trim() == "۰") 0L else parseTomanDraft(amount)
        JibikModalDialog(properties = JibikWideDialogProperties, modifier = Modifier.jibikDialogSurface(),
            onDismissRequest = { if (!busy) selected = null },
            title = { JibikDialogHeading(item.title, "تخصیص یا آزادسازی موجودی") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("تخصیص فعلی: " + formatToman(item.reservedToman))
                    Text("حداکثر پشتوانه پس از سایر پاکت‌ها: " + formatToman(minOf(free, item.targetToman)))
                    JibikTomanField(amount, { amount = it }, "مبلغ کل تخصیص جدید", enabled = !busy)
                    TextButton(enabled = !busy, onClick = { amount = "0" }) { Text("آزادسازی کامل") }
                    if (parsed != null && parsed > max) JibikFormHint("موجودی آزاد کافی نیست.", isWarning = true)
                    JibikFormHint("آزادسازی یا تخصیص مجدد، مانده واقعی حساب را تغییر نمی‌دهد.")
                    if (error.isNotEmpty()) JibikFormHint(error, isWarning = true)
                    JibikSectionTitle("تاریخچه تخصیص", "آخرین تغییرات")
                    if (historyFailed) Text("تاریخچه بارگذاری نشد.")
                    history.take(15).forEach { event ->
                        Text(formatJalaliDueDate(event.atMillis) + " · " + formatToman(event.previousToman) + " ← " + formatToman(event.reservedToman))
                    }
                }
            },
            confirmButton = { LiquidGlassButton("ذخیره تخصیص", enabled = !busy && parsed != null && parsed in 0..max && parsed != item.reservedToman,
                onClick = { save { store.setEnvelopeReserve(item.id, item.reservedToman, requireNotNull(parsed)) } }) },
            dismissButton = { TextButton(enabled = !busy, onClick = { selected = null }) { Text("انصراف") } })
    }
}
