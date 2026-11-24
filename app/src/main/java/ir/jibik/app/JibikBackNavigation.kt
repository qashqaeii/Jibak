package ir.jibik.app

/**
 * Route resolution for the hardware/system back gesture.
 * Dialogs keep their native back handling in their own windows.
 * A hidden nested page in a different bottom tab must never consume Back.
 */
internal enum class JibikBackRoute {
    OBLIGATIONS, CATEGORIES, GOALS, BANK_SMS, HOME, NONE
}

internal fun resolveJibikBackRoute(
    section: Section,
    showObligations: Boolean,
    showCategoryManager: Boolean,
    showGoals: Boolean,
    showBankSms: Boolean,
): JibikBackRoute = when {
    section == Section.ACCOUNTS && showObligations -> JibikBackRoute.OBLIGATIONS
    section == Section.ACCOUNTS && showCategoryManager -> JibikBackRoute.CATEGORIES
    section == Section.ACCOUNTS && showGoals -> JibikBackRoute.GOALS
    section == Section.TRANSACTIONS && showBankSms -> JibikBackRoute.BANK_SMS
    section != Section.HOME -> JibikBackRoute.HOME
    else -> JibikBackRoute.NONE
}
