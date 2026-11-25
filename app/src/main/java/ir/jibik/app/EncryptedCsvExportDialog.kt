package ir.jibik.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** User-initiated secure CSV export. Passphrase is never rememberSaveable. */
@Composable
internal fun EncryptedCsvExportDialog(
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val valid = passphrase.length in EncryptedLedgerCsvExport.MIN_PASSWORD_CHARS..256 &&
        passphrase == confirmation
    AlertDialog(
        properties = JibikWideDialogProperties,
        modifier = Modifier.jibikDialogSurface(),
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        title = {
            JibikDialogHeading("خروجی مالی رمزدار", "اطلاعات خروجی را با گذرواژه محافظت کن")
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                JibikFormHint(
                    "یک رمز حداقل ۱۲ کاراکتری بساز. فایل بدون این رمز باز نمی‌شود " +
                        "و جیبیک آن را ذخیره یا بازیابی نمی‌کند."
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it.take(256) },
                    label = { Text("گذرواژه فایل") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp),
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it.take(256) },
                    label = { Text("تکرار گذرواژه") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp),
                )
                if (confirmation.isNotEmpty() && confirmation != passphrase) {
                    JibikFormHint("دو گذرواژه یکسان نیستند.", isWarning = true)
                }
                JibikFormHint(
                    "فایل .jbc فقط CSV تراکنش‌ها را رمز می‌کند؛ " +
                        "بکاپ کامل یا قابل‌بازیابیِ همه اطلاعات برنامه نیست."
                )
            }
        },
        confirmButton = {
            LiquidGlassButton(
                text = "انتخاب محل ذخیره",
                enabled = valid,
                onClick = {
                    val copy = passphrase.toCharArray()
                    passphrase = ""
                    confirmation = ""
                    onConfirm(copy)
                },
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("انصراف") }
        },
    )
}
