package ir.jibik.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun AppProtectionCard(protection: AppProtection) {
    JibikSurfaceCard {
        JibikSectionTitle("امنیت و حریم خصوصی", "کنترل دسترسی به دفتر مالی")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("قفل برنامه", fontWeight = FontWeight.Bold)
                Text("تأیید هویت هنگام ورود یا پس از ۳۰ ثانیه خروج", color = JibikStyle.muted)
            }
            Switch(checked = protection.enabled, onCheckedChange = protection::requestEnabled,
                enabled = !protection.busy)
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("محافظت از تصویر صفحه", fontWeight = FontWeight.Bold)
                Text(if (protection.enabled) "همراه قفل برنامه فعال است." else "جلوگیری از تصویر صفحه و پیش‌نمایش اخیر", color = JibikStyle.muted)
            }
            Switch(checked = protection.enabled || protection.blockCapture, onCheckedChange = protection::setCapture,
                enabled = !protection.busy && !protection.enabled)
        }
        if (protection.enabled) LiquidGlassButton("قفل کردن جیبیک", onClick = protection::lockNow, enabled = !protection.busy)
        if (protection.message.isNotBlank()) JibikFormHint(protection.message)
        JibikFormHint("قفل برنامه جایگزین رمزنگاری دیتابیس نیست. برای بازیابی روی دستگاه دیگر پشتیبان رمزدار بساز.")
    }
}

@Composable
internal fun AppLockedScreen(protection: AppProtection) {
    Box(Modifier.fillMaxSize().liquidBackdrop().safeDrawingPadding(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            JiboArt(JiboArtwork.ACCOUNTS, Modifier.size(140.dp))
            Text("دفتر مالی شما قفل است", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, color = JibikStyle.ink)
            Text("برای دسترسی دوباره، هویت خود را با روش امن گوشی تأیید کن.",
                color = JibikStyle.muted, textAlign = TextAlign.Center)
            LiquidGlassButton(if (protection.busy) "در حال تأیید…" else "باز کردن جیبیک",
                onClick = { protection.unlock() }, enabled = !protection.busy, modifier = Modifier.fillMaxWidth())
            TextButton(enabled = !protection.busy, onClick = { protection.unlock(credentialOnly = true) }) {
                Text("استفاده از رمز یا الگوی گوشی")
            }
            if (protection.message.isNotBlank()) JibikFormHint(protection.message, isWarning = true)
        }
    }
}
