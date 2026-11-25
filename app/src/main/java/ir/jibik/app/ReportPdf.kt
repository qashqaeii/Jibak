package ir.jibik.app

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

internal data class ReportPdfData(val range: ReportRange, val totals: ReportTotals,
    val previous: ReportTotals, val categories: List<Pair<String, Long>>, val categoriesSupported: Boolean)

internal fun writeReportPdf(output: OutputStream, data: ReportPdfData) {
    val doc = PdfDocument()
    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(16,45,49); textSize = 14f; typeface = Typeface.DEFAULT }
    val green = Color.rgb(7,87,76)
    var pageNo = 0
    var page: PdfDocument.Page? = null
    var y = 0f
    fun newPage() {
        page?.let { doc.finishPage(it) }
        pageNo++
        page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
        val canvas = requireNotNull(page).canvas
        canvas.drawRect(0f,0f,595f,8f,Paint().apply { color = green })
        y = 38f
    }
    fun line(value: String, heading: Boolean = false) {
        paint.textSize = if (heading) 20f else 14f
        paint.typeface = if (heading) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        paint.color = if (heading) green else Color.rgb(16,45,49)
        val layout = StaticLayout.Builder.obtain(value,0,value.length,paint,515)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setTextDirection(android.text.TextDirectionHeuristics.RTL)
            .setIncludePad(true).setLineSpacing(3f,1f).build()
        if (y + layout.height > 800) newPage()
        val canvas = requireNotNull(page).canvas
        canvas.save(); canvas.translate(40f,y); layout.draw(canvas); canvas.restore()
        y += layout.height + 10
    }
    try {
        newPage()
        line("جیبیک | گزارش مالی", true)
        line(data.range.label)
        line("ورودی نقدی: " + forecastMoney(data.totals.income))
        line("خروجی نقدی: " + forecastMoney(data.totals.expense))
        line("خالص جریان: " + forecastMoney(data.totals.net))
        line("نرخ پس‌انداز نقدی: " + reportRateText(data.totals.savingsRate))
        line("مقایسه با بازه قبلی هم‌طول", true)
        line("تغییر ورودی: " + forecastMoney(data.totals.income - data.previous.income))
        line("تغییر خروجی: " + forecastMoney(data.totals.expense - data.previous.expense))
        line("تفکیک هزینه‌ها", true)
        if (!data.categoriesSupported) line("برای مشاهده جزئیات دسته‌ها بازه کوتاه‌تری انتخاب کن.")
        else if (data.categories.isEmpty()) line("هزینه‌ای در این بازه ثبت نشده است.")
        data.categories.forEach { (title,amount) -> line(title + " | " + formatToman(amount)) }
        line("گزارش گردش نقدی ثبت‌شده؛ انتقال حساب‌ها و ارزش‌گذاری دارایی در جریان نقدی منظور نمی‌شود. این فایل رمزدار نیست.")
        page?.let { doc.finishPage(it) }; page = null
        doc.writeTo(output)
    } finally {
        page?.let { doc.finishPage(it) }
        doc.close()
    }
}

@Composable
internal fun ReportPdfAction(range: ReportRange, totals: ReportTotals, previous: ReportTotals, categories: List<CategoryReportRow>, categoriesSupported: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<ReportPdfData?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val snapshot = pending
        pending = null
        if (uri != null && snapshot != null) {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { writeReportPdf(it, snapshot) }
                    }
                    message = "گزارش PDF ذخیره شد."
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { message = "ذخیره کامل نشد؛ فایل ناقص را بررسی کن." }
                finally { busy = false }
            }
        }
    }
    JibikSurfaceCard {
        JibikSectionTitle("گزارش قابل چاپ", "خلاصه همین بازه")
        JibikFormHint("PDF شامل مبالغ و دسته‌های مالی است و رمزنگاری نشده؛ فقط در محل امن ذخیره کن.")
        LiquidGlassButton("دریافت گزارش PDF", enabled = !busy && pending == null, onClick = {
            pending = ReportPdfData(range, totals, previous, categories.map { it.title to it.amountToman }, categoriesSupported)
            try { launcher.launch("jibik-report.pdf") }
            catch (_: Exception) { pending = null; message = "انتخاب محل ذخیره انجام نشد." }
        })
        if (busy || message.isNotBlank()) Text(if (busy) "در حال ساخت گزارش…" else message)
    }
}
