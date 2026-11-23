"""Integration tests for privacy, authorization, quota and mobile AI prompts."""
from unittest.mock import patch

from django.contrib.auth import get_user_model
from django.test import override_settings
from rest_framework.test import APITestCase

from .models import AiAnalysisRun


class MobileAiTests(APITestCase):
    def setUp(self):
        self.user = get_user_model().objects.create_user(
            username="jibo_test", password="not-a-real-credential",
        )
        self.url = "/api/v1/ai/mobile-summary/"
        self.payload = {
            "feature": "spending_review",
            "consent": True,
            "summary": {
                "period": "rolling_30_days",
                "income_toman": 200000,
                "expense_toman": 130000,
                "income_count": 2,
                "expense_count": 4,
                "categories": [
                    {"code": "food", "amount_toman": 80000},
                    {"code": "transport", "amount_toman": 30000},
                ],
            },
        }

    def test_requires_user_authentication(self):
        self.assertIn(self.client.post(self.url, self.payload, format="json").status_code, (401, 403))
        self.assertIn(self.client.get("/api/v1/ai/status/").status_code, (401, 403))

    def test_requires_explicit_consent_and_valid_aggregates(self):
        self.client.force_authenticate(user=self.user)
        with patch("finance.ai_assistant.complete") as complete:
            bad = dict(self.payload, consent=False)
            self.assertEqual(self.client.post(self.url, bad, format="json").status_code, 400)
            bad = dict(self.payload, summary=dict(self.payload["summary"], expense_toman=100))
            self.assertEqual(self.client.post(self.url, bad, format="json").status_code, 400)
            bad = dict(self.payload, summary=dict(self.payload["summary"], categories=[
                {"code": "private_account_name", "amount_toman": 500},
            ]))
            self.assertEqual(self.client.post(self.url, bad, format="json").status_code, 400)
            bad = dict(self.payload, summary=dict(self.payload["summary"], income_toman=2**65))
            self.assertEqual(self.client.post(self.url, bad, format="json").status_code, 400)
            complete.assert_not_called()

    @override_settings(
        ARVAN_AI_ENABLED=True,
        ARVAN_AI_BASE_URL="https://sample.example/v1",
        ARVAN_AI_API_KEY="fake-test-credential",
        ARVAN_AI_MODEL="Gemini-2.5-Flash-lite",
        ARVAN_AI_USER_DAILY_LIMIT=3,
        ARVAN_AI_GLOBAL_DAILY_LIMIT=10,
    )
    @patch("finance.ai_assistant.complete")
    def test_model_receives_only_whitelisted_aggregates_and_same_request_is_cached(self, complete):
        complete.return_value = (
            {"summary": "این ماه هزینه خوراک بیشترین سهم را داشته است.",
             "tips": ["برای خوراک یک سقف هفتگی کوچک تعیین کن."]},
            88, 57,
        )
        self.client.force_authenticate(user=self.user)
        response = self.client.post(self.url, self.payload, format="json")
        self.assertEqual(response.status_code, 200, response.data)
        self.assertTrue(response.data["advisory_only"])
        self.assertEqual(response.data["source"], "user_approved_mobile_summary")
        self.assertEqual(AiAnalysisRun.objects.count(), 1)
        prompt_data = complete.call_args[0][1]
        self.assertEqual(prompt_data["data"]["expense_rial"], 1_300_000)
        self.assertEqual(prompt_data["data"]["income_rial"], 2_000_000)
        self.assertEqual(prompt_data["data"]["categories"][0]["name"], "خوراک")
        self.assertNotIn("note", str(prompt_data))
        self.assertNotIn("username", str(prompt_data))
        self.assertNotIn("account", str(prompt_data))
        retry = self.client.post(self.url, self.payload, format="json")
        self.assertEqual(retry.status_code, 200, retry.data)
        self.assertTrue(retry.data["cached"])
        self.assertEqual(complete.call_count, 1)

    @override_settings(
        ARVAN_AI_ENABLED=True,
        ARVAN_AI_BASE_URL="https://sample.example/v1",
        ARVAN_AI_API_KEY="fake-test-credential",
        ARVAN_AI_MODEL="Gemini-2.5-Flash-lite",
        ARVAN_AI_USER_DAILY_LIMIT=1,
        ARVAN_AI_GLOBAL_DAILY_LIMIT=10,
    )
    @patch("finance.ai_assistant.complete")
    def test_ai_never_mutates_ledger_and_respects_quota(self, complete):
        complete.return_value = (
            {"summary": "در این دوره گردش مالی ثبت شده است.",
             "tips": ["ثبت خریدها را ادامه بده تا گزارش دقیق‌تر شود."]},
            20, 30,
        )
        self.client.force_authenticate(user=self.user)
        first = self.client.post(self.url, self.payload, format="json")
        self.assertEqual(first.status_code, 200, first.data)
        second = dict(self.payload, feature="budget_guidance")
        result = self.client.post(self.url, second, format="json")
        self.assertEqual(result.status_code, 429)
        self.assertEqual(result.data["code"], "quota")
        from .models import LedgerTransaction
        self.assertEqual(LedgerTransaction.objects.count(), 0)

    def test_ai_disabled_without_server_secrets(self):
        self.client.force_authenticate(user=self.user)
        with override_settings(ARVAN_AI_ENABLED=False):
            result = self.client.post(self.url, self.payload, format="json")
        self.assertEqual(result.status_code, 503)
        self.assertEqual(result.data["code"], "disabled")

    def test_alpha_jwt_login_returns_access_but_no_arvan_key(self):
        response = self.client.post("/api/v1/auth/token/", {
            "username": "jibo_test", "password": "not-a-real-credential",
        }, format="json")
        self.assertEqual(response.status_code, 200, response.data)
        self.assertTrue(response.data.get("access"))
        self.assertNotIn("apikey", str(response.data).lower())
        self.client.credentials(HTTP_AUTHORIZATION="Bearer " + response.data["access"])
        response = self.client.get("/api/v1/ai/status/")
        self.assertEqual(response.status_code, 200)
        self.assertTrue(response.data["requires_explicit_consent"])
