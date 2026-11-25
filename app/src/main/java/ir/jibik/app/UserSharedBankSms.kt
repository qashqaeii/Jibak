package ir.jibik.app

internal const val USER_SHARED_BANK_SENDER = "user-shared"

/** Timestamp from a user-confirmed Tehran calendar day and clock, not share arrival time. */
internal fun manualBankTimestamp(day: Long, clock: String): Long? {
    val value = Regex("^([0-9]{1,2}):([0-9]{2})$").matchEntire(BankSmsParser.normalise(clock).trim()) ?: return null
    val hour = value.groupValues[1].toInt()
    val minute = value.groupValues[2].toInt()
    if (day <= 0L || hour !in 0..23 || minute !in 0..59) return null
    return day + (hour * 60L + minute) * 60_000L
}

/** Explicit user import, never infers that the sender is a verified bank. */
internal fun parseUserSharedBankSms(body: String, at: Long, salt: String, otherBank: Boolean = false): BankSmsMatch? {
    if (at <= 0L) return null
    val cleaned = body.trim().replace("\r\n", "\n")
    val minute = at - at % 60_000L
    return BankSmsParser.parse(USER_SHARED_BANK_SENDER, cleaned, minute, salt,
        if (otherBank) "custom" else null)?.takeIf { it.amountToman != null }
}
