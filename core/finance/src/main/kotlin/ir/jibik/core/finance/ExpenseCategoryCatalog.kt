package ir.jibik.core.finance

/**
 * Stable offline-first categorisation. Stored category values remain human-readable
 * paths for backwards compatibility with Room v4. IDs are independent of translations.
 * A parent can be selected for quick entry; deepest leaves give full three-level detail.
 */
data class ExpenseCategory(
    val id: String,
    val title: String,
    val children: List<ExpenseCategory> = emptyList(),
)

private fun cat(id: String, title: String, vararg children: ExpenseCategory) =
    ExpenseCategory(id, title, children.toList())

data class CategoryPath(val ids: List<String>, val labels: List<String>) {
    val id: String get() = ids.last()
    val title: String get() = labels.last()
    val displayName: String get() = labels.joinToString(" / ")
    val rootId: String get() = ids.first()
    val depth: Int get() = ids.size
}

internal fun normaliseCategorySearch(text: String): String = text.trim().lowercase()
    .replace('ي', 'ی').replace('ك', 'ک').replace("‌", " ")
    .replace(Regex("[\\s\\u064B-\\u065F]+"), " ")

private fun paths(roots: List<ExpenseCategory>, leavesOnly: Boolean): List<CategoryPath> {
    fun walk(node: ExpenseCategory, ids: List<String>, labels: List<String>): List<CategoryPath> {
        val current = CategoryPath(ids + node.id, labels + node.title)
        return if (node.children.isEmpty()) listOf(current)
        else (if (leavesOnly) emptyList() else listOf(current)) +
            node.children.flatMap { walk(it, current.ids, current.labels) }
    }
    return roots.flatMap { walk(it, emptyList(), emptyList()) }
}

private fun search(categories: List<CategoryPath>, query: String): List<CategoryPath> {
    val q = normaliseCategorySearch(query)
    if (q.isBlank()) return categories
    return categories.filter { path ->
        normaliseCategorySearch(path.displayName).contains(q) ||
            path.ids.any { it.contains(q) }
    }.sortedWith(compareBy<CategoryPath> {
        if (normaliseCategorySearch(it.title).startsWith(q)) 0 else 1
    }.thenBy { it.depth }.thenBy { it.displayName })
}

object ExpenseCategoryCatalog {
    // Previous IDs/paths remain intact where possible; existing saved strings
    // always display as-is when they no longer match the current tree.
    val roots: List<ExpenseCategory> = listOf(
        cat("food", "خوراک",
            cat("groceries", "خرید مواد غذایی",
                cat("legumes", "حبوبات"), cat("vegetables", "سبزیجات"),
                cat("fruits", "میوه"), cat("carbs", "برنج، نان و ماکارونی"),
                cat("protein", "گوشت، مرغ و ماهی"), cat("dairy", "لبنیات"),
                cat("eggs", "تخم‌مرغ"), cat("snacks", "تنقلات"),
                cat("drinks", "نوشیدنی"), cat("oil", "روغن و چاشنی‌ها"),
                cat("nuts", "خشکبار و آجیل"), cat("sugar", "قند و شیرینی"),
                cat("frozen", "مواد غذایی منجمد"), cat("bread", "نان"),
                cat("potato", "سیب‌زمینی"), cat("breakfast", "صبحانه"),
            ),
            cat("restaurant", "رستوران و غذای بیرون",
                cat("restaurant_meals", "غذا در رستوران"),
                cat("takeaway", "سفارش غذا"), cat("cafe", "کافه"),
                cat("fastfood", "فست‌فود"),
            ),
        ),
        cat("home", "مسکن",
            cat("rent", "اجاره"), cat("building", "شارژ ساختمان"),
            cat("home_maintenance", "تعمیرات",
                cat("plumbing", "لوله‌کشی"), cat("electrical_repair", "برق‌کاری"),
                cat("painting", "رنگ و بازسازی"), cat("hvac_repair", "سرمایش و گرمایش"),
                cat("other_home_repair", "سایر تعمیرات"),
            ),
            cat("home_supplies", "لوازم خانه",
                cat("furniture", "مبلمان"), cat("appliances", "لوازم برقی"),
                cat("kitchenware", "وسایل آشپزخانه"),
                cat("cleaning", "شوینده و نظافت"), cat("decor", "دکوراسیون"),
                cat("consumables", "اقلام مصرفی خانه"),
            ),
        ),
        cat("utilities", "قبوض و ارتباطات",
            cat("energy", "انرژی و آب",
                cat("electricity", "برق"), cat("gas", "گاز"),
                cat("water", "آب"), cat("heating", "گرمایش"),
            ),
            cat("communications", "تلفن و اینترنت",
                cat("internet", "اینترنت"), cat("mobile", "تلفن همراه"),
                cat("landline", "تلفن ثابت"), cat("digital_subscription", "اشتراک خدمات دیجیتال"),
            ),
        ),
        cat("transport", "حمل‌ونقل",
            cat("commute", "رفت‌وآمد",
                cat("fuel", "سوخت"), cat("taxi", "تاکسی"),
                cat("ride_hailing", "تاکسی اینترنتی"), cat("transit", "حمل‌ونقل عمومی"),
                cat("parking", "پارکینگ"), cat("toll", "عوارض مسیر"),
            ),
            cat("vehicle", "خودرو",
                cat("car_service", "تعمیر خودرو"), cat("car_wash", "کارواش"),
                cat("car_parts", "لوازم خودرو"), cat("car_insurance", "بیمه خودرو"),
                cat("car_tax", "مالیات و عوارض خودرو"),
            ),
        ),
        cat("health", "سلامت",
            cat("healthcare", "درمان",
                cat("doctor", "پزشک"), cat("medicine", "دارو"),
                cat("dental", "دندان‌پزشکی"), cat("lab", "آزمایش"),
                cat("hospital", "بیمارستان"), cat("therapy", "مشاوره و درمان"),
                cat("optical", "عینک و بینایی"),
            ),
            cat("personal_care", "بهداشت و زیبایی",
                cat("hygiene", "بهداشت فردی"), cat("hair", "آرایشگاه"),
                cat("skincare", "مراقبت پوست"), cat("beauty", "خدمات زیبایی"),
            ),
        ),
        cat("clothes", "پوشاک",
            cat("apparel", "لباس",
                cat("daily_clothes", "لباس روزمره"), cat("formal_clothes", "لباس رسمی"),
                cat("children_clothes", "پوشاک کودک"), cat("sports_clothes", "پوشاک ورزشی"),
            ),
            cat("accessories", "کفش و اکسسوری",
                cat("shoes", "کفش"), cat("bags", "کیف"),
                cat("jewellery", "زیورآلات"), cat("tailoring", "خیاطی"),
            ),
        ),
        cat("education", "آموزش",
            cat("learning", "دوره و آموزش",
                cat("tuition", "شهریه"), cat("course", "دوره آموزشی"),
                cat("language", "زبان"), cat("online_course", "آموزش آنلاین"),
            ),
            cat("school", "مدرسه و تحصیل",
                cat("books", "کتاب"), cat("stationery", "لوازم‌التحریر"),
                cat("school_transport", "سرویس مدرسه"),
                cat("exam", "آزمون و کنکور"),
            ),
        ),
        cat("leisure", "تفریح و سفر",
            cat("entertainment", "تفریح",
                cat("cinema", "سینما"), cat("games", "بازی"),
                cat("concert", "کنسرت"), cat("sport", "ورزش و باشگاه"),
                cat("hobbies", "سرگرمی"),
            ),
            cat("travel", "سفر",
                cat("hotel", "اقامت"), cat("ticket", "بلیت سفر"),
                cat("tour", "تور"), cat("trip_food", "خوراک سفر"),
                cat("trip_other", "سایر هزینه سفر"),
            ),
        ),
        cat("family", "خانواده و هدایا",
            cat("children", "کودکان",
                cat("childcare", "مراقبت کودک"), cat("toys", "اسباب‌بازی"),
                cat("baby_supplies", "لوازم کودک"),
            ),
            cat("gifts", "هدیه و مناسبت",
                cat("birthday", "تولد"), cat("wedding", "عروسی"),
                cat("other_gift", "سایر هدایا"),
            ),
            cat("family_support", "کمک به خانواده"),
        ),
        cat("pets", "حیوانات خانگی",
            cat("pet_care", "نگهداری حیوان",
                cat("pet_food", "غذای حیوان"), cat("pet_vet", "دامپزشک"),
                cat("pet_supplies", "لوازم حیوان"),
            ),
        ),
        cat("charity", "خیریه و کمک",
            cat("donation", "کمک و نیکوکاری",
                cat("donate_people", "کمک به افراد"), cat("donate_org", "مؤسسات خیریه"),
            ),
        ),
        cat("insurance", "بیمه",
            cat("insurance_premiums", "حق بیمه",
                cat("health_insurance", "بیمه درمان"),
                cat("life_insurance", "بیمه عمر"),
                cat("property_insurance", "بیمه اموال"),
            ),
        ),
        cat("fees", "عوارض و کارمزد",
            cat("administrative", "هزینه‌های اداری",
                cat("bank_fee", "کارمزد بانک"),
                cat("government_fee", "عوارض و خدمات دولتی"),
                cat("tax", "مالیات"), cat("postage", "پست و ارسال"),
            ),
        ),
        cat("work", "کار و کسب‌وکار",
            cat("work_expense", "هزینه کار",
                cat("work_tools", "ابزار کار"), cat("work_travel", "رفت‌وآمد کاری"),
                cat("work_subscription", "اشتراک کاری"), cat("work_supplies", "ملزومات کار"),
            ),
        ),
        cat("other", "سایر هزینه‌ها",
            cat("other_personal", "سایر هزینه شخصی"),
            cat("other_household", "سایر هزینه خانه"),
        ),
    )

    private val everyPath by lazy { paths(roots, leavesOnly = false) }
    private val leafPaths by lazy { paths(roots, leavesOnly = true) }
    /** Kept as typealias-like nested class for existing callers. */
    data class Path(val ids: List<String>, val labels: List<String>) {
        val displayName: String get() = labels.joinToString(" / ")
        val title: String get() = labels.last()
        val depth: Int get() = ids.size
    }

    private fun CategoryPath.compat() = Path(ids, labels)
    fun all(): List<Path> = everyPath.map { it.compat() }
    fun leaves(): List<Path> = leafPaths.map { it.compat() }
    fun find(id: String): Path? = everyPath.firstOrNull { it.id == id }?.compat()
    fun byDisplayName(value: String): Path? = everyPath.firstOrNull {
        it.displayName == value || (it.depth == 1 && it.title == value)
    }?.compat()
    fun search(query: String): List<Path> = search(everyPath, query).map { it.compat() }
    fun children(parentIds: List<String> = emptyList()): List<ExpenseCategory> {
        if (parentIds.isEmpty()) return roots
        var current = roots
        var node: ExpenseCategory? = null
        for (id in parentIds) {
            node = current.firstOrNull { it.id == id } ?: return emptyList()
            current = node.children
        }
        return current
    }
}

object IncomeCategoryCatalog {
    val roots = listOf(
        cat("income_salary", "حقوق و دستمزد",
            cat("income_job", "دریافتی شغلی",
                cat("income_monthly_salary", "حقوق ثابت"),
                cat("income_overtime", "اضافه‌کاری"),
                cat("income_bonus", "پاداش"),
                cat("income_commission", "پورسانت"),
            ),
            cat("income_benefits", "مزایا",
                cat("income_allowance", "کمک‌هزینه"),
                cat("income_year_end", "عیدی و سنوات"),
            ),
        ),
        cat("income_business", "کسب‌وکار",
            cat("income_services", "خدمات",
                cat("income_freelance", "فریلنسری"),
                cat("income_project", "پروژه"),
                cat("income_consulting", "مشاوره"),
            ),
            cat("income_sales", "فروش",
                cat("income_product", "فروش کالا"),
                cat("income_shop", "فروشگاه"),
            ),
        ),
        cat("income_rental", "درآمد دارایی",
            cat("income_property", "اجاره",
                cat("income_property_rent", "اجاره ملک"),
                cat("income_equipment_rent", "اجاره تجهیزات"),
            ),
            cat("income_investment", "بازده دارایی",
                cat("income_interest", "سود سپرده"),
                cat("income_dividend", "سود سهام"),
            ),
        ),
        cat("income_gifts", "هدیه و حمایت",
            cat("income_present", "هدایا",
                cat("income_cash_gift", "هدیه نقدی"),
                cat("income_family_help", "کمک خانواده"),
            ),
        ),
        cat("income_refund", "بازگشت وجه",
            cat("income_reimbursement", "بازپرداخت",
                cat("income_purchase_refund", "بازگشت خرید"),
                cat("income_insurance_claim", "دریافت از بیمه"),
            ),
        ),
        cat("income_sale", "فروش دارایی",
            cat("income_asset_sales", "فروش",
                cat("income_secondhand", "فروش کالای دست دوم"),
                cat("income_vehicle_sale", "فروش خودرو"),
            ),
        ),
        cat("income_other", "سایر درآمدها",
            cat("income_misc", "درآمد متفرقه"),
        ),
    )
    private val everyPath by lazy { paths(roots, leavesOnly = false) }
    fun all(): List<CategoryPath> = everyPath
    fun leaves(): List<CategoryPath> = paths(roots, leavesOnly = true)
    fun find(id: String): CategoryPath? = everyPath.firstOrNull { it.id == id }
    fun search(query: String): List<CategoryPath> = search(everyPath, query)
    fun children(parentIds: List<String> = emptyList()): List<ExpenseCategory> {
        if (parentIds.isEmpty()) return roots
        var current = roots
        for (id in parentIds) {
            current = current.firstOrNull { it.id == id }?.children ?: return emptyList()
        }
        return current
    }
}
