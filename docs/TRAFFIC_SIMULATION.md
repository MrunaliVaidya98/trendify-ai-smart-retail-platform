# Traffic simulation

The opt-in Python service in `traffic-generator/` authenticates as a scoped demo manager and sends events through Spring Boot. Spring validates JWT scope plus `X-Simulator-Key`, then persists to PostgreSQL. The generator never writes to a database directly.

Weighted patterns are `normal`, `peak`, `flash_sale`, `holiday`, `low_conversion`, and `high_abandonment`. `SIMULATOR_SEED` makes choices and IDs reproducible; timestamps remain observed UTC time. `EVENTS_PER_SECOND` is restricted to 0.1–100.

For `PURCHASE_COMPLETED`, the generator first calls the normal `/api/orders` workflow. Inventory or concurrency rejection produces `PURCHASE_FAILED`. Re-delivering an event ID returns `created=false` without another row.

```bash
docker compose --profile simulator up -d --build traffic-generator
docker compose --profile simulator logs -f traffic-generator
docker compose --profile simulator stop traffic-generator
```

The 2026-10-17 runtime check persisted 58 events across 58 sessions before the simulator was stopped. Persistent start/pause/resume state and UI-controlled reset are not implemented.
