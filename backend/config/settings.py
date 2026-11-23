"""Jibik API. PostgreSQL required outside explicit local development."""
import os
from pathlib import Path
from django.core.exceptions import ImproperlyConfigured

BASE_DIR = Path(__file__).resolve().parent.parent
DEBUG = os.getenv("JIBIK_DEBUG", "false").lower() == "true"
SECRET_KEY = os.getenv("JIBIK_SECRET_KEY") or (
    "development-only-unsafe-secret" if DEBUG else ""
)
if not SECRET_KEY:
    raise ImproperlyConfigured("JIBIK_SECRET_KEY required")
ALLOWED_HOSTS = os.getenv("JIBIK_ALLOWED_HOSTS", "localhost,127.0.0.1,testserver").split(",")
INSTALLED_APPS = [
    "django.contrib.admin", "django.contrib.auth",
    "django.contrib.contenttypes", "django.contrib.sessions",
    "django.contrib.messages", "django.contrib.staticfiles",
    "rest_framework", "finance",
]
MIDDLEWARE = [
    "django.middleware.security.SecurityMiddleware",
    "django.contrib.sessions.middleware.SessionMiddleware",
    "django.middleware.common.CommonMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "django.contrib.auth.middleware.AuthenticationMiddleware",
    "django.contrib.messages.middleware.MessageMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
]
ROOT_URLCONF = "config.urls"
TEMPLATES = [{
    "BACKEND": "django.template.backends.django.DjangoTemplates",
    "DIRS": [], "APP_DIRS": True,
    "OPTIONS": {"context_processors": [
        "django.template.context_processors.debug",
        "django.template.context_processors.request",
        "django.contrib.auth.context_processors.auth",
        "django.contrib.messages.context_processors.messages",
    ]},
}]
WSGI_APPLICATION = "config.wsgi.application"
if os.getenv("JIBIK_DB_HOST"):
    DATABASES = {"default": {
        "ENGINE": "django.db.backends.postgresql",
        "NAME": os.environ["JIBIK_DB_NAME"],
        "USER": os.environ["JIBIK_DB_USER"],
        "PASSWORD": os.environ["JIBIK_DB_PASSWORD"],
        "HOST": os.environ["JIBIK_DB_HOST"],
        "PORT": os.getenv("JIBIK_DB_PORT", "5432"),
        "OPTIONS": {"sslmode": os.getenv("JIBIK_DB_SSLMODE", "require")},
    }}
elif DEBUG:
    DATABASES = {"default": {
        "ENGINE": "django.db.backends.sqlite3",
        "NAME": BASE_DIR / "db.sqlite3",
    }}
else:
    raise ImproperlyConfigured("PostgreSQL JIBIK_DB_* required in production")
LANGUAGE_CODE = "fa"
TIME_ZONE = "UTC"
USE_TZ = True
DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"
STATIC_URL = "static/"
SESSION_COOKIE_HTTPONLY = True
SESSION_COOKIE_SECURE = not DEBUG
CSRF_COOKIE_SECURE = not DEBUG
SECURE_CONTENT_TYPE_NOSNIFF = True
REST_FRAMEWORK = {
    "DEFAULT_AUTHENTICATION_CLASSES": ["rest_framework.authentication.SessionAuthentication"],
    "DEFAULT_PERMISSION_CLASSES": ["rest_framework.permissions.IsAuthenticated"],
    "DEFAULT_RENDERER_CLASSES": ["rest_framework.renderers.JSONRenderer"],
    "DEFAULT_THROTTLE_CLASSES": [
        "rest_framework.throttling.AnonRateThrottle",
        "rest_framework.throttling.UserRateThrottle",
    ],
    "DEFAULT_THROTTLE_RATES": {"anon": "30/hour", "user": "1000/hour"},
}

# ArvanCloud AIaaS. Server-only credentials; disabled until explicitly configured.
ARVAN_AI_ENABLED = os.getenv("ARVAN_AI_ENABLED", "false").lower() == "true"
ARVAN_AI_BASE_URL = os.getenv("ARVAN_AI_BASE_URL", "")
ARVAN_AI_API_KEY = os.getenv("ARVAN_AI_API_KEY", "")
ARVAN_AI_MODEL = os.getenv("ARVAN_AI_MODEL", "")
ARVAN_AI_TIMEOUT_SECONDS = int(os.getenv("ARVAN_AI_TIMEOUT_SECONDS", "10"))
ARVAN_AI_MAX_RETRIES = int(os.getenv("ARVAN_AI_MAX_RETRIES", "1"))
ARVAN_AI_MAX_TOKENS = int(os.getenv("ARVAN_AI_MAX_TOKENS", "700"))
ARVAN_AI_TEMPERATURE = float(os.getenv("ARVAN_AI_TEMPERATURE", "0.2"))
ARVAN_AI_USER_DAILY_LIMIT = int(os.getenv("ARVAN_AI_USER_DAILY_LIMIT", "10"))
ARVAN_AI_GLOBAL_DAILY_LIMIT = int(os.getenv("ARVAN_AI_GLOBAL_DAILY_LIMIT", "200"))
