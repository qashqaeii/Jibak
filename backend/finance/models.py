"""Financial ledger amounts are integral RIALS (not floating-point tomans)."""
import uuid
from django.conf import settings
from django.db import models
from django.db.models import Q


class Household(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    name = models.CharField(max_length=80)
    created_by = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT)
    created_at = models.DateTimeField(auto_now_add=True)


class Membership(models.Model):
    class Role(models.TextChoices):
        OWNER = "owner", "Owner"
        EDITOR = "editor", "Editor"
        VIEWER = "viewer", "Viewer"

    household = models.ForeignKey(Household, on_delete=models.CASCADE, related_name="memberships")
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE)
    role = models.CharField(max_length=12, choices=Role.choices)

    class Meta:
        constraints = [
            models.UniqueConstraint(fields=["household", "user"], name="unique_household_member"),
        ]


class FinancialAccount(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    title = models.CharField(max_length=80)
    opening_balance_rial = models.BigIntegerField(default=0)
    owner = models.ForeignKey(
        settings.AUTH_USER_MODEL, null=True, blank=True, on_delete=models.PROTECT,
        related_name="personal_financial_accounts",
    )
    household = models.ForeignKey(
        Household, null=True, blank=True, on_delete=models.PROTECT, related_name="accounts",
    )
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        constraints = [models.CheckConstraint(
            condition=(Q(owner__isnull=False, household__isnull=True) |
                       Q(owner__isnull=True, household__isnull=False)),
            name="account_exactly_one_scope",
        )]

    def scope_key(self):
        if self.household_id:
            return ("household", self.household_id)
        return ("personal", self.owner_id)


class LedgerTransaction(models.Model):
    class Kind(models.TextChoices):
        EXPENSE = "expense", "Expense"
        INCOME = "income", "Income"
        TRANSFER = "transfer", "Transfer"

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    created_by = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.PROTECT)
    idempotency_key = models.CharField(max_length=64)
    kind = models.CharField(max_length=10, choices=Kind.choices)
    amount_rial = models.BigIntegerField()
    from_account = models.ForeignKey(
        FinancialAccount, null=True, blank=True, on_delete=models.PROTECT,
        related_name="outgoing_transactions",
    )
    to_account = models.ForeignKey(
        FinancialAccount, null=True, blank=True, on_delete=models.PROTECT,
        related_name="incoming_transactions",
    )
    category = models.CharField(max_length=120, default="", blank=True)
    note = models.CharField(max_length=500, default="", blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        constraints = [
            models.UniqueConstraint(
                fields=["created_by", "idempotency_key"],
                name="unique_creator_idempotency_key",
            ),
            models.CheckConstraint(condition=Q(amount_rial__gt=0),
                                   name="positive_transaction_rial"),
        ]
        ordering = ["-created_at", "-id"]
