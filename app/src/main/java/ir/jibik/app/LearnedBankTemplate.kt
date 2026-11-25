package ir.jibik.app

import java.security.MessageDigest

/**
 * Sample-trained structural grammar. Only known financial fields are variable.
 * The literal structure (including header, labels and order) is hashed; no
 * sample, personal name, full account number or user-provided regex is stored.
 * An unfamiliar message must fail closed rather than learn by itself.
 */
internal fun learnedBankTemplate(body: String, match: BankSmsMatch): String? {
    if (match.amountToman == null || match.accountLast4.length != 4) return null
    val lines = BankSmsParser.normalise(body).lines().map { it.trim().replace(Regex("[ \t]+"), " ") }.filter { it.isNotEmpty() }
    if (lines.size !in 3..8) return null
    val header = lines.first()
    if (!Regex("^[\\p{L} ]{2,60}$").matches(header)) return null
    if (Regex("واریز|برداشت|خرید|انتقال|رمز|کد|مانده|موجودی|مبلغ|پرداخت|دریافت").containsMatchIn(header)) return null
    val number = "([0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)"
    val direction = "(واریز|برداشت|خرید اینترنتی|خرید|پرداخت)"
    val inline = Regex("^$direction\\s*[:：]\\s*$number\\s*(ریال|تومان)$")
    val event = Regex("^$direction$")
    val amount = Regex("^(مبلغ|وجه)\\s*[:：]\\s*$number\\s*(ریال|تومان)$")
    val account = Regex("^(حساب|کارت)\\s*[:：]\\s*([0-9]{4,24})$")
    val balance = Regex("^(مانده|موجودی)\\s*[:：]\\s*$number\\s*(ریال|تومان)$")
    val date = Regex("^(?:تاریخ\\s*[:：]\\s*)?[0-9]{4}[/.-][0-9]{1,2}[/.-][0-9]{1,2}$")
    val clock = Regex("^(?:ساعت\\s*[:：]\\s*)?(?:[01]?[0-9]|2[0-3]):[0-5][0-9]$")
    val shape = mutableListOf(header)
    var label: String? = null
    var raw: Long? = null
    var currency: String? = null
    var hasAccount = false
    var balanceUnit: String? = null
    var hasDate = false
    var hasClock = false
    for (line in lines.drop(1)) {
        val direct = inline.matchEntire(line)
        val sign = event.matchEntire(line)
        val money = amount.matchEntire(line)
        val identifier = account.matchEntire(line)
        val remaining = balance.matchEntire(line)
        when {
            direct != null -> {
                if (label != null || raw != null) return null
                label = direct.groupValues[1]
                raw = direct.groupValues[2].replace(",", "").toLongOrNull() ?: return null
                currency = direct.groupValues[3]
                shape.add("event:$label:amount:$currency")
            }
            sign != null -> {
                if (label != null) return null
                label = sign.groupValues[1]
                shape.add("event:$label")
            }
            money != null -> {
                if (raw != null) return null
                raw = money.groupValues[2].replace(",", "").toLongOrNull() ?: return null
                currency = money.groupValues[3]
                shape.add("amount:" + money.groupValues[1] + ":" + currency)
            }
            identifier != null -> {
                if (hasAccount || identifier.groupValues[2].takeLast(4) != match.accountLast4) return null
                hasAccount = true
                shape.add("account:" + identifier.groupValues[1])
            }
            remaining != null -> {
                if (balanceUnit != null || remaining.groupValues[2].replace(",", "").toLongOrNull() == null) return null
                balanceUnit = remaining.groupValues[3]
                shape.add("balance:" + remaining.groupValues[1] + ":" + balanceUnit)
            }
            date.matches(line) -> {
                if (hasDate) return null
                hasDate = true
                shape.add(if (line.startsWith("تاریخ")) "labelled-date" else "date")
            }
            clock.matches(line) -> {
                if (hasClock) return null
                hasClock = true
                shape.add(if (line.startsWith("ساعت")) "labelled-clock" else "clock")
            }
            else -> return null
        }
    }
    val value = raw ?: return null
    val unit = currency ?: return null
    if (!hasAccount || label == null || (label == "واریز") != match.income ||
        (balanceUnit != null && balanceUnit != unit)) return null
    val rial = if (unit == "ریال") value else runCatching { Math.multiplyExact(value, 10L) }.getOrNull()
    if (rial != match.amountRial) return null
    val digest = MessageDigest.getInstance("SHA-256").digest(shape.joinToString("\n").toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return "learned-v1:" + match.bankId + ":" + digest + ":" + unit
}

internal fun isLearnedBankTemplate(bankId: String, template: String): Boolean =
    Regex("^learned-v1:" + Regex.escape(bankId) + ":[0-9a-f]{64}:(ریال|تومان)$").matches(template)
