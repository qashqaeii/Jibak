package ir.jibik.app

import java.math.BigInteger

/** A pasted, recognized message can provide a read-only comparison. No raw
 * message or bank balance is retained and no adjustment is created.
 */
internal fun bankReportedBalanceToman(body: String, match: BankSmsMatch): Long? {
    if (match.amountToman == null) return null
    val lines = BankSmsParser.normalise(body).lines().map { it.trim() }
    val rows = lines.filter { Regex("^(مانده|موجودی)\\s*[:：]").containsMatchIn(it) }
    if (rows.size != 1) return null
    val value = Regex("^(?:مانده|موجودی)\\s*[:：]\\s*([0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)\\s*(ریال|تومان)?$")
        .matchEntire(rows.single()) ?: return null
    val raw = value.groupValues[1].replace(",", "").toLongOrNull() ?: return null
    val unit = value.groupValues[2]
    return when {
        unit == "تومان" -> raw
        unit == "ریال" || (unit.isBlank() && match.bankId == "melli" &&
            Regex("^(انتقال|خرید ?اینترنتی|خرید|برداشت|واریز)\\s*[:：]\\s*[0-9,]+\\s*[+-]$")
                .matches(lines.getOrNull(1).orEmpty())) -> raw.takeIf { it % 10L == 0L }?.div(10L)
        else -> null
    }
}
internal fun bankBalanceDifference(local: Long, reported: Long): BigInteger =
    local.toBigInteger() - reported.toBigInteger()
