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

## Important limitations

SessionAuthentication is included for authenticated developer/admin tests. **Public OTP, mobile access tokens, sync push, invitations, billing, deletion/export and live Android integration are not implemented yet.** Do not invent API keys, hardcode credentials or deploy this as a complete financial service. Production settings refuse SQLite and require explicit PostgreSQL settings and a secret key.

**Sync limitation:** The pull feed currently returns transaction changes only; account and membership changes, edited/reversed rows, deletes and cross-scope transfers require a versioned outbox and carefully designed privacy rules. It is not yet a full bidirectional offline sync system.
