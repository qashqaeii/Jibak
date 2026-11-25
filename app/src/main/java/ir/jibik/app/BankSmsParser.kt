package ir.jibik.app

import java.security.MessageDigest
import java.util.Locale

/** Parsing only: raw SMS never travels to backend or AI services. */
internal data class BankSmsMatch(
    val bankId: String,
    val bankTitle: String,
    val amountRial: Long,
    val income: Boolean,
    val accountLast4: String,
    val sender: String,
    val receivedAtMillis: Long,
    val fingerprint: String,
    // Inbox receipt-time alias for older imports; never persisted as raw SMS.
    val alternateFingerprint: String? = null,
) {
    val amountToman: Long? get() =
        if (amountRial > 0L && amountRial % 10L == 0L) amountRial / 10L else null
}

internal data class SupportedBank(
    val id: String,
    val title: String,
    val aliases: List<String>,
    val senderHints: List<String>,
)

internal object BankSmsParser {
    val banks = listOf(
        SupportedBank("melli", "بانک ملی", listOf("بانک ملی ایران", "بانک ملی", "بانک ملي ايران", "بانك ملي ايران"), listOf("bmi", "bankmelli", "bank-melli")),
        SupportedBank("blu", "بلوبانک", listOf("بلو", "بلوبانک", "bluebank"), listOf("blubank", "blu-bank", "blu")),
        SupportedBank("mellat", "بانک ملت", listOf("بانک ملت"), listOf("mellat", "bankmellat")),
        SupportedBank("saderat", "بانک صادرات", listOf("بانک صادرات"), listOf("bsi", "saderat")),
        SupportedBank("sepah", "بانک سپه", listOf("بانک سپه"), listOf("sepah")),
        SupportedBank("tejarat", "بانک تجارت", listOf("بانک تجارت"), listOf("tejarat")),
        SupportedBank("saman", "بانک سامان", listOf("بانک سامان"), listOf("saman")),
        SupportedBank("pasargad", "بانک پاسارگاد", listOf("بانک پاسارگاد"), listOf("pasargad")),
        SupportedBank("parsian", "بانک پارسیان", listOf("بانک پارسیان"), listOf("parsian")),
        SupportedBank("resalat", "بانک رسالت", listOf("بانک رسالت"), listOf("resalat")),
        SupportedBank("shahr", "بانک شهر", listOf("بانک شهر"), listOf("bankshahr")),
        SupportedBank("refah", "بانک رفاه", listOf("بانک رفاه"), listOf("refah")),
        SupportedBank("maskan", "بانک مسکن", listOf("بانک مسکن"), listOf("maskan")),
        SupportedBank("ayandeh", "بانک آینده", listOf("بانک آینده"), listOf("ayandeh")),
        SupportedBank("keshavarzi", "بانک کشاورزی", listOf("بانک کشاورزی"), listOf("keshavarzi")),
        // Never autodetect this option: only an explicitly introduced sender.
        SupportedBank("custom", "بانک دیگر / سرشماره اختصاصی", emptyList(), emptyList()),
    )
    private val otp = Regex("رمز|یکبارمصرف|یک بار مصرف|کد( تایید| تأیید| ورود| فعال)|otp|verification|رمز پویا", RegexOption.IGNORE_CASE)
    private val dangerous = Regex("https?://|www\\.|\\bbit\\.ly\\b", RegexOption.IGNORE_CASE)
    private val labelAmount = Regex("(?:انتقال|خرید ?اینترنتی|خرید|واریز|برداشت|پرداخت|دریافت|تراکنش)\\s*[:：]?\\s*([0-9][0-9,٬]*)\\s*([+\\-])")
    private val moneyWithUnit = Regex("([0-9][0-9,٬]*)\\s*(ریال|تومان)")
    private val labeledAmount = Regex("(?:مبلغ|وجه)\\s*[:：]?\\s*([0-9][0-9,٬]*)\\s*(ریال|تومان)?")
    private val account = Regex("(?:حساب|کارت)\\s*[:：]?\\s*([0-9*xX۰-۹٠-٩-]{2,24})")

    fun normalise(input: String): String = buildString(input.length) {
        for (ch in input) append(when (ch) {
            in '۰'..'۹' -> '0' + (ch - '۰')
            in '٠'..'٩' -> '0' + (ch - '٠')
            'ي', 'ى' -> 'ی'
            'ك' -> 'ک'
            '٬', '،' -> ','
            0x200C.toChar(), 0x200F.toChar(), 0x200E.toChar() -> ' '
            else -> ch
        })
    }.lowercase(Locale.ROOT)

    fun bankFor(sender: String, body: String): SupportedBank? {
        val header = normalise(body).lines().take(2).joinToString(" ").trim()
        val address = normalise(sender).trim()
        return banks.firstOrNull { bank ->
            bank.aliases.any { normalise(it) in header } ||
                bank.senderHints.any { address.contains(it) && address.length <= 40 }
        }
    }

    /**
     * Fail-closed. National Bank signed amounts are Rial by their known template.
     * Other templates require an explicit currency label.
     */
    fun parse(sender: String, body: String, timestamp: Long, salt: String,
        approvedBankId: String? = null): BankSmsMatch? {
        if (body.length !in 12..1500 || timestamp <= 0L || salt.length < 8) return null
        val normalized = normalise(body)
        if (otp.containsMatchIn(normalized) || dangerous.containsMatchIn(normalized)) return null
        if (Regex("ناموفق|انجام نشد|لغو شد|رد شد|عدم موفقیت|نامعتبر|تبلیغات|failed|declined|unsuccessful").containsMatchIn(normalized)) return null
        val detected = bankFor(sender, body)
        val bank = if (approvedBankId == null) detected ?: return null else {
            val expected = banks.firstOrNull { it.id == approvedBankId } ?: return null
            if (detected != null && detected.id != expected.id) return null
            expected
        }
        val signedMatches = labelAmount.findAll(normalized).toList()
        if (signedMatches.size > 1) return null
        val signed = signedMatches.singleOrNull()
        val income: Boolean
        val amountString: String
        val unit: String
        when {
            bank.id == "melli" && signed != null -> {
                income = signed.groupValues[2] == "+"
                amountString = signed.groupValues[1]
                unit = "ریال"
            }
            else -> {
                val incoming = Regex("واریز|دریافت|بستانکار|به حساب شما نشست|به حساب شما واریز").containsMatchIn(normalized)
                val outgoing = Regex("برداشت|خرید|پرداخت|بدهکار|از حساب شما پرید|کسر شد").containsMatchIn(normalized)
                if (incoming == outgoing) return null
                income = incoming
                // A balance may appear before the transaction amount.
                // Prefer a transaction-labeled sum and reject balance lines.
                val labelled = labeledAmount.findAll(normalized).firstOrNull { hit ->
                    val line = normalized.substring(
                        normalized.lastIndexOf('\n', hit.range.first).let { it + 1 },
                        hit.range.first,
                    )
                    !Regex("مانده|موجودی|باقیمانده|موجودي").containsMatchIn(line)
                }
                val amount = labelled ?: moneyWithUnit.findAll(normalized).firstOrNull { hit ->
                    val line = normalized.substring(
                        normalized.lastIndexOf('\n', hit.range.first).let { it + 1 },
                        hit.range.first,
                    )
                    !Regex("مانده|موجودی|باقیمانده|موجودي").containsMatchIn(line)
                } ?: return null
                amountString = amount.groupValues[1]
                unit = amount.groupValues.getOrNull(2).orEmpty()
                if (unit.isBlank()) return null
            }
        }
        if (!Regex("[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+").matches(amountString)) return null
        val numeric = amountString.replace(",", "")
        val rawAmount = numeric.toLongOrNull()?.takeIf { it > 0L } ?: return null
        val rial = try {
            if (unit == "تومان") Math.multiplyExact(rawAmount, 10L) else rawAmount
        } catch (_: ArithmeticException) { return null }
        if (rial <= 0L) return null
        val last4 = account.find(normalized)?.groupValues?.get(1)
            ?.filter { it.isDigit() }?.takeLast(4).orEmpty()
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(
            (salt + "|" + normalise(sender) + "|" + timestamp + "|" + normalized)
                .toByteArray(Charsets.UTF_8)
        ).joinToString("") { "%02x".format(it) }
        return BankSmsMatch(
            bank.id, bank.title, rial, income, last4, sender.trim().take(48),
            timestamp, fingerprint,
        )
    }
}
