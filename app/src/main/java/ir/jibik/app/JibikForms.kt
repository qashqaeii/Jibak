package ir.jibik.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jibik.core.finance.Account
import java.text.NumberFormat
import java.util.Locale

/**
 * Keep the editor's underlying state numeric only.
 * Formatting is visual, so a user never accidentally stores a grouped string.
 * Accept ASCII, Arabic-Indic and Persian numerals without silently changing
 * an invalid (negative/fractional) amount into a different value.
 */
internal fun isAllowedTomanDraft(raw: String): Boolean = raw.all { char ->
    char in '0'..'9' || char in '۰'..'۹' || char in '٠'..'٩' ||
        char == '٬' || char == ',' || char == ' '
}

internal fun normalizeTomanDraft(raw: String): String {
    val builder = StringBuilder()
    for (char in raw) {
        val digit = when (char) {
            in '0'..'9' -> char
            in '۰'..'۹' -> ('0'.code + char.code - '۰'.code).toChar()
            in '٠'..'٩' -> ('0'.code + char.code - '٠'.code).toChar()
            else -> null
        }
        if (digit != null && builder.length < 19) builder.append(digit)
    }
    return builder.toString()
}

internal fun parseTomanDraft(raw: String): Long? {
    if (raw.isBlank()) return null
    // Refuse unexpected symbols instead of interpreting a negative as positive.
    if (raw.any { it !in '0'..'9' && it !in '۰'..'۹' && it !in '٠'..'٩'
        && it != '٬' && it != ',' && it != ' ' }) return null
    val digits = normalizeTomanDraft(raw)
    if (digits.isBlank()) return null
    return digits.toLongOrNull()
}

internal fun groupDigits(raw: String): String {
    if (raw.length <= 3) return raw
    return raw.reversed().chunked(3).joinToString(",").reversed()
}

/**
 * Thousands separators are added for display only. Mapping keeps caret
 * movement safe in the middle of a multi-digit Persian financial amount.
 */
internal class TomanGroupingTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val output = StringBuilder()
        val sourceToDisplay = IntArray(digits.length + 1)
        for (index in digits.indices) {
            if (index > 0 && (digits.length - index) % 3 == 0) {
                output.append(',')
            }
            sourceToDisplay[index] = output.length
            output.append(digits[index])
        }
        sourceToDisplay[digits.length] = output.length
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                sourceToDisplay[offset.coerceIn(0, digits.length)]

            override fun transformedToOriginal(offset: Int): Int {
                val bounded = offset.coerceIn(0, output.length)
                return sourceToDisplay.indexOfLast { it <= bounded }.coerceAtLeast(0)
            }
        }
        return TransformedText(AnnotatedString(output.toString()), mapping)
    }
}

@Composable
internal fun JibikTomanField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    allowZero: Boolean = false,
) {
    val number = parseTomanDraft(value)
    val formattedPreview = remember(number) {
        number?.let(::formatToman)
    }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                if (isAllowedTomanDraft(it)) onValueChange(normalizeTomanDraft(it))
            },
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                textDirection = TextDirection.Ltr,
            ),
            visualTransformation = remember { TomanGroupingTransformation() },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        if (formattedPreview != null && (number!! > 0L || allowZero)) {
            Text(
                formattedPreview, fontSize = 12.sp,
                color = JibikStyle.muted, maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
internal fun JibikAccountChoice(
    title: String,
    accounts: List<Account>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    emptyHint: String = "حساب قابل انتخابی وجود ندارد",
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, color = JibikStyle.muted, fontSize = 12.sp)
        if (accounts.isEmpty()) {
            Text(emptyHint, color = JibikStyle.coral, fontSize = 12.sp)
        } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                accounts.forEach { account ->
                    JibikGlassChip(
                        text = account.title,
                        selected = selectedId == account.id,
                        onClick = { onSelect(account.id) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun JibikFormHint(
    text: String,
    isWarning: Boolean = false,
) {
    Text(
        text,
        fontSize = 12.sp,
        lineHeight = 20.sp,
        color = if (isWarning) JibikStyle.coral else JibikStyle.muted,
    )
}
