import uuid
import django.db.models.deletion
from django.conf import settings
from django.db import migrations, models
from django.db.models import Q


class Migration(migrations.Migration):
    initial = True
    dependencies = [migrations.swappable_dependency(settings.AUTH_USER_MODEL)]

    operations = [
        migrations.CreateModel(
            name="Household",
            fields=[
                ("id", models.UUIDField(default=uuid.uuid4, editable=False, primary_key=True, serialize=False)),
                ("name", models.CharField(max_length=80)),
                ("created_at", models.DateTimeField(auto_now_add=True)),
                ("created_by", models.ForeignKey(on_delete=django.db.models.deletion.PROTECT, to=settings.AUTH_USER_MODEL)),
            ],
        ),
        migrations.CreateModel(
            name="Membership",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("role", models.CharField(choices=[("owner", "Owner"), ("editor", "Editor"), ("viewer", "Viewer")], max_length=12)),
                ("household", models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, related_name="memberships", to="finance.household")),
                ("user", models.ForeignKey(on_delete=django.db.models.deletion.CASCADE, to=settings.AUTH_USER_MODEL)),
            ],
        ),
        migrations.AddConstraint(
            model_name="membership",
            constraint=models.UniqueConstraint(fields=("household", "user"), name="unique_household_member"),
        ),
        migrations.CreateModel(
            name="FinancialAccount",
            fields=[
                ("id", models.UUIDField(default=uuid.uuid4, editable=False, primary_key=True, serialize=False)),
                ("title", models.CharField(max_length=80)),
                ("opening_balance_rial", models.BigIntegerField(default=0)),
                ("created_at", models.DateTimeField(auto_now_add=True)),
                ("owner", models.ForeignKey(blank=True, null=True, on_delete=django.db.models.deletion.PROTECT, related_name="personal_financial_accounts", to=settings.AUTH_USER_MODEL)),
                ("household", models.ForeignKey(blank=True, null=True, on_delete=django.db.models.deletion.PROTECT, related_name="accounts", to="finance.household")),
            ],
        ),
        migrations.AddConstraint(
            model_name="financialaccount",
            constraint=models.CheckConstraint(
                condition=(Q(owner__isnull=False, household__isnull=True) |
                           Q(owner__isnull=True, household__isnull=False)),
                name="account_exactly_one_scope",
            ),
        ),
        migrations.CreateModel(
            name="LedgerTransaction",
            fields=[
                ("id", models.UUIDField(default=uuid.uuid4, editable=False, primary_key=True, serialize=False)),
                ("idempotency_key", models.CharField(max_length=64)),
                ("kind", models.CharField(choices=[("expense", "Expense"), ("income", "Income"), ("transfer", "Transfer")], max_length=10)),
                ("amount_rial", models.BigIntegerField()),
                ("category", models.CharField(blank=True, default="", max_length=120)),
                ("note", models.CharField(blank=True, default="", max_length=500)),
                ("created_at", models.DateTimeField(auto_now_add=True)),
                ("created_by", models.ForeignKey(on_delete=django.db.models.deletion.PROTECT, to=settings.AUTH_USER_MODEL)),
                ("from_account", models.ForeignKey(blank=True, null=True, on_delete=django.db.models.deletion.PROTECT, related_name="outgoing_transactions", to="finance.financialaccount")),
                ("to_account", models.ForeignKey(blank=True, null=True, on_delete=django.db.models.deletion.PROTECT, related_name="incoming_transactions", to="finance.financialaccount")),
            ],
            options={"ordering": ["-created_at", "-id"]},
        ),
        migrations.AddConstraint(
            model_name="ledgertransaction",
            constraint=models.UniqueConstraint(fields=("created_by", "idempotency_key"), name="unique_creator_idempotency_key"),
        ),
        migrations.AddConstraint(
            model_name="ledgertransaction",
            constraint=models.CheckConstraint(condition=Q(amount_rial__gt=0), name="positive_transaction_rial"),
        ),
    ]
