package ir.jibik.app

import java.text.NumberFormat
import java.util.Locale

/**
 * Display currency consistently in RTL financial screens.
 *
 * Persian zero is a hollow glyph that looks like a stray dot in the screenshots.
 * A real zero balance is shown as a readable word; nonzero values use Persian
 * digits and plain commas rather than the two-dot Arabic separator.
 * Amounts are integers; formatting is strictly presentation-only.
 */
internal fun formatToman(amount: Long): String {
    if (amount == 0L) return "صفر تومان"
    val grouped = NumberFormat.getIntegerInstance(Locale.US).format(amount)
    val digits = buildString(grouped.length) {
        grouped.forEach { char ->
            append(
                if (char in '0'..'9') ('۰'.code + char.code - '0'.code).toChar()
                else char
            )
        }
    }
    return "$digits تومان"
}
