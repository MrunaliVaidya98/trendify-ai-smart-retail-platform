# Five-minute Trendify demo

## Before the call

```bash
cp .env.example .env                    # first run only; set a new JWT_SECRET
docker compose up -d --build
docker compose ps
curl -fsS http://localhost:8080/actuator/health
curl -fsS http://localhost:8001/health
```

Open `http://localhost:4200` and `http://localhost:8080/swagger-ui.html` in separate tabs. Use `admin@trendify.dev` / `Trendify123!`. Keep [TEST_REPORT.md](TEST_REPORT.md) open. Do not run `docker compose down -v` unless local data can be discarded.

## 0:00–0:35 — Frame the problem

Show the sign-in page.

> “Trendify is a locally runnable multi-store retail operations platform. React provides the operator experience, Spring Boot is the system of record, DynamoDB protects inventory under concurrency, and a separate FastAPI service owns leakage-safe forecasting. The design also includes a Terraform-defined scheduled Lambda workflow, but I explicitly treat live AWS deployment as pending.”

Sign in. Point out that the login returns an expiring signed JWT with role and store scope.

## 0:35–1:25 — Dashboard and store isolation

Show the KPI cards, revenue trend, action center, and product watchlist.

> “Dashboard and product data come from authenticated APIs. The store and category controls are sent to both endpoints. Store scope is not just a UI convention: the JWT identifies the assigned store, `StoreAccess` rejects cross-store selection, and repository conditions include the resolved store.”

Select **Beverages**, click **Apply filters**, and show that the watchlist is narrowed. Clear the category afterward.

## 1:25–2:10 — Transactional order workflow

Read the displayed stock for one product, then click **Sell one** once.

> “The UI sends the product's displayed version. The backend creates one DynamoDB transaction containing a conditional stock decrement and the immutable order snapshot. The condition requires the expected version, matching store, and sufficient stock. If any line fails, the whole order is cancelled—there is no partial inventory change.”

Show the confirmation message and decremented/refreshing stock. Do not repeatedly click; this demo mutates local fixture state.

## 2:10–2:55 — DynamoDB design and concurrency

Switch briefly to [ARCHITECTURE.md](ARCHITECTURE.md), at the data-model table and order sequence.

> “A store-scoped SKU lock uses a conditional put in the same transaction as product metadata, giving DynamoDB a unique-SKU invariant. Inventory changes use optimistic versions rather than process locks, so the rule still holds across multiple API instances. Integration tests race stale writers against DynamoDB Local.”

Mention the honest scaling tradeoff: the current catalog uses a correctly paginated filtered scan; a production dataset should add a store/category GSI.

## 2:55–3:40 — ML methodology and fallback

Show FastAPI docs or the README ML section.

```bash
curl -fsS -X POST http://localhost:8001/forecast \
  -H 'Content-Type: application/json' \
  -d '{"sku":"SKU-COFFEE-01","horizon":7}' | jq
```

> “Features are grouped by SKU, sorted by date, and shifted before lags and rolling windows are computed. The newest 20 percent is the test set—there is no random temporal leakage. HistGradientBoosting is compared with a seven-day seasonal-naive baseline. If primary inference fails for a known SKU, the baseline is returned with fallback metadata; unknown SKUs are still 404, and dual failure is a controlled 503.”

State that synthetic metrics validate the pipeline, not real retail accuracy.

## 3:40–4:20 — AWS and operational design

Show the scheduled forecasting section of [ARCHITECTURE.md](ARCHITECTURE.md).

> “EventBridge supplies a run ID. Lambda claims it with a conditional DynamoDB put, calls training once, persists the result to encrypted S3 and DynamoDB, and replays completed duplicates. Failures are recorded and re-raised so retries and the CloudWatch error alarm still work. Terraform defines this slice with least-privilege IAM, but I have not claimed a live deployment.”

## 4:20–4:50 — Evidence

Show [TEST_REPORT.md](TEST_REPORT.md).

> “The verified baseline is 88 tests passed, zero failed, zero skipped; 50 of 51 documented requirements pass. Java/Python/React line coverage is 90.36, 91, and 100 percent. Four Playwright journeys pass. The k6 smoke run completed 452 checks with dashboard p95 of 52.78 milliseconds against a 500-millisecond threshold. Terraform formatting and validation pass.”

Point directly to `CLOUD-001` as blocked.

## 4:50–5:00 — Close

> “The project’s core theme is making retail automation safe: transactional stock, tenant isolation, leakage-aware ML, explicit fallback behavior, and human approval for price changes. The next production increments are order-backed KPI aggregation, a catalog GSI, managed identity, and a real AWS sandbox verification.”

## If the live app fails

Do not imply it is running. Show the genuine screenshots in the README, run `docker compose ps` and health checks, and discuss the last executed evidence in `TEST_REPORT.md`. State the observed failure and move to architecture rather than spending the five-minute demo rebuilding containers.
