from django.urls import path
from .views import HealthView, HouseholdView, AccountView, TransactionView
from .sync import SyncPullView
from .ai_views import AiStatusView, AiAnalysisView

urlpatterns = [
    path("health/", HealthView.as_view()),
    path("households/", HouseholdView.as_view()),
    path("accounts/", AccountView.as_view()),
    path("transactions/", TransactionView.as_view()),
    path("sync/pull/", SyncPullView.as_view()),
    path("ai/status/", AiStatusView.as_view()),
    path("ai/analyze/", AiAnalysisView.as_view()),
]
