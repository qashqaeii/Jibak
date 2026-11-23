"""Security and financial invariants for the first Jibik API slice."""
from django.contrib.auth import get_user_model
from rest_framework.test import APITestCase
from .models import FinancialAccount, Household, LedgerTransaction, Membership


class JibikFinanceApiTests(APITestCase):
    def setUp(self):
        users = get_user_model()
        self.alice = users.objects.create_user(username="alice", password="alice-secret")
        self.bob = users.objects.create_user(username="bob", password="bob-secret")
        self.carol = users.objects.create_user(username="carol", password="carol-secret")
        self.personal = FinancialAccount.objects.create(
            title="private", owner=self.alice, opening_balance_rial=100_000,
        )
        self.other = FinancialAccount.objects.create(
            title="other private", owner=self.bob, opening_balance_rial=500_000,
        )
        self.household = Household.objects.create(name="Our home", created_by=self.alice)
        Membership.objects.create(user=self.alice, household=self.household,
                                  role=Membership.Role.OWNER)
        Membership.objects.create(user=self.carol, household=self.household,
                                  role=Membership.Role.VIEWER)
        self.shared = FinancialAccount.objects.create(
            title="shared", household=self.household, opening_balance_rial=20_000,
        )

    def auth(self, user):
        self.client.force_authenticate(user=user)

    def test_unauthenticated_financial_endpoints_denied(self):
        for url in ("/api/v1/accounts/", "/api/v1/households/", "/api/v1/transactions/"):
            self.assertIn(self.client.get(url).status_code, (401, 403))
        self.assertEqual(self.client.get("/api/v1/health/").status_code, 200)

    def test_private_accounts_cannot_be_listed_by_other_user(self):
        self.auth(self.bob)
        response = self.client.get("/api/v1/accounts/")
        self.assertEqual(response.status_code, 200)
        ids = [obj["id"] for obj in response.json()]
        self.assertIn(str(self.other.pk), ids)
        self.assertNotIn(str(self.personal.pk), ids)
        self.assertNotIn(str(self.shared.pk), ids)

    def test_household_viewer_can_read_but_cannot_spend(self):
        self.auth(self.carol)
        visible = self.client.get("/api/v1/accounts/").json()
        self.assertIn(str(self.shared.pk), [item["id"] for item in visible])
        r = self.client.post("/api/v1/transactions/", {
            "idempotency_key": "viewer-write",
            "kind": "expense", "amount_rial": 2000,
            "from_account": str(self.shared.pk),
        }, format="json")
        self.assertEqual(r.status_code, 403)
        self.assertEqual(LedgerTransaction.objects.count(), 0)

    def test_invalid_outside_account_is_forbidden(self):
        self.auth(self.alice)
        r = self.client.post("/api/v1/transactions/", {
            "idempotency_key": "outsider-1", "kind": "expense",
            "amount_rial": 8000, "from_account": str(self.other.pk),
        }, format="json")
        self.assertEqual(r.status_code, 403)

    def test_transfer_does_not_become_income_or_expense(self):
        self.auth(self.alice)
        second = FinancialAccount.objects.create(
            title="savings", owner=self.alice, opening_balance_rial=30_000,
        )
        r = self.client.post("/api/v1/transactions/", {
            "idempotency_key": "transfer-1", "kind": "transfer",
            "amount_rial": 40_000, "from_account": str(self.personal.pk),
            "to_account": str(second.pk),
        }, format="json")
        self.assertEqual(r.status_code, 201, r.data)
        balances = {
            item["id"]: item["balance_rial"]
            for item in self.client.get("/api/v1/accounts/").json()
        }
        self.assertEqual(balances[str(self.personal.pk)], 60_000)
        self.assertEqual(balances[str(second.pk)], 70_000)
        self.assertEqual(balances[str(self.personal.pk)] + balances[str(second.pk)], 130_000)

    def test_idempotent_retry_returns_existing_record(self):
        self.auth(self.alice)
        payload = {
            "idempotency_key": "pay-once", "kind": "expense",
            "amount_rial": 5000, "from_account": str(self.personal.pk),
            "category": "groceries",
        }
        self.assertEqual(self.client.post("/api/v1/transactions/", payload,
                                          format="json").status_code, 201)
        retry = self.client.post("/api/v1/transactions/", payload, format="json")
        self.assertEqual(retry.status_code, 200, retry.data)
        self.assertEqual(LedgerTransaction.objects.count(), 1)
        payload["amount_rial"] = 6000
        reused = self.client.post("/api/v1/transactions/", payload, format="json")
        self.assertEqual(reused.status_code, 400)
        self.assertEqual(LedgerTransaction.objects.count(), 1)

    def test_private_to_household_transfer_rejected_until_privacy_policy(self):
        self.auth(self.alice)
        r = self.client.post("/api/v1/transactions/", {
            "idempotency_key": "cross-scope", "kind": "transfer",
            "amount_rial": 1000,
            "from_account": str(self.personal.pk),
            "to_account": str(self.shared.pk),
        }, format="json")
        self.assertEqual(r.status_code, 400)
        self.assertEqual(LedgerTransaction.objects.count(), 0)

    def test_household_creation_assigns_owner_only(self):
        self.auth(self.bob)
        r = self.client.post("/api/v1/households/", {"name": "Bob family"}, format="json")
        self.assertEqual(r.status_code, 201, r.data)
        self.assertTrue(Membership.objects.filter(
            household_id=r.data["id"], user=self.bob, role="owner",
        ).exists())
        self.auth(self.alice)
        ids = [item["id"] for item in self.client.get("/api/v1/households/").json()]
        self.assertNotIn(r.data["id"], ids)
