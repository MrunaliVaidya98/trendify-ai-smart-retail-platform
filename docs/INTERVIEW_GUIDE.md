# Trendify interview guide

Thirty code-grounded questions and model answers for a technical interview or project deep dive.

## Java and Spring Boot

### 1. Why is the backend a modular monolith instead of several microservices?

The strongest invariants—SKU uniqueness, stock balance, order creation, and pricing decisions—share a system of record. A Spring Boot modular monolith keeps deployment and authorization simple and lets `OrderStore` express every inventory decrement plus the order put as one DynamoDB transaction. Splitting early would add service discovery, distributed tracing, versioned contracts, and compensation logic without evidence that modules need independent scaling or ownership. The ML runtime is separate because Python dependencies and compute behavior create a real boundary.

### 2. Where does validation occur, and why use multiple layers?

HTTP shape validation lives on request records in controllers through Jakarta Bean Validation; examples include nonblank product fields, positive quantities, and bounded dashboard days. Services such as `RecommendationService` defend their own domain assumptions because callers may not always be HTTP controllers. DynamoDB conditions are the final integrity layer for facts that can change concurrently—version, store, available stock, pending decision, or uniqueness. UI validation improves experience but is never treated as authoritative.

### 3. How does exception handling avoid leaking sensitive internals?

`ApiExceptionHandler` maps known input, authorization, not-found, and persistence conflicts to intentional status codes and safe response text. Spring is configured with `server.error.include-message=never`, so an unhandled exception does not serialize stack details. Authentication uses the same “Invalid credentials” path for an unknown user and a wrong password. Tests assert that raw passwords are absent from both success and failure responses.

### 4. Why use Java records for API models?

Records make the DTOs immutable, concise, and value-oriented. An order line snapshot or product response should not be partially mutated after construction. Their canonical constructors integrate cleanly with Jackson and Bean Validation. For a larger codebase, transport records would likely be separated from domain types to avoid accidental API coupling; the current repository keeps them near controllers to stay compact.

### 5. How does the application initialize DynamoDB locally?

`ProductStore.initialize()` runs after construction, describes the configured table, and creates it with `PK`/`SK` if DynamoDB Local reports it missing; deterministic products are then seeded. `DynamoConfig` uses the configured endpoint override locally and the default AWS credential chain otherwise. This accelerates local startup, but production schema creation belongs to Terraform—the runtime role should not normally have `CreateTable`.

## DynamoDB and concurrency

### 6. How is duplicate SKU enforcement implemented without a SQL unique constraint?

`ProductStore.create` builds a lock item keyed as `STORE#<store>#SKU#<normalized-sku> / LOCK`. It transacts that conditional put (`attribute_not_exists(PK)`) with the product metadata put. Only one concurrent writer can own that store/SKU key; if either write fails, neither is committed. A SKU rename transacts the new lock, product update, and old-lock deletion. Because the store is part of the key, the same SKU is valid in another store.

### 7. How does optimistic concurrency protect inventory?

Every product has a numeric `version`. `adjustStock` requires the caller's expected version and atomically increments it with the balance change. The condition also requires the correct store and enough stock for an outbound delta. Two writers using version 1 cannot both succeed: the first changes it to 2 and the second receives a conditional failure. This prevents lost updates and negative inventory without a process-local lock.

### 8. Why is order creation atomic?

`OrderStore.create` first consistent-reads each product in the resolved store and snapshots its current name, SKU, and price. It then sends one `TransactWriteItems` request containing a conditional decrement for each distinct line plus a conditional order put. If one product is stale, belongs to another store, or lacks stock, DynamoDB cancels the entire transaction. There is no confirmed order with partial stock movement and no partially fulfilled rejected order.

### 9. What happens when two orders race for the same final unit?

Both may read the same stock/version, but both build a condition requiring that version and `stockLevel >= quantity`. DynamoDB serializes evaluation of the conditional transaction: one commits and increments the version; the other transaction is cancelled. `ProductStoreIT.orderAtomicallyDecrementsStockAndRejectsStaleConcurrentOrder` verifies the winner/loser behavior and unchanged inventory after stale replay.

### 10. Why reject duplicate products inside one order?

`OrderStore` compares line count with the count of distinct product IDs before building the transaction. DynamoDB transactions cannot include multiple operations targeting the same item, and allowing duplicate lines would also complicate version and total calculations. Requiring the client to aggregate quantity per product makes the invariant explicit and the rejection deterministic.

### 11. What subtle pagination defect did testing reveal?

DynamoDB applies a scan `Limit` before its `FilterExpression`. Once SKU-lock and other entity records shared the table, a limited scan could return fewer matching products even though more existed. `ProductStore.list` now continues from the complete `LastEvaluatedKey` until it accumulates the requested matches or reaches the end. The opaque token includes both `PK` and `SK`; using only the partition key would not uniquely identify the cursor.

### 12. Would you keep the filtered scan in production?

Not at high volume. It is honest and adequate for a bounded portfolio fixture, but read cost grows with unrelated table items. I would introduce an access pattern such as `GSI1PK=STORE#<id>` and `GSI1SK=CATEGORY#<category>#PRODUCT#<id>`, document sparsity, backfill old items, compare results during migration, then switch reads. The API continuation token can remain opaque so its contract does not expose the index design.

## JWT and security

### 13. What claims are in a Trendify JWT and how are they enforced?

`JwtService` issues subject, role, `storeId`, issued-at, and expiration claims and signs them with an HMAC key of at least 32 characters. `JwtFilter` validates signature and expiration and builds the Spring Security authentication. `SecurityConfig` applies endpoint/HTTP-method role rules. Controllers then pass the requested store to `StoreAccess`, which allows an admin `*` scope or requires exact equality for an assigned store.

### 14. Why separate role authorization from store authorization?

A role answers “what operation may this person perform?” while store scope answers “on whose data?” A manager may be allowed to create a product but only for `SEA-01`. Combining both into ad hoc controller checks is easy to miss. Spring Security handles capability, `StoreAccess` resolves tenant scope, and repositories include `storeId` in conditions for defense in depth.

### 15. What is intentionally not production-grade about identity?

Users are bootstrapped in memory with a shared documented demo password, there is no account lifecycle or refresh-token revocation, and the SPA stores its bearer token in `localStorage`. That is appropriate only for an isolated demo. A production design should use Cognito or another managed provider, unique credentials/MFA, rotation, short sessions, revocation, and preferably an HttpOnly secure-cookie/BFF pattern to reduce script-accessible token exposure.

### 16. How is cross-store access tested?

MockMvc tests log in as a scoped manager and request another store, expecting denial. Testcontainers integration tests attempt product reads across store boundaries. The same store resolution convention is reused for products, inventory, orders, recommendations, and analytics. Admin tests explicitly select stores, so “admin can access everything” is exercised rather than assumed.

## React

### 17. How does the frontend manage state?

The UI uses React function components and hooks for local state: dashboard data, products, loading, error, and order feedback. A typed fetch wrapper owns bearer-token attachment and protected-route `401` handling. The scope is small enough that a global store would add ceremony; if product editing and pricing workflows became multi-route, an explicit feature store and shared query caching could become worthwhile.

### 18. How are loading, error, empty, and success states represented?

`DashboardComponent.load` sets loading before requesting dashboard data, then requests filtered products. The template selects a skeleton, an actionable error card, or KPI/product content. An empty product response renders the table shell without fabricated rows. Unit tests control each HTTP response with `HttpTestingController`, including dashboard failure and product failure, so every state is deterministic.

### 19. What does the HTTP interceptor do?

`authInterceptor` reads the local JWT and clones outgoing requests with an `Authorization: Bearer` header. If a protected request returns `401`, it removes token and store state and routes to `/login`; it avoids recursively handling the login request. Unit and Playwright tests verify header attachment and expired-session navigation.

### 20. How does the UI participate in optimistic concurrency?

Each product response includes `version`. “Sell one” posts product ID, quantity, and that displayed `expectedVersion`. A successful order triggers a reload, replacing stale version/stock. A `400` displays “Inventory changed; refresh and retry.” The UI participates in the protocol but DynamoDB remains authoritative, so a crafted request cannot bypass the condition.

## ML forecasting

### 21. How does the pipeline prevent target leakage?

Rows are sorted by SKU and date. Lag values are grouped by SKU and shifted; rolling statistics are calculated from shifted demand, so the current target is excluded. Training/test division is chronological rather than randomly shuffled, with the latest 20% held out. Tests verify both temporal exclusion and isolation between SKUs, preventing one SKU's history from leaking into another's features.

### 22. Why use HistGradientBoosting and a seasonal-naive baseline?

HistGradientBoosting handles nonlinear relationships in calendar, lag, rolling, promotion, and price features without a large dependency footprint. The seven-day naive baseline is easy to explain and aligned with weekly retail seasonality. A learned model is only meaningful relative to a credible simple benchmark; the baseline also supplies a deterministic degradation path when loading or primary inference fails.

### 23. How are metrics computed, and why can WAPE be null?

The report includes MAE for absolute error magnitude and RMSE to penalize larger misses. WAPE divides total absolute error by total actual demand. If total actual demand is zero, division would be undefined, so the implementation returns `null` rather than infinity or a misleading zero. Tests use known arrays to verify exact metric behavior.

### 24. How does inference fallback differ from cold start?

An unknown SKU has no trustworthy history and receives `404`; fallback must not invent demand. For a known SKU, model load, training, or prediction failure triggers seasonal-naive inference from its observed weekly history and labels the fallback in metadata. If that path also fails, FastAPI returns a controlled `503`. This preserves semantic distinction between absent data and temporary model failure.

### 25. What would you add before production forecasting?

Use real store/SKU histories; rolling-origin backtests; holiday, stockout, and known-event features; calibrated intervals; segment-level error analysis; drift and feature-quality monitors; artifact lineage; champion/challenger approval; and rollback. Recursive multi-day forecasts compound uncertainty, so direct/multi-output approaches should be compared. Synthetic metrics in this repository demonstrate plumbing only.

## AWS, Terraform, and CI/CD

### 26. How is the Lambda idempotent?

It derives `run_id` from the EventBridge event ID and conditionally creates `FORECAST_RUN#<id>` as `RUNNING`. A concurrent duplicate cannot acquire the same key. If the stored run is `COMPLETED`, the handler returns its persisted result without calling `/train`; a still-running duplicate raises. Completion writes encrypted S3 JSON and conditionally transitions DynamoDB from `RUNNING` to `COMPLETED`.

### 27. Why re-raise Lambda failures after recording them?

Swallowing an exception would tell Lambda/EventBridge that the invocation succeeded, disabling retries and suppressing the error metric. The handler best-effort marks the run `FAILED`, logs structured context without the full sensitive payload, and re-raises. Terraform configures two EventBridge retries within one hour and a CloudWatch alarm on Lambda `Errors`.

### 28. What does Terraform provision—and not provision?

It creates an encrypted/PITR DynamoDB table, an encrypted versioned S3 bucket, a Python 3.12 Lambda, its least-privilege role/policy, a daily EventBridge rule/target, invocation permission, 30-day log group, and error alarm. It does not create frontend/API hosting, VPC networking, TLS ingress, the ML service, dashboards, or a complete production platform. `ml_batch_url` is supplied externally.

### 29. How is CI structured and why?

GitHub Actions has independent backend, Python, frontend, E2E, performance, and Terraform jobs. Separation gives fast attribution and lets static/unit work run independently of Compose workflows. Coverage and browser artifacts are uploaded, failure cleanup uses `if: always()`, and no job masks test failures. The E2E/performance jobs build the same Compose boundaries used locally.

### 30. What does the test strategy prove, and what remains unproven?

JUnit/Mockito/MockMvc prove Java rules and API security; Testcontainers with DynamoDB Local proves conditions, transactions, pagination, and concurrency; pytest covers pipeline/API/Lambda logic with mocked AWS clients; React Testing Library covers component states; Playwright proves four browser workflows; k6 checks a bounded smoke threshold; Terraform checks syntax/provider consistency. The executed result is 88/88 tests and 50/51 documented requirements. `CLOUD-001` remains blocked: no evidence yet proves deployed EventBridge delivery, real IAM enforcement, AWS DynamoDB/S3 behavior, alarm delivery, or teardown.
