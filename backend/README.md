# Jibik Django API — first implementation slice

This is the agreed Django REST/PostgreSQL backend architecture, **not yet a finished online product**. All stored monetary amounts are **integer rials**, whereas the current Android local database stores **integer tomans**. A future sync adapter must convert *exactly* 10 rials per toman and reject fractions. Never use floats.

## Local development

```bash
cd backend
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
export JIBIK_DEBUG=true
python manage.py migrate
python manage.py check
pytest -q
python manage.py runserver
```

## API v1 (partial)

- GET /api/v1/health/ (public)
- GET/POST /api/v1/households/ (authenticated users; create assigns owner membership)
- GET/POST /api/v1/accounts/ (user-private or their household; editor/owner required for writes)
- GET/POST /api/v1/transactions/ (immutable income, expense or transfer; per-user idempotency keys)
- GET /api/v1/sync/pull/?cursor=... (authenticated, signed user-bound cursor, 50 changes per page, 90-day cursor validity)
- Listing and permissions derive from **server-side membership checks**, not hidden UI.
- Cross-scope transfer is deliberately rejected until household privacy-safe transfer policy is built.

## Jibo AI: ArvanCloud provider and mobile summary (alpha)

Read [docs/ARVAN_AI_JIBO_FA.md](../docs/ARVAN_AI_JIBO_FA.md) before deployment.

- `GET /api/v1/ai/status/`: authenticated feature availability
- `POST /api/v1/ai/analyze/`: authorized server-ledger aggregate financial analysis
- `POST /api/v1/ai/mobile-summary/`: authenticated opt-in analysis of **anonymous** 30-day totals from Android; no full ledger upload, account name, item, or note
- `POST /api/v1/auth/token/`: rate-limited alpha username/password login yielding a 30-minute JWT. User provisioning / public OTP remains unfinished.
- Private server configuration: `ARVAN_AI_ENABLED=true`, `ARVAN_AI_BASE_URL=<HTTPS inference base from Arvan panel>`, `ARVAN_AI_API_KEY=<runtime-only secret>` and `ARVAN_AI_MODEL=Gemini-2.5-Flash-lite`.
- Android uses only a **Jibik backend** URL injected with Gradle `-PJIBIK_API_BASE_URL=https://YOUR-JIBIK-DOMAIN`. Never add the Arvan credential to Android BuildConfig or GitHub.
- Without provisioned Arvan endpoint, private server key, deployed Jibik HTTPS API and a Jibik user account, the cloud inference path remains **unavailable**. The offline, local financial observations remain functional.

## Important limitations

SessionAuthentication is included for authenticated developer/admin tests. **Public OTP, complete mobile account provisioning/session management, sync push, invitations, billing, deletion/export and deployed live AI are not implemented yet.** Do not invent API keys, hardcode credentials or deploy this as a complete financial service. Production settings refuse SQLite and require explicit PostgreSQL settings and a secret key.

**Sync limitation:** The pull feed currently returns transaction changes only; account and membership changes, edited/reversed rows, deletes and cross-scope transfers require a versioned outbox and carefully designed privacy rules. It is not yet a full bidirectional offline sync system.
