# Trendify automated test plan

Status values are evidence-based: **PASS** means the referenced test executed successfully; **FAIL** means it executed and failed; **BLOCKED** means a required external environment was not available. Results below reflect the verification run completed on 2026-10-16.

## Test layers

| Layer | Isolation | Local dependency | CI job |
|---|---|---|---|
| Java unit/API | JUnit 5, Mockito, MockMvc | Maven/JDK 21 | `backend` |
| DynamoDB integration | JUnit 5, Testcontainers, DynamoDB Local | Docker | `backend` |
| Python unit/API | pytest, FastAPI TestClient | Python 3.12 | `python` |
| Lambda unit | pytest, mocked `urlopen` and environment | Python 3.12 | `python` |
| React component | Vitest/Testing Library | Node 24 | `frontend` |
| Browser E2E | Playwright, local Compose stack | Docker + Chromium | `e2e` |
| Performance smoke | k6 | local/CI Compose stack | `performance` |
| Infrastructure | Terraform fmt/init/validate | Terraform | `terraform` |

## Authentication, API, and security

| ID | Scenario | Expected result | Implementation reference | Status |
|---|---|---|---|---|
| AUTH-001 | Valid demo login | 200, bearer JWT, role and display name; password absent | `SecurityApiTest.validAdminLoginReturnsJwtWithoutPassword` | PASS |
| AUTH-002 | Invalid credentials | 401 without credential disclosure | `SecurityApiTest.invalidCredentialsReturn401WithoutLeakingDetails` | PASS |
| AUTH-003 | Missing/invalid/expired JWT | Protected API returns 401 | `SecurityApiTest.missingAndInvalidTokensReturn401`, `JwtServiceTest` | PASS |
| AUTH-004 | Role restrictions | Analyst cannot mutate products; manager cannot delete; manager can create | `SecurityApiTest.roleMatrixIsEnforced` | PASS |
| AUTH-005 | Sensitive credential handling | Raw password is absent from successful and failed responses | `SecurityApiTest.validAdminLoginReturnsJwtWithoutPassword`, `invalidCredentialsReturn401WithoutLeakingDetails` | PASS |
| SEC-001 | Correlation ID | Supplied ID is propagated; absent ID is generated | `CorrelationFilterTest` | PASS |
| SEC-002 | CORS allowlist | Configured origin accepted; unknown origin rejected | `SecurityApiTest.corsIsRestricted` | PASS |
| SEC-003 | Cross-store authorization | A user cannot access another store | `SecurityApiTest.managerCannotSelectAnotherStore`, `ProductStoreIT.productCannotBeReadAcrossStores` | PASS |
| API-001 | OpenAPI contract | Document is served and contains only implemented core paths | `SecurityApiTest.openApiContainsImplementedPaths` | PASS |
| API-002 | Structured error traceability | Error response includes safe message, request path, and correlation ID; access denial hides internal reason | `ApiExceptionHandlerTest` and live Compose request | PASS |

## Product and inventory

| ID | Scenario | Expected result | Implementation reference | Status |
|---|---|---|---|---|
| PROD-001 | Product input validation | Blank fields, non-positive price/lead time, negative stock rejected | `ProductControllerTest.invalidProductReturns400` | PASS |
| PROD-002 | Product create/read/update/delete | DynamoDB round trip preserves fields and increments version | `ProductStoreIT.productCrudRoundTrip` | PASS |
| PROD-003 | Pagination | Page token continues without repeats; malformed token rejected | `ProductStoreIT.paginationReturnsContinuationWithoutRepeats` | PASS |
| PROD-004 | Stale update | Conditional write conflict becomes client-safe error | `ProductStoreFailureTest.staleProductUpdateBecomesSafeClientError` | PASS |
| PROD-005 | Duplicate SKU | Second product with same SKU in one store is rejected; same SKU in another store is allowed | `ProductStoreIT.duplicateSkuIsRejectedWithinStoreButAllowedAcrossStores` | PASS |
| INV-001 | Stock in/out/adjustment mapping | Correct signed delta and audit item | `InventoryControllerTest` | PASS |
| INV-002 | Negative stock | Conditional stock-out is rejected and balance unchanged | `ProductStoreIT.negativeStockAndStaleVersionAreRejected` | PASS |
| INV-003 | Optimistic concurrency | One stale concurrent update is rejected | `ProductStoreIT.concurrentWritersCannotBothSucceed` | PASS |
| INV-004 | DynamoDB throttling/failure | AWS exception propagates without a false success | `ProductStoreFailureTest` | PASS |
| ORDER-001 | Order decrements inventory atomically | All lines and the order commit together; stale replay is rejected without another decrement | `ProductStoreIT.orderAtomicallyDecrementsStockAndRejectsStaleConcurrentOrder` | PASS |

## Analytics and recommendations

| ID | Scenario | Expected result | Implementation reference | Status |
|---|---|---|---|---|
| DASH-001 | KPI arithmetic | Revenue/unit totals equal trend aggregation; margin formula is consistent | `AnalyticsControllerTest.kpisMatchTrend` | PASS |
| DASH-002 | Days filter boundaries | 7 and 365 accepted; outside range rejected | `AnalyticsControllerTest.daysRangeIsEnforced` | PASS |
| DASH-003 | Store/category filters | KPI scope and product list honor authorized store/category | `AnalyticsControllerTest.storeAndCategoryFiltersChangeScopeDeterministically`, `App.test.tsx` | PASS |
| PRICE-001 | Price floor/cap | Recommendation respects margin floor and ±10% guardrail | `RecommendationServiceTest` | PASS |
| PRICE-002 | Explanations/competitor scenario | Reason discloses non-causal scenario assumption | `RecommendationServiceTest` | PASS |
| PRICE-003 | Approval workflow | Pending result can be approved/rejected and records actor/time | `RecommendationControllerTest` | PASS |
| PRICE-004 | Durable audit history | Decisions survive repository recreation and append a queryable audit entry | `ProductStoreIT.pricingDecisionAndAuditSurviveRepositoryRecreation` | PASS |
| REORDER-001 | Reorder point | Lead-time demand plus safety stock is rounded up | `RecommendationServiceTest` | PASS |
| REORDER-002 | Risk/overstock/holding cost | Status and quantity reflect stock/on-order inputs | `RecommendationServiceTest` | PASS |

## ML and Lambda

| ID | Scenario | Expected result | Implementation reference | Status |
|---|---|---|---|---|
| ML-001 | Synthetic data determinism | Same seed produces identical data | `test_pipeline.py` | PASS |
| ML-002 | Leakage prevention | Lags/rolling windows exclude current target and are SKU-isolated | `test_pipeline.py` | PASS |
| ML-003 | Metrics | MAE/RMSE exact; WAPE handles all-zero actuals | `test_pipeline.py` | PASS |
| ML-004 | Chronological split/metadata | Cutoff precedes test rows; report/model artifacts persist | `test_pipeline.py` | PASS |
| ML-005 | Forecast API | 7/30-day response, nonnegative values, model metadata | `test_api.py` | PASS |
| ML-006 | Cold-start SKU | Unknown SKU returns 404 | `test_api.py` | PASS |
| ML-007 | Missing model | Request trains a model before inference | `test_api.py` | PASS |
| ML-008 | Model fallback | Seasonal-naive output returned when primary inference fails; dual failure returns controlled 503 | `test_primary_model_failure_uses_seasonal_naive_fallback`, `test_model_and_fallback_failure_returns_controlled_503` | PASS |
| LAMBDA-001 | Scheduled invocation | Lambda POSTs `/train`, returns version and run ID | `test_lambda.py` | PASS |
| LAMBDA-002 | Upstream failure | Error is logged and re-raised for EventBridge retry | `test_lambda.py` | PASS |
| LAMBDA-003 | Idempotency/persistence | Run ID is conditionally claimed; duplicate completed event replays persisted result; output stored in DynamoDB/S3 | `test_persistent_run_claim_writes_result_to_dynamodb_and_s3`, `test_duplicate_completed_event_returns_persisted_result_without_training` | PASS |

## Frontend, E2E, performance, infrastructure

| ID | Scenario | Expected result | Implementation reference | Status |
|---|---|---|---|---|
| UI-001 | Dashboard loading | Loading state shown while requests are pending | `App.test.tsx` | PASS |
| UI-002 | Dashboard success | KPI and product rows render from API results | `App.test.tsx` | PASS |
| UI-003 | Empty products | Header/table render without fabricated rows | `App.test.tsx` | PASS |
| UI-004 | Dashboard/product error | Actionable error state renders and retry invokes load | `App.test.tsx` | PASS |
| UI-005 | Expired session | 401 clears JWT and navigates to login | `App.test.tsx` | PASS |
| E2E-001 | Login-to-dashboard | Demo login renders live KPI/product data | `e2e/retail-workflow.spec.ts` | PASS |
| E2E-002 | Inventory workflow | API-assisted stock-in changes displayed inventory and is cleaned up | `e2e/retail-workflow.spec.ts` | PASS |
| E2E-003 | Complete order workflow | Create order through UI, observe stock decrement, reject stale replay, and restore fixture stock | `retail-workflow.spec.ts` | PASS |
| PERF-001 | Auth/dashboard smoke | p95 < 500 ms, HTTP failure rate < 1% at 5 VUs for 30s | `performance/smoke.js` | PASS |
| TF-001 | Terraform formatting/validation | `fmt -check`, offline init, and validate succeed | CI `terraform` job | PASS |
| CLOUD-001 | Provisioned AWS smoke | Deployed Lambda, EventBridge, DynamoDB, and S3 resources behave in a sandbox account | Requires explicitly provisioned AWS account/credentials; excluded from local CI | BLOCKED |

## Coverage policy

JaCoCo, Python `coverage.py`, and React/V8 coverage artifacts are generated in CI. The 2026-10-17 run measured Java lines at 90.36%, Python lines at 91%, and React lines at 100%. Branch coverage is lower (Java 73.15%, React 81.63%) and is reported rather than hidden. Cloud deployment tests are excluded from local CI and require an explicitly provisioned sandbox account.
