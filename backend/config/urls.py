from django.contrib import admin
from django.urls import path, include
from finance.auth_views import AiMobileTokenView
urlpatterns = [
    path("admin/", admin.site.urls),
    path("api/v1/auth/token/", AiMobileTokenView.as_view()),
    path("api/v1/", include("finance.urls")),
]
