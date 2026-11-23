from django.urls import path
from .views import HealthView, HouseholdView, AccountView, TransactionView
from .sync import SyncPullView

urlpatterns = [
    path("health/", HealthView.as_view()),
    path("households/", HouseholdView.as_view()),
    path("accounts/", AccountView.as_view()),
    path("transactions/", TransactionView.as_view()),
    path("sync/pull/", SyncPullView.as_view()),
]
