CREATE TABLE traffic_events (
  id VARCHAR(64) PRIMARY KEY,
  store_id VARCHAR(64) NOT NULL,
  session_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  product_id VARCHAR(64),
  occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
  attributes_json TEXT NOT NULL DEFAULT '{}',
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT ck_traffic_event_type CHECK (event_type IN ('SESSION_STARTED','PAGE_VIEW','PRODUCT_VIEW','SEARCH','ADD_TO_CART','CART_ABANDONED','CHECKOUT_STARTED','PURCHASE_COMPLETED','PURCHASE_FAILED'))
);

CREATE INDEX idx_traffic_events_store_time ON traffic_events (store_id, occurred_at DESC);
CREATE INDEX idx_traffic_events_store_type_time ON traffic_events (store_id, event_type, occurred_at DESC);
CREATE INDEX idx_traffic_events_session ON traffic_events (store_id, session_id);
