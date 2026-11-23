# جیبیک | Jibik 🦊

اپلیکیشن مدیریت مالی شخصی و خانوادگی، فارسی و RTL، با Android Native **Kotlin + Jetpack Compose** و سرور **Django REST Framework + PostgreSQL**. نقشهٔ راه مبنای توسعه: [MASTER_PLAN_FA.md](docs/MASTER_PLAN_FA.md).

> وضعیت: **Pre-alpha**؛ بخش‌هایی از Android و بک‌اند واقعاً پیاده‌سازی و در CI آزمایش شده‌اند. **هنوز آماده انتشار عمومی یا کافه‌بازار نیست.**

## Android موجود
- صفحه خوش‌آمدگویی و هویت بصری سبز جنگلی/کرم/نارنجی؛ WebP شفاف جیبو و لوگوی Launcher.
- ثبت آفلاین درآمد، هزینه و انتقال بین حساب‌های مستقل با Room (schema v4 و مهاجرت از داده قدیمی).
- ثبت یک خرید چندقلمی به‌صورت یک پرداخت اتمیک، دسته‌بندی سه‌سطحی خوراک و سایر گروه‌ها.
- بودجه ماه شمسی، گزارش دسته‌ها و جریان مالی، جستجوی تراکنش‌ها.
- اهداف پس‌انداز بر پایه موجودی واقعی حساب متصل به هر هدف.
- Android CI تست ماژول‌ها و Debug APK تولید می‌کند. آخرین نتیجه را در [Actions](https://github.com/qashqaeii/Jibak/actions) بررسی کنید.

## Django موجود
- Django REST و PostgreSQL با تنظیمات توسعه محلی SQLite؛ در production کلید محرمانه و PostgreSQL الزامی‌اند.
- API اولیه خانواده، حساب‌ها و تراکنش‌های شخصی/مشترک با کنترل membership در سمت سرور و Idempotency Key.
- **Sync pull** اولیه با cursor امضاشده، محدود به دسترسی فعلی کاربر.
- تست دسترسی، محرمانگی، دوباره‌ثبت نشدن تراکنش و صفحه‌بندی Sync؛ Workflow مستقل PostgreSQL: [backend.yml](.github/workflows/backend.yml).
- مستندات راه‌اندازی [backend/README.md](backend/README.md).

**فعلاً OTP عمومی، توکن‌های اندروید، Sync push کامل، عضویت/دعوت دوطرفه، پرداخت واقعی و Entitlement فعال نشده‌اند.** Android هنوز به Backend آنلاین متصل نشده است.

## اجرای تست
Android (JDK 17، Android SDK 36، Gradle 8.13):

```bash
gradle :core:finance:test :app:testDebugUnitTest :app:assembleDebug
```

Backend:

```bash
cd backend
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
export JIBIK_DEBUG=true
python manage.py migrate
python -m pytest finance/tests.py -q
```

## استاندارد تحویل
بعد از هر بخش: Commit، CI، لینک Artifact فقط در صورت موفقیت، به‌روزرسانی [وضعیت توسعه](docs/IMPLEMENTATION_STATUS_FA.md). فایل Release امضاشده و اطلاعات حساس هنوز در Repo قرار ندارند. از نسخه Pre-alpha برای داده‌های مالی حساس واقعی استفاده نکنید.
