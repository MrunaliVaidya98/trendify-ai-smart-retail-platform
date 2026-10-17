# API additions

The complete runtime contract is available from `/v3/api-docs` and `/swagger-ui.html`.

## `POST /api/v1/events/traffic`

Requires a bearer JWT and `X-Simulator-Key`. The body includes `eventId`, `sessionId`, `eventType`, optional `productId`, `occurredAt`, and JSON `attributes`. A valid new or duplicate event returns HTTP 201 with `{eventId, created}`. Invalid simulator credentials return 403. JWT store isolation still applies.

## `GET /api/v1/traffic/metrics`

Accepts `minutes` (1–1440) and optional `storeId`. It returns persisted event/session/funnel counts, conversion rate, measurement time, and resolved store. React `/traffic` refreshes it every five seconds and retains the last successful snapshot during a network failure.
