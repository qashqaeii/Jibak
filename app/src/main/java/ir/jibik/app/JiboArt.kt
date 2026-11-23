package ir.jibik.app

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

/**
 * Cohesive, transparent 384px WebP illustrations.
 *
 * They are decorative accents, not control glyphs or replacements for critical
 * financial numbers. All buttons retain textual labels and screen-reader names.
 * Resources live under drawable-nodpi to prevent resampling multiple density copies.
 */
internal enum class JiboArtwork(@DrawableRes val resource: Int, val label: String) {
    HOME(R.drawable.jibo_art_home, "جیبو با کیف پول"),
    TRANSACTIONS(R.drawable.jibo_art_transactions, "جیبو با رسید"),
    BUDGET(R.drawable.jibo_art_budget, "قلک پس‌انداز"),
    REPORTS(R.drawable.jibo_art_reports, "نمودار رشد مالی"),
    ACCOUNTS(R.drawable.jibo_art_accounts, "کیف پول و حساب‌ها"),
    GOALS(R.drawable.jibo_art_goals, "هدف پس‌انداز جیبو"),
    OBLIGATIONS(R.drawable.jibo_art_obligations, "تقویم سررسید"),
    PURCHASE(R.drawable.jibo_art_purchase, "سبد خرید"),
    CATEGORIES(R.drawable.jibo_art_categories, "پوشه‌های دسته‌بندی"),
    ASSISTANT(R.drawable.jibo_art_jibo_ai, "دستیار هوشمند جیبو"),
    EMPTY(R.drawable.jibo_art_empty, "جیبو با ذره‌بین"),
    REMINDERS(R.drawable.jibo_art_reminders, "زنگ یادآوری"),
}

@Composable
internal fun JiboArt(
    illustration: JiboArtwork,
    modifier: Modifier = Modifier,
    decorative: Boolean = true,
) {
    Image(
        painter = painterResource(id = illustration.resource),
        contentDescription = if (decorative) null else illustration.label,
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}
