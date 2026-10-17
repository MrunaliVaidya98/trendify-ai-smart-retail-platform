# ADR 002: DynamoDB single-table access patterns

Status: Accepted

The current `trendify` table uses `PK` and `SK`. GSIs remain a production scaling option; the verified local implementation does not claim indexes that Terraform does not create.

| Access pattern | Key condition |
|---|---|
| Product by id | `PK=PRODUCT#id, SK=META` |
| Store/category product page | Filtered scan over `entityType`, `storeId`, and optional `category`; opaque full-key cursor |
| Store-scoped SKU uniqueness | `PK=STORE#store#SKU#sku, SK=LOCK`, conditional transaction |
| Inventory history | `PK=PRODUCT#id, begins_with(SK, MOVEMENT#)` |
| Order detail | `PK=ORDER#id, SK=META`, with embedded immutable line snapshot |
| Forecast by product/horizon | `PK=PRODUCT#id, begins_with(SK, FORECAST#)` |
| Price recommendation | `PK=PRICE_REC#id, SK=META` |
| Price decision audit | `PK=PRICE_REC#id, begins_with(SK, AUDIT#)` |
| Forecast run idempotency | `PK=FORECAST_RUN#eventId, SK=META` |

List APIs expose opaque DynamoDB continuation tokens containing the full evaluated key. The implementation continues filtered scans until the requested number of matching products is collected or the table is exhausted. Direct records use key reads/queries. Stock changes use an update condition (`stock + delta >= 0`, expected version, and store match) and increment `version` atomically. Orders transact every stock condition with the order write.
