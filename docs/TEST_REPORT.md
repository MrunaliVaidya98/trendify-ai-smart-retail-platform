# Trendify test verification report

Verified on 2026-10-17 against the implemented repository and a freshly built, isolated Docker Compose stack. PASS is used only where the referenced test or validation command completed successfully.

## 2026-10-17 database/traffic phase

The latest phase executed **94 tests successfully, 0 test failures, and 8 environment-skipped Testcontainers tests**:

| Suite | Passed | Failed | Skipped |
|---|---:|---:|---:|
| Spring/JUnit/MockMvc (including Flyway/traffic) | 46 | 0 | 0 |
| DynamoDB Testcontainers in latest Maven container | 0 | 0 | 8 |
| FastAPI/ML/Lambda pytest in Python 3.12 container | 21 | 0 | 0 |
| Traffic generator pytest | 4 | 0 | 0 |
| React/Vitest | 19 | 0 | 0 |
| Playwright/Chromium | 4 | 0 | 0 |
| **Latest-phase total** | **94** | **0** | **8** |

The 8 integration tests were skipped because that particular Maven container was not given a Docker socket. They passed in the previously recorded isolated Testcontainers run and were not relabelled as newly executed. JaCoCo was regenerated from the latest Java unit/API execution: **90.87% lines (189/208)**. React measured **95.34% lines**, **71.14% branches**, **80.33% statements**, and **71.59% functions**.

Runtime integration evidence: all five core Compose services became healthy; Flyway V1 applied to PostgreSQL 17; authenticated ingestion returned `created=true`, duplicate delivery returned `created=false`; and the opt-in simulator persisted 58 events before it was stopped. A fresh 10-second k6 smoke run completed 50 iterations with **152/152 checks**, 0% failures, dashboard p95 **66.70 ms**, and HTTP p95 **67.66 ms** against 500 ms thresholds. Host Python lacked FastAPI, so the authoritative 21-test run used the project's Python 3.12 container. Playwright initially lacked a host browser and was rerun successfully in the matching official 1.64.1 image.

The updated test plan now contains **55 PASS, 0 FAIL, 0 SKIPPED, and 2 BLOCKED** rows. The blocked rows are durable simulator controls and live AWS verification. The broad master-prompt acceptance criteria are **not all complete**: customer, notifications, settings, administration, PostgreSQL commerce tables, SSE/WebSockets, and database-derived commerce KPIs remain partial or unimplemented.

### CI reliability correction

After a GitHub-hosted run observed transient Nginx `502` responses, both E2E and performance jobs gained an authenticated readiness probe through the same Nginx route used by the tests. Compose now has an explicit 180-second wait timeout, and readiness failure emits service status and bounded logs. The Playwright workflow passed 12/12 executions in a three-repeat, one-worker container run after this change. The Trivy action reference was corrected from the unresolved `0.33.1` reference to the published `v0.36.0` tag. GitHub-hosted verification of the modified workflow remains pending until the next pushed run.

## Previous full requirement baseline

| Suite | Type | Passed | Failed | Skipped | Evidence |
|---|---|---:|---:|---:|---|
| Spring/JUnit/MockMvc | Unit and API integration | 43 | 0 | 0 | Java 21 Maven container, `mvn -B verify` |
| DynamoDB Local/Testcontainers | Integration and concurrency | 8 | 0 | 0 | `ProductStoreIT` |
| Python/FastAPI/Lambda | Unit and API | 21 | 0 | 0 | pytest locally and in Python 3.12 image |
| React/Vitest/Testing Library | Component unit | 12 | 0 | 0 | V8 coverage enabled |
| Playwright/Chromium | E2E | 4 | 0 | 0 | Official Linux Playwright image |
| **Automated test total** | | **88** | **0** | **0** | |

Requirement verification: **50 PASS, 0 FAIL, 0 SKIPPED, 1 BLOCKED** across the 51 rows currently present in `TEST_PLAN.md`. The only blocked requirement is `CLOUD-001`, which requires a provisioned AWS sandbox. Local tests intentionally do not use real AWS credentials.

Additional verification:

- k6: 150 iterations, 151 HTTP requests, 452/452 checks passed, 0% HTTP and business failures. Dashboard p95 was 52.78 ms and HTTP p95 was 52.86 ms against the 500 ms thresholds, using 5 virtual users for 30 seconds.
- Terraform: `fmt -check -recursive`, `init -backend=false`, and `validate` all passed.
- Container build: backend, frontend, and ML images built successfully. The ML image ran 21/21 tests on Python 3.12.
- The four-service `trendifyenterprise` Compose stack became healthy, and the live API returned the documented error path and correlation ID.
- CI workflow YAML parsed successfully and contains independent backend, Python, frontend, E2E, performance, Terraform, and container-scan jobs. GitHub-hosted execution is not claimed because the workflow has not been pushed.

## Coverage

| Component | Line coverage | Branch coverage | Other |
|---|---:|---:|---|
| Java/JaCoCo | 90.36% (178/197) | 73.15% (109/149) | Instructions 92.74% (3843/4144) |
| Python/coverage.py | 91% (94/103) | Not configured | 21 tests |
| React/V8 | 100% (23/23) | 81.63% (40/49) | Statements 89.89%; functions 76% |

All three components exceed the requested 80% line-coverage target. Lower branch/function results are reported explicitly.

## Implemented blocked requirements

- Orders: `POST /api/orders` uses one DynamoDB transaction for the order and every versioned stock decrement. Insufficient stock, wrong store, or stale versions cancel the entire order.
- Duplicate SKUs: a store-scoped SKU lock item is conditionally created in the same transaction as product metadata. The same SKU remains valid in another store.
- Store isolation: JWTs carry `storeId`; store managers and analysts cannot select another store. Admins can explicitly select a store. Product, inventory, order, pricing, and dashboard operations resolve this scope.
- Dashboard filtering: store and category parameters scope deterministic KPIs and product queries; React exposes both filters.
- ML fallback: model training/loading/inference failures use seven-day seasonal-naive inference. Unknown SKUs remain 404; failure of both paths is a controlled 503.
- Pricing audit: recommendations, decisions, and append-only audit entries are persisted in DynamoDB. Decisions are conditional and cannot be overwritten.
- Lambda: EventBridge run IDs are claimed with a conditional DynamoDB write. Completed duplicate events return the persisted result without retraining. Successful results are stored in DynamoDB and encrypted S3 objects; failures are persisted and re-raised.
- Complete retail E2E: the UI creates an order, refreshes displayed stock, stale replay is rejected, and the fixture is restored.

## Defects found during implementation

The expanded integration suite found that filtered DynamoDB scans can return short pages because `Limit` applies before `FilterExpression`, especially after SKU lock records were added. Pagination now continues until it collects the requested matching records or exhausts the table, and continuation tokens preserve the complete `PK/SK` cursor.

## Remaining blocked verification

`CLOUD-001` remains BLOCKED: no real AWS account was supplied, so deployed EventBridge invocation, IAM enforcement, DynamoDB/S3 persistence, CloudWatch alarms, and teardown were not executed. Equivalent Lambda behavior is covered locally with deterministic mocked clients, and Terraform configuration is formatted and valid. This is not represented as a cloud pass.

Detailed IDs are maintained in `docs/TEST_PLAN.md`.
