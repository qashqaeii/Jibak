package ir.jibik.core.finance

/**
 * Built-in three-level household expense taxonomy. IDs are stable and never
 * derived from translated labels; user-defined categories come in a later phase.
 */
data class ExpenseCategory(
    val id: String,
    val title: String,
    val children: List<ExpenseCategory> = emptyList(),
)

object ExpenseCategoryCatalog {
    val roots: List<ExpenseCategory> = listOf(
        ExpenseCategory("food", "خوراک", listOf(
            ExpenseCategory("groceries", "خرید مواد غذایی", listOf(
                ExpenseCategory("legumes", "حبوبات"),
                ExpenseCategory("vegetables", "سبزیجات"),
                ExpenseCategory("fruits", "میوه"),
                ExpenseCategory("carbs", "برنج، نان و ماکارونی"),
                ExpenseCategory("protein", "گوشت، مرغ و ماهی"),
                ExpenseCategory("dairy", "لبنیات"),
                ExpenseCategory("eggs", "تخم‌مرغ"),
                ExpenseCategory("snacks", "تنقلات"),
                ExpenseCategory("drinks", "نوشیدنی"),
                ExpenseCategory("oil", "روغن و چاشنی‌ها"),
            )),
            ExpenseCategory("restaurant", "رستوران و غذای بیرون"),
        )),
        ExpenseCategory("home", "مسکن", listOf(
            ExpenseCategory("rent", "اجاره"),
            ExpenseCategory("building", "شارژ ساختمان"),
            ExpenseCategory("home_maintenance", "تعمیرات"),
            ExpenseCategory("home_supplies", "لوازم خانه"),
        )),
        ExpenseCategory("utilities", "قبوض و ارتباطات", listOf(
            ExpenseCategory("electricity", "برق"),
            ExpenseCategory("gas", "گاز"),
            ExpenseCategory("water", "آب"),
            ExpenseCategory("internet", "اینترنت"),
            ExpenseCategory("mobile", "تلفن همراه"),
        )),
        ExpenseCategory("transport", "حمل‌ونقل", listOf(
            ExpenseCategory("fuel", "سوخت"),
            ExpenseCategory("taxi", "تاکسی"),
            ExpenseCategory("transit", "حمل‌ونقل عمومی"),
            ExpenseCategory("car_service", "تعمیر خودرو"),
        )),
        ExpenseCategory("health", "سلامت", listOf(
            ExpenseCategory("doctor", "پزشک"),
            ExpenseCategory("medicine", "دارو"),
            ExpenseCategory("dental", "دندان‌پزشکی"),
        )),
        ExpenseCategory("clothes", "پوشاک"),
        ExpenseCategory("education", "آموزش"),
        ExpenseCategory("leisure", "تفریح و سفر"),
        ExpenseCategory("family", "خانواده و هدایا"),
        ExpenseCategory("other", "سایر هزینه‌ها"),
    )

    data class Path(val ids: List<String>, val labels: List<String>) {
        val displayName: String get() = labels.joinToString(" / ")
    }

    fun leaves(): List<Path> {
        fun visit(node: ExpenseCategory, ids: List<String>, labels: List<String>): List<Path> {
            val newIds = ids + node.id
            val newLabels = labels + node.title
            return if (node.children.isEmpty()) listOf(Path(newIds, newLabels))
            else node.children.flatMap { visit(it, newIds, newLabels) }
        }
        return roots.flatMap { visit(it, emptyList(), emptyList()) }
    }

    fun find(id: String): Path? = leaves().firstOrNull { id in it.ids }
}
