package ir.jibik.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibik.core.finance.CategoryExpense
import ir.jibik.core.finance.LedgerRecord
import ir.jibik.core.finance.PeriodTotals
import ir.jibik.core.finance.periodTotals
import java.text.NumberFormat
import java.util.Locale

private val Emerald = Color(0xFF07574C)
private val Ivory = Color(0xFFFFFAF5)
private val Ink = Color(0xFF102D31)
private val Muted = Color(0xFF677A78)
private val Coral = Color(0xFFE95C50)
private val Mint = Color(0xFF21C79B)
private val Amber = Color(0xFFFF8A1F)

private fun moneyReport(value: Long): String =
    NumberFormat.getNumberInstance(Locale("fa", "IR")).format(value) + " تومان"

/** Always uses real local ledger rows. Offline, private, and free of fabricated trends. */
@Composable
internal fun ReportsScreen(
    records: List<LedgerRecord>,
    categorySpending: List<CategoryExpense>,
    modifier: Modifier = Modifier,
    onExportCsv: () -> Unit = {},
) {
    val month = currentJalaliMonth()
    val totals = periodTotals(records, month.startMillis, month.endExclusiveMillis)
    val categories = categorySpending
    val cashFlow = totals.incomeToman - totals.expenseToman
    Column(
        modifier = modifier.fillMaxSize().liquidBackdrop().verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("گزارش مالی", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                Text("آنچه واقعاً در ${month.title} اتفاق افتاده", fontSize = 13.sp, color = Muted)
            }
            JibikBrandLogo(Modifier.size(44.dp))
        }
        JibikHero(Modifier.fillMaxWidth()) {
            Text("خالص جریان پول این ماه", fontSize = 13.sp, color = Color(0xFFDAF1E9))
            Text(
                moneyReport(cashFlow),
                fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.18f))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("درآمد ماه", color = Color(0xFFCCEEE5), fontSize = 12.sp)
                    Text(
                        moneyReport(totals.incomeToman), color = Color.White, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("هزینه ماه", color = Color(0xFFCCEEE5), fontSize = 12.sp)
                    Text(
                        moneyReport(totals.expenseToman), color = Color.White, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (categories.isNotEmpty()) {
            JibikSurfaceCard {
                Text("بیشترین هزینه در این ماه", color = Muted, fontSize = 12.sp)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        categories.first().category, color = Ink, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        moneyReport(categories.first().amountToman), color = Emerald,
                        fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        LiquidGlassCard(
            modifier = Modifier.fillMaxWidth(),
            tone = GlassTone.CRYSTAL,
        ) {
            Column(Modifier.padding(0.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                Text("سهم دسته‌ها از هزینه", fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold, color = Ink)
                if (categories.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        JiboMascot(Modifier.size(66.dp))
                        Text("برای این ماه هنوز هزینه‌ای ثبت نکردی.",
                            color = Muted, fontSize = 13.sp)
                    }
                } else {
                    CategoryRing(categories, totals.expenseToman)
                    categories.take(10).forEachIndexed { index, entry ->
                        val colour = listOf(Emerald, Amber, Mint, Coral)[index % 4]
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    entry.category, fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium, color = Ink,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    moneyReport(entry.amountToman),
                                    fontSize = 12.sp, color = Muted,
                                    modifier = Modifier.weight(0.9f),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            }
                            LinearProgressIndicator(
                                progress = {
                                    (entry.amountToman.toDouble() /
                                        totals.expenseToman.coerceAtLeast(1L))
                                        .toFloat().coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth().height(7.dp),
                                color = colour, trackColor = Ivory,
                            )
                        }
                    }
                    if (categories.size > 10) {
                        Text("دسته‌های دیگر: ${categories.size - 10}",
                            color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        JibikSurfaceCard {
            Text(
                "دریافت گردش مالی",
                color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp,
            )
            Text(
                "فایل CSV شامل همه تراکنش‌های ثبت‌شده، تاریخ، مبلغ، حساب‌ها و توضیحات است. " +
                    "خروجی فقط با انتخاب خودت در محل دلخواه ذخیره می‌شه.",
                color = Muted, fontSize = 12.sp, lineHeight = 20.sp,
            )
            LiquidGlassButton(
                text = "ذخیره فایل CSV",
                onClick = onExportCsv,
                modifier = Modifier.fillMaxWidth().height(49.dp),
                tone = GlassTone.EMERALD,
            )
            JibikFormHint(
                "این فایل اطلاعات مالی شخصی داره؛ فقط در جای امن ذخیره یا ارسالش کن. " +
                    "CSV نسخه پشتیبان قابل بازیابی نیست.",
            )
        }
        Text(
            "انتقال بین حساب‌ها در مجموع درآمد و هزینه حساب نمی‌شود. " +
                "این آمار براساس تراکنش‌های ذخیره‌شده روی دستگاه است.",
            color = Muted, fontSize = 12.sp, lineHeight = 20.sp,
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun CategoryRing(categories: List<CategoryExpense>, total: Long) {
    val palette = listOf(Emerald, Amber, Mint, Coral)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(124.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 17.dp.toPx()
                var angle = -90f
                categories.forEachIndexed { index, category ->
                    val fraction = category.amountToman.toDouble() / total.coerceAtLeast(1L)
                    val sweep = (fraction * 360.0).toFloat()
                    drawArc(
                        color = palette[index % palette.size],
                        startAngle = angle,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(stroke),
                    )
                    angle += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${categories.size}", fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold, color = Ink)
                Text("دسته", fontSize = 12.sp, color = Muted)
            }
        }
    }
}
