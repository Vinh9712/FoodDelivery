-- =============================================================================
-- V5: Saga Schema Enhancements
-- Mở rộng schema cho Saga Orchestrator pattern:
--   1. Rename/add columns trong order_items theo chuẩn schema yêu cầu
--   2. Thêm driver_id vào orders
--   3. Tạo bảng outbox_events (Transactional Outbox Pattern)
-- =============================================================================

-- ── 1. order_items: rename cột name → item_name, price → unit_price ──
ALTER TABLE order_items RENAME COLUMN name TO item_name;
ALTER TABLE order_items RENAME COLUMN price TO unit_price;

-- Thêm cột mô tả, subtotal tính sẵn, và timestamp tạo
ALTER TABLE order_items ADD COLUMN item_description TEXT;
ALTER TABLE order_items ADD COLUMN subtotal NUMERIC(12,2) NOT NULL DEFAULT 0.00;
ALTER TABLE order_items ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- Cập nhật subtotal cho các bản ghi cũ (subtotal = unit_price * quantity)
UPDATE order_items SET subtotal = unit_price * quantity WHERE subtotal = 0;

-- Thêm ràng buộc kiểm tra số lượng hợp lệ
ALTER TABLE order_items ADD CONSTRAINT chk_order_items_quantity CHECK (quantity > 0);

-- ── 2. orders: thêm driver_id (nullable, chờ assign sau thanh toán) ──
ALTER TABLE orders ADD COLUMN driver_id UUID;

-- ── 3. Tạo bảng outbox_events (Transactional Outbox Pattern) ──
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(100)  NOT NULL,
    aggregate_id    UUID          NOT NULL,
    event_type      VARCHAR(100)  NOT NULL,
    payload         JSONB         NOT NULL,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- Index giúp Outbox Poller query nhanh các event chưa publish
CREATE INDEX idx_outbox_events_unpublished
    ON outbox_events (created_at)
    WHERE published_at IS NULL;

-- Index tra cứu event theo aggregate
CREATE INDEX idx_outbox_events_aggregate
    ON outbox_events (aggregate_type, aggregate_id);
