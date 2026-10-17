# Trendify traffic generator

This opt-in development service sends deterministic, weighted synthetic events through the authenticated Spring API. A completed-purchase scenario first uses the normal order endpoint; failed inventory/version checks are recorded as `PURCHASE_FAILED`.

Start it with `docker compose --profile simulator up -d traffic-generator` and stop it with `docker compose --profile simulator stop traffic-generator`. Configure `EVENTS_PER_SECOND` (0.1–100), `SIMULATOR_SEED`, and `TRAFFIC_PATTERN`. Generated data is always labelled `source=trendify-simulator`.
