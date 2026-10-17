# Trendify feature audit

Audit date: 2026-10-17. Status reflects executable code, not roadmap intent.

| Feature | Route | Expected behavior | Existing API | Gap before this phase | Status |
|---|---|---|---|---|---|
| Dashboard | `/` | Filtered KPIs, inventory watchlist, order shortcut | Dashboard, product, and order APIs | KPIs are synthetic rather than persisted sales | PARTIAL |
| Products | `/products` | Search/filter/page and create, edit, delete | Product CRUD | Sidebar was decorative | IMPLEMENTED |
| Inventory | `/inventory` | View stock, low-stock state, record movements | Products and inventory movement APIs | Movement history has no read API | PARTIAL |
| Orders | `/orders` | Create and inspect orders | Order create/get APIs | No list, cancellation, or status API | PARTIAL |
| Customers | `/customers` | Profiles, search, purchase history | None | Domain and persistence absent | NOT_IMPLEMENTED |
| Sales analytics | `/traffic` | Persisted traffic/funnel metrics | PostgreSQL traffic metrics | Revenue/top-product time series still absent | PARTIAL |
| Demand forecasting | `/forecasts` | Request 1–30 day SKU forecasts | FastAPI `/forecast` | No UI or local reverse proxy | IMPLEMENTED_LOCAL |
| Pricing | `/pricing` | Generate advisory price and decide | Recommendation APIs | No recommendation list API | PARTIAL |
| AI insights | `/insights` | Grounded, cited business analysis | None | No LLM/RAG implementation | NOT_IMPLEMENTED |
| Notifications | `/notifications` | Persisted alerts and acknowledgement | None | No notification domain | NOT_IMPLEMENTED |
| Settings | `/settings` | Persisted preferences | None | No preference API | NOT_IMPLEMENTED |
| Administration | `/admin` | Users, roles, stores, simulator | Demo auth only | No administration API | NOT_IMPLEMENTED |
| Live traffic simulator | Compose `simulator` profile | Continuous weighted traffic through real APIs | Traffic ingestion, product, order APIs | No persistent UI control plane | PARTIAL |

## Data audit

- Product, stock, orders, movements, and pricing decisions currently use DynamoDB.
- Dashboard KPI history is generated deterministically in `AnalyticsController`; it is not observed sales data.
- ML training data is deterministic synthetic CSV data.
- PostgreSQL now owns idempotent traffic events and bounded funnel aggregations. Customers, carts, payments, notifications, simulator state, SSE/WebSockets, and relational commerce analytics are absent.

## Safe implementation order

1. Turn every currently visible sidebar item into a real React route using existing APIs.
2. Add PostgreSQL/Flyway for new customer, session, traffic-event, simulator-state, and analytics ownership without duplicating current DynamoDB authority.
3. Add authenticated, idempotent traffic ingestion and persisted aggregate queries.
4. Add the Python generator and backend-controlled simulator lifecycle.
5. Add SSE snapshots/deltas and live React visualizations.
6. Migrate transactional ownership only through a documented cutover; do not dual-write inventory or orders without reconciliation.
