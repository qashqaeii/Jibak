"""Cursor-based read-only sync; household scope is checked on every request."""
import uuid
from datetime import datetime

from django.core import signing
from django.db.models import Q
from django.utils import timezone
from rest_framework.exceptions import ValidationError
from rest_framework.response import Response
from rest_framework.views import APIView

from .models import FinancialAccount, LedgerTransaction
from .views import TransactionSerializer

SALT = "jibik-finance-sync-v1"
PAGE_SIZE = 50


class SyncPullView(APIView):
    """A stable incremental feed. No untrusted client-controlled scopes."""

    def get(self, request):
        token = request.query_params.get("cursor")
        marker = None
        if token:
            try:
                data = signing.loads(token, salt=SALT, max_age=90 * 24 * 60 * 60)
                if not isinstance(data, dict) or data.get("user_id") != str(request.user.pk):
                    raise ValidationError({"cursor": "Cursor does not belong to this user"})
                timestamp = datetime.fromisoformat(data["at"])
                marker_id = uuid.UUID(data["id"])
                if not timezone.is_aware(timestamp):
                    raise ValueError("Cursor must include timezone")
                marker = (timestamp, marker_id)
            except (signing.BadSignature, ValueError, KeyError, TypeError) as exc:
                raise ValidationError({"cursor": "Invalid or expired cursor"}) from exc

        accessible = FinancialAccount.objects.filter(
            Q(owner=request.user) |
            Q(household__memberships__user=request.user)
        ).distinct()
        queryset = LedgerTransaction.objects.filter(
            Q(from_account__in=accessible) | Q(to_account__in=accessible)
        ).distinct()
        if marker is not None:
            queryset = queryset.filter(
                Q(created_at__gt=marker[0]) |
                Q(created_at=marker[0], id__gt=marker[1])
            )
        records = list(queryset.order_by("created_at", "id")[:PAGE_SIZE + 1])
        has_more = len(records) > PAGE_SIZE
        page = records[:PAGE_SIZE]
        next_cursor = None
        if page:
            last = page[-1]
            next_cursor = signing.dumps(
                {"user_id": str(request.user.pk), "at": last.created_at.isoformat(),
                 "id": str(last.id)},
                salt=SALT,
                compress=True,
            )
        return Response({
            "transactions": TransactionSerializer(page, many=True).data,
            "next_cursor": next_cursor,
            "has_more": has_more,
            "server_time": timezone.now().isoformat(),
        })
