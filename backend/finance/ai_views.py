"""Explicit, authenticated, no-store AI advisory endpoints."""
from rest_framework import serializers
from rest_framework.response import Response
from rest_framework.views import APIView

from .ai_assistant import FEATURES, analyze, analyze_for_context
from .ai_snapshot import StrictSummarySerializer, SummarySerializer, summary_context
from .ai_provider import AiError, available

ERRORS = {
    "disabled": ("دستیار هوشمند هنوز فعال نیست.", 503),
    "invalid_input": ("درخواست معتبر نیست.", 400),
    "forbidden_scope": ("به اطلاعات این خانواده دسترسی ندارید.", 403),
    "insufficient_data": ("برای تحلیل، در این بخش باید ابتدا تراکنش ثبت شود.", 409),
    "busy": ("درخواست دیگری در حال بررسی است؛ کمی بعد تلاش کنید.", 429),
    "quota": ("سهمیه روزانه هوش مصنوعی تکمیل شده است.", 429),
    "rate_limited": ("سرویس هوش مصنوعی شلوغ است.", 429),
    "provider_auth": ("اتصال هوش مصنوعی نیاز به تنظیم سرور دارد.", 503),
    "provider_unavailable": ("سرویس هوش مصنوعی موقتاً در دسترس نیست.", 503),
    "invalid_output": ("پاسخ هوش مصنوعی قابل استفاده نبود؛ اطلاعات شما تغییر نکرد.", 502),
}


def no_store(payload, status=200):
    response = Response(payload, status=status)
    response["Cache-Control"] = "no-store"
    return response


class AiRequestSerializer(serializers.Serializer):
    feature = serializers.ChoiceField(choices=FEATURES)
    scope = serializers.ChoiceField(choices=("personal", "household"), default="personal")
    household_id = serializers.UUIDField(required=False, allow_null=True)
    consent = serializers.BooleanField(default=False)


class AiStatusView(APIView):
    def get(self, request):
        return no_store({
            "available": available(),
            "features": list(FEATURES),
            "privacy": "Aggregate financial totals only; no notes or account names.",
            "requires_explicit_consent": True,
        })


class AiAnalysisView(APIView):
    def post(self, request):
        form = AiRequestSerializer(data=request.data)
        form.is_valid(raise_exception=True)
        data = form.validated_data
        if not data["consent"]:
            return no_store({"code": "consent_required", "message": "برای تحلیل ابری باید رضایت بدهید."}, 400)
        try:
            result, cached = analyze(
                request.user, data["feature"], data["scope"], data.get("household_id"),
            )
        except AiError as exc:
            code = str(exc)
            message, status = ERRORS.get(code, ERRORS["provider_unavailable"])
            return no_store({"code": code if code in ERRORS else "provider_unavailable", "message": message}, status)
        return no_store({
            "result": result, "cached": cached, "feature": data["feature"],
            "scope": data["scope"], "period": "rolling_30_days",
            "advisory_only": True,
        })


class AiMobileRequestSerializer(StrictSummarySerializer):
    feature = serializers.ChoiceField(choices=FEATURES)
    consent = serializers.BooleanField(required=True)
    summary = SummarySerializer()

    def validate_consent(self, value):
        if value is not True:
            raise serializers.ValidationError("Explicit consent required")
        return value


class AiMobileSummaryView(APIView):
    """Cloud assistance for offline-first Android without uploading the ledger.

    Caller must be authenticated; provider sees only enumerated anonymous totals.
    The device never receives an Arvan credential or provider endpoint.
    """
    def post(self, request):
        form = AiMobileRequestSerializer(data=request.data)
        form.is_valid(raise_exception=True)
        data = form.validated_data
        try:
            result, cached = analyze_for_context(
                request.user, data["feature"], "mobile_summary",
                summary_context(data["summary"]),
            )
        except AiError as exc:
            code = str(exc)
            message, http_status = ERRORS.get(code, ERRORS["provider_unavailable"])
            return no_store({
                "code": code if code in ERRORS else "provider_unavailable",
                "message": message,
            }, http_status)
        return no_store({
            "result": result, "cached": cached, "feature": data["feature"],
            "period": "rolling_30_days", "advisory_only": True,
            "source": "user_approved_mobile_summary",
        })
