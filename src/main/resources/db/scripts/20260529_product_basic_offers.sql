-- Ofertas basicas del modulo productos.
-- Separado de las tablas comerciales existentes.

CREATE TABLE IF NOT EXISTS product_basic_offer (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(180) NOT NULL,
    starts_at DATE NOT NULL,
    ends_at DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_product_basic_offer_status CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'EXPIRED')),
    CONSTRAINT ck_product_basic_offer_dates CHECK (starts_at <= ends_at)
);

CREATE TABLE IF NOT EXISTS product_basic_offer_item (
    id BIGSERIAL PRIMARY KEY,
    offer_id BIGINT NOT NULL REFERENCES product_basic_offer(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES product(id),
    min_quantity NUMERIC(14,3) NOT NULL DEFAULT 1,
    offer_price NUMERIC(14,2) NOT NULL,
    regular_price_snapshot NUMERIC(14,2),
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_product_basic_offer_item_min_qty CHECK (min_quantity > 0),
    CONSTRAINT ck_product_basic_offer_item_offer_price CHECK (offer_price > 0),
    CONSTRAINT uq_product_basic_offer_item UNIQUE (offer_id, product_id)
);

CREATE INDEX IF NOT EXISTS ix_product_basic_offer_status_dates
    ON product_basic_offer(status, starts_at, ends_at);

CREATE INDEX IF NOT EXISTS ix_product_basic_offer_item_offer
    ON product_basic_offer_item(offer_id, sort_order);
