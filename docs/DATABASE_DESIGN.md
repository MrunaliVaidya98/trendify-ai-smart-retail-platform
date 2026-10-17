# Database design

## Ownership

- **DynamoDB is authoritative** for products, stock balances, inventory movements, orders, pricing decisions, and scheduled forecast-run state.
- **PostgreSQL is authoritative** for synthetic traffic events and their aggregate queries. It does not duplicate product, order, or inventory authority.

This narrow first migration avoids a dual-write cutover. Moving commerce transactions to PostgreSQL still requires a separately tested reservation/payment/outbox design.

## Flyway V1

`V1__traffic_events.sql` creates `traffic_events` and Flyway's schema history. Event IDs are primary keys, making retry delivery idempotent. Store/time, store/type/time, and store/session indexes support bounded dashboard windows. Timestamps are timezone-aware, and a check constraint prevents unknown event types.

The migration executed successfully against PostgreSQL 17 in Docker Compose and H2 PostgreSQL mode in Spring tests on 2026-10-17.
