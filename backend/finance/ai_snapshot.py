"""Strictly anonymous aggregates supplied by an explicitly consenting Android user.

No raw ledger, account title, transaction note, IDs, location or free-text query.
Financial amounts stay integers in tomans on device and become exact rials for AI.
"""
from rest_framework import serializers

from .ai_provider import AiError

CATEGORY_LABELS = {
    "food": "خوراک",
    "housing": "مسکن",
    "transport": "حمل‌ونقل",
    "bills": "قبوض",
    "health": "سلامت",
    "education": "آموزش",
    "shopping": "خرید",
    "entertainment": "تفریح",
    "other": "سایر",
}
MAX_TOMAN = 900_000_000_000_000_000  # <= signed 64-bit rials after *10


class StrictSummarySerializer(serializers.Serializer):
    """Never silently accept note/record fields on the mobile AI boundary."""
    def to_internal_value(self, data):
        if isinstance(data, dict) and set(data) - set(self.fields):
            raise serializers.ValidationError("Only aggregate summary fields are permitted")
        return super().to_internal_value(data)


class CategoryRow(StrictSummarySerializer):
    code = serializers.ChoiceField(choices=tuple(CATEGORY_LABELS))
    amount_toman = serializers.IntegerField(min_value=0, max_value=MAX_TOMAN)


class SummarySerializer(StrictSummarySerializer):
    period = serializers.ChoiceField(choices=("rolling_30_days",))
    income_toman = serializers.IntegerField(min_value=0, max_value=MAX_TOMAN)
    expense_toman = serializers.IntegerField(min_value=0, max_value=MAX_TOMAN)
    income_count = serializers.IntegerField(min_value=0, max_value=100000)
    expense_count = serializers.IntegerField(min_value=0, max_value=100000)
    categories = CategoryRow(many=True, allow_empty=True, max_length=9)

    def validate(self, attrs):
        if attrs["income_toman"] and not attrs["income_count"]:
            raise serializers.ValidationError("Income count mismatch")
        if attrs["expense_toman"] and not attrs["expense_count"]:
            raise serializers.ValidationError("Expense count mismatch")
        if not attrs["income_count"] and not attrs["expense_count"]:
            raise serializers.ValidationError("No transactions")
        if len({row["code"] for row in attrs["categories"]}) != len(attrs["categories"]):
            raise serializers.ValidationError("Duplicate categories")
        if sum(row["amount_toman"] for row in attrs["categories"]) > attrs["expense_toman"]:
            raise serializers.ValidationError("Category totals exceed total expense")
        if not attrs["expense_toman"] and attrs["categories"]:
            raise serializers.ValidationError("No expense for categories")
        return attrs


def summary_context(summary):
    """Convert checked numeric data to the same schema as server-generated context."""
    income = summary["income_toman"] * 10
    expense = summary["expense_toman"] * 10
    if not summary["income_count"] and not summary["expense_count"]:
        raise AiError("insufficient_data")
    rows = sorted(summary["categories"], key=lambda x: (-x["amount_toman"], x["code"]))[:5]
    return {
        "period": "rolling_30_days",
        "currency": "IRR",
        "income_rial": income,
        "expense_rial": expense,
        "net_rial": income - expense,
        "income_count": summary["income_count"],
        "expense_count": summary["expense_count"],
        "categories": [
            {
                "name": CATEGORY_LABELS[row["code"]],
                "share_percent": (100 * row["amount_toman"]) // expense,
            }
            for row in rows
        ] if expense else [],
    }
