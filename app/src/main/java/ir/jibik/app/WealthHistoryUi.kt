package ir.jibik.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.math.BigInteger

@Composable
internal fun AssetAllocationBar(worth: NetWorth, assets: List<AssetPosition>) {
    if (worth.assets.signum() <= 0) return
    val groups = listOf("حساب‌ها" to worth.cash, "مطالبات" to worth.receivables) +
        assets.filterNot { it.archived }.groupBy { it.holding.kind }.map { (kind, items) ->
            ASSET_KINDS.getValue(kind) to items.fold(BigInteger.ZERO) { sum, item -> sum + item.valueToman.toBigInteger() }
        }
    val palette = listOf(Color(0xFF07574C), Color(0xFF21A482), Color(0xFFE0AA54), Color(0xFF659BB6),
        Color(0xFF8A79B0), Color(0xFFBA7657), Color(0xFF83A852), Color(0xFF557D79))
    val rows = groups.filter { it.second.signum() > 0 }
    Canvas(Modifier.fillMaxWidth().height(13.dp).semantics { contentDescription = "ترکیب دارایی‌های مثبت" }) {
        var right = size.width
        rows.forEachIndexed { index, row ->
            val width = (size.width * (row.second.toDouble() / worth.assets.toDouble())).toFloat().coerceIn(0f, right)
            drawRect(palette[index % palette.size], Offset(right-width,0f), Size(width,size.height))
            right -= width
        }
    }
    rows.forEachIndexed { index, row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Canvas(Modifier.size(10.dp).padding(top = 2.dp)) { drawRect(palette[index % palette.size]) }
            Text(row.first, modifier = Modifier.weight(1f))
            Text(reportRateText(java.math.BigDecimal(row.second * 100.toBigInteger()).divide(
                java.math.BigDecimal(worth.assets), 1, java.math.RoundingMode.HALF_UP)))
        }
    }
}

@Composable
internal fun WealthHistoryCard(store: LocalLedgerStore) {
    var history by remember { mutableStateOf(emptyList<WealthSnapshot>()) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var count by remember { mutableIntStateOf(12) }
    val scope = rememberCoroutineScope()
    suspend fun read() {
        try { history = store.loadWealthHistory(); loaded = true; error = "" }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "تاریخچه بارگذاری نشد."; loaded = false }
    }
    LaunchedEffect(store) { read() }
    val points = dailyWealthHistory(history).takeLast(count)
    JibikSurfaceCard {
        JibikSectionTitle("تاریخچه ثروت", "تصویرهای ثبت‌شده؛ بدون بازسازی فرضی گذشته")
        if (!loaded) Text(if (error.isBlank()) "در حال بارگذاری…" else error)
        else if (points.isEmpty()) JibikFormHint("هنوز تصویری ذخیره نشده است. اولین تصویر ثروت را ثبت کن.")
        else {
            val values = points.map { it.worth().net }
            val scale = financeChartScale(values)
            val reveal = rememberChartReveal(points)
            val tint = JibikStyle.forest
            FinanceChartFrame(scale, "تاریخچه ثبت‌شده؛ آخرین ارزش " + forecastMoney(values.last()),
                formatJalaliDueDate(points.first().atMillis).takeLast(5),
                formatJalaliDueDate(points.last().atMillis).takeLast(5)) {
                val span = (points.last().atMillis - points.first().atMillis).coerceAtLeast(1L)
                val zero = size.height * (1f - scale.fraction(BigInteger.ZERO))
                fun position(i: Int): Offset {
                    val fraction = if (values.size == 1) .5f else ((points[i].atMillis - points.first().atMillis).toDouble() / span).toFloat()
                    val y = size.height * (1f - scale.fraction(values[i]))
                    return Offset(size.width - 5.dp.toPx() - (size.width - 10.dp.toPx()) * fraction,
                        zero + (y - zero) * reveal)
                }
                values.indices.forEach { i ->
                    if (i > 0) drawLine(tint, position(i-1), position(i), strokeWidth = 2.dp.toPx())
                    drawCircle(if (values[i].signum() < 0) JibikStyle.coral else tint, 3.dp.toPx(), position(i))
                }
            }
            JibikFinancialDetail("آخرین ارزش ثبت‌شده", forecastMoney(values.last()), emphasis = true)
            if (values.size > 1) JibikFinancialDetail("تغییر در نقاط نمایش‌داده‌شده", forecastMoney(values.last()-values.first()))
            points.asReversed().forEach { row -> JibikFinancialDetail(formatJalaliDueDate(row.atMillis), forecastMoney(row.worth().net)) }
            if (dailyWealthHistory(history).size > count) TextButton(onClick = { count += 12 }) { Text("نمایش سابقه بیشتر") }
        }
        if (loaded && error.isNotBlank()) JibikFormHint(error, isWarning = true)
        LiquidGlassButton(if (busy) "در حال ثبت…" else "ثبت تصویر ثروت امروز", enabled = !busy, onClick = {
            busy = true; error = ""
            scope.launch {
                try { history = store.captureWealth(); loaded = true }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { error = "ثبت تصویر کامل نشد؛ دوباره تلاش کن." }
                finally { busy = false }
            }
        })
        JibikFormHint("هر نقطه نتیجه ثبت صریح توست. اصلاح تراکنش‌های گذشته تصویرهای ذخیره‌شده را بازنویسی نمی‌کند.")
    }
}
