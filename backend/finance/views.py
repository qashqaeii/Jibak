"""Tenant-scoped v1 finance endpoints; session auth is development-only for mobile."""
from django.contrib.auth import get_user_model
from django.db import transaction
from django.db.models import Q, Sum
from rest_framework import serializers, status
from rest_framework.exceptions import PermissionDenied, ValidationError
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView

from .models import FinancialAccount, Household, Membership, LedgerTransaction


def can_write(user, account):
    if account.household_id is None:
        return account.owner_id == user.pk
    return Membership.objects.filter(
        user=user, household_id=account.household_id, role__in=["owner", "editor"]
    ).exists()


def visible_accounts(user):
    return FinancialAccount.objects.filter(
        Q(owner=user) | Q(household__memberships__user=user)
    ).distinct()


class HouseholdSerializer(serializers.ModelSerializer):
    class Meta:
        model = Household
        fields = ["id", "name", "created_at"]
        read_only_fields = ["id", "created_at"]


class AccountSerializer(serializers.ModelSerializer):
    balance_rial = serializers.SerializerMethodField()
    class Meta:
        model = FinancialAccount
        fields = ["id", "title", "opening_balance_rial", "household", "balance_rial", "created_at"]
        read_only_fields = ["id", "balance_rial", "created_at"]
        extra_kwargs = {"household": {"required": False, "allow_null": True}}

    def validate_opening_balance_rial(self, amount):
        if amount < 0:
            raise serializers.ValidationError("Opening balance must be nonnegative")
        return amount

    def get_balance_rial(self, account):
        received = account.incoming_transactions.aggregate(v=Sum("amount_rial"))["v"] or 0
        paid = account.outgoing_transactions.aggregate(v=Sum("amount_rial"))["v"] or 0
        return account.opening_balance_rial + received - paid


class TransactionSerializer(serializers.ModelSerializer):
    class Meta:
        model = LedgerTransaction
        fields = ["id", "idempotency_key", "kind", "amount_rial", "from_account",
                  "to_account", "category", "note", "created_at"]
        read_only_fields = ["id", "created_at"]

    def validate_amount_rial(self, amount):
        if amount <= 0:
            raise serializers.ValidationError("Transaction amount must be positive")
        return amount


class HealthView(APIView):
    permission_classes = [AllowAny]
    authentication_classes = []
    def get(self, request):
        return Response({"status": "ok", "service": "jibik-api", "version": "v1"})


class HouseholdView(APIView):
    def get(self, request):
        qs = Household.objects.filter(memberships__user=request.user).distinct()
        return Response(HouseholdSerializer(qs, many=True).data)

    @transaction.atomic
    def post(self, request):
        form = HouseholdSerializer(data=request.data)
        form.is_valid(raise_exception=True)
        household = form.save(created_by=request.user)
        Membership.objects.create(
            household=household, user=request.user, role=Membership.Role.OWNER
        )
        return Response(HouseholdSerializer(household).data, status=status.HTTP_201_CREATED)


class AccountView(APIView):
    def get(self, request):
        qs = visible_accounts(request.user).order_by("created_at")
        return Response(AccountSerializer(qs, many=True).data)

    def post(self, request):
        form = AccountSerializer(data=request.data)
        form.is_valid(raise_exception=True)
        household = form.validated_data.get("household")
        if household and not Membership.objects.filter(
            household=household, user=request.user, role__in=["owner", "editor"]
        ).exists():
            raise PermissionDenied("Cannot create an account in this household")
        item = form.save(owner=None if household else request.user, household=household)
        return Response(AccountSerializer(item).data, status=status.HTTP_201_CREATED)


class TransactionView(APIView):
    def get(self, request):
        accessible = visible_accounts(request.user)
        qs = LedgerTransaction.objects.filter(
            Q(from_account__in=accessible) | Q(to_account__in=accessible)
        ).distinct().order_by("-created_at", "-id")[:100]
        return Response(TransactionSerializer(qs, many=True).data)

    def post(self, request):
        form = TransactionSerializer(data=request.data)
        form.is_valid(raise_exception=True)
        value = form.validated_data
        src, dst = value.get("from_account"), value.get("to_account")
        kind = value["kind"]
        if kind == "expense":
            direction_ok = src is not None and dst is None
        elif kind == "income":
            direction_ok = src is None and dst is not None
        else:
            direction_ok = (src is not None and dst is not None and src.pk != dst.pk and
                            ((src.household_id is not None and
                              src.household_id == dst.household_id) or
                             (src.household_id is None and dst.household_id is None and
                              src.owner_id == dst.owner_id)))
        if not direction_ok:
            raise ValidationError({"accounts": "Invalid direction or cross-scope transfer"})
        for account in (src, dst):
            if account and not can_write(request.user, account):
                raise PermissionDenied("Not permitted to edit this account")
        # Serialize requests from the same user; prevents double posting during retries.
        with transaction.atomic():
            get_user_model().objects.select_for_update().get(pk=request.user.pk)
            previous = LedgerTransaction.objects.filter(
                created_by=request.user, idempotency_key=value["idempotency_key"]
            ).first()
            if previous is not None:
                fields = ("kind", "amount_rial", "from_account", "to_account", "category", "note")
                if any(getattr(previous, name) != value.get(name, "") for name in fields):
                    raise ValidationError({"idempotency_key": "Key reused with different contents"})
                return Response(TransactionSerializer(previous).data, status=status.HTTP_200_OK)
            involved = [a.pk for a in (src, dst) if a]
            list(FinancialAccount.objects.select_for_update()
                 .filter(pk__in=involved).order_by("pk"))
            item = LedgerTransaction.objects.create(created_by=request.user, **value)
        return Response(TransactionSerializer(item).data, status=status.HTTP_201_CREATED)
