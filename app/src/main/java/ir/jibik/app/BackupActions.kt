package ir.jibik.app

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream

@Composable
internal fun BackupActions(store: LocalLedgerStore, onRestored: () -> Unit) {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf<String?>(null) }
    var secret by remember { mutableStateOf<CharArray?>(null) }
    var source by remember { mutableStateOf<Uri?>(null) }
    var preview by remember { mutableStateOf<JSONObject?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    DisposableEffect(Unit) { onDispose { secret?.fill(Char.MIN_VALUE) } }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val password = secret
        secret = null
        if (uri == null || password == null) password?.fill(Char.MIN_VALUE)
        else {
            busy = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val bytes = FinancialBackup.encrypt(store.backup(), password)
                        requireNotNull(resolver.openOutputStream(uri, "wt")).use { it.write(bytes) }
                    }
                    message = "نسخه پشتیبان ذخیره شد."
                } catch (_: Exception) { message = "ذخیره کامل نشد. فایل ناقص را حذف کن." }
                finally { password.fill(Char.MIN_VALUE); busy = false }
            }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { source = uri; mode = "restore" }
    }
    JibikSurfaceCard {
        Text("نسخه پشتیبان دفتر مالی")
        TextButton(enabled = !busy, onClick = { mode = "save" }) { Text("ساخت پشتیبان رمزدار") }
        TextButton(enabled = !busy, onClick = { open.launch(arrayOf("*/*")) }) { Text("بازیابی از فایل") }
        if (busy || message.isNotBlank()) JibikFormHint(if (busy) "در حال پردازش…" else message)
    }
    mode?.let { action ->
        EncryptedCsvExportDialog(
            title = if (action == "save") "پشتیبان رمزدار" else "باز کردن پشتیبان",
            backup = true, restoring = action == "restore", onDismiss = { mode = null },
            onConfirm = { password ->
                mode = null
                if (action == "save") {
                    secret = password
                    save.launch("jibik-backup.jbk")
                } else {
                    busy = true
                    scope.launch {
                        try {
                            preview = withContext(Dispatchers.IO) {
                                val bytes = ByteArrayOutputStream()
                                requireNotNull(resolver.openInputStream(requireNotNull(source))).use { input ->
                                    val buffer = ByteArray(8192)
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        require(bytes.size() + count <= FinancialBackup.maxBytes)
                                        bytes.write(buffer, 0, count)
                                    }
                                }
                                FinancialBackup.decrypt(bytes.toByteArray(), password)
                            }
                        } catch (_: Exception) { message = "فایل یا گذرواژه معتبر نیست." }
                        finally { password.fill(Char.MIN_VALUE); busy = false }
                    }
                }
            },
        )
    }
    preview?.let { data ->
        val accounts = data.getJSONObject("financial_accounts").getJSONArray("rows").length()
        val entries = data.getJSONObject("ledger_records").getJSONArray("rows").length()
        JibikModalDialog(
            properties = JibikWideDialogProperties,
            modifier = Modifier.jibikDialogSurface(),
            onDismissRequest = { if (!busy) preview = null },
            title = { JibikDialogHeading("بازیابی دفتر مالی", "فقط روی دفتر خالی") },
            text = { Text("$accounts حساب و $entries تراکنش به همراه جزئیات بازیابی می‌شوند.") },
            confirmButton = {
                LiquidGlassButton(text = "تأیید بازیابی", enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { store.restoreBackup(data) }
                            message = "دفتر مالی بازیابی شد."
                            onRestored()
                        } catch (_: Exception) { message = "بازیابی انجام نشد. دفتر مقصد باید خالی و فایل معتبر باشد." }
                        finally { busy = false; preview = null }
                    }
                })
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { preview = null }) { Text("انصراف") } },
        )
    }
}
