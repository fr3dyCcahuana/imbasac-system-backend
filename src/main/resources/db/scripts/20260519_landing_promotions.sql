-- Modulo de promociones/ofertas para landing publica IMBASAC.
-- Ejecutar una sola vez sobre la base de datos principal.

CREATE TABLE IF NOT EXISTS promotion_campaign (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    slug VARCHAR(180) NOT NULL UNIQUE,
    kind VARCHAR(20) NOT NULL DEFAULT 'PROMOTION',
    type VARCHAR(40) NOT NULL DEFAULT 'COMBO',
    title VARCHAR(180) NOT NULL,
    subtitle VARCHAR(240),
    description TEXT,
    badge VARCHAR(80),
    hero_image_url TEXT,
    banner_image_url TEXT,
    discount_type VARCHAR(20) NOT NULL DEFAULT 'PERCENT',
    discount_value NUMERIC(14,2) NOT NULL DEFAULT 0,
    starts_at TIMESTAMP NULL,
    ends_at TIMESTAMP NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    priority INTEGER NOT NULL DEFAULT 0,
    channel VARCHAR(20) NOT NULL DEFAULT 'LANDING',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_promotion_campaign_kind CHECK (kind IN ('PROMOTION', 'OFFER')),
    CONSTRAINT ck_promotion_campaign_type CHECK (type IN ('COMBO', 'MOTORCYCLE_BUNDLE', 'ACCESSORY_BUNDLE', 'PRODUCT_DISCOUNT', 'CATEGORY_DISCOUNT', 'WHOLESALE_DISCOUNT', 'SPARE_PARTS', 'ACCESSORY', 'MIXED')),
    CONSTRAINT ck_promotion_campaign_discount_type CHECK (discount_type IN ('PERCENT', 'FIXED', 'PRICE_OVERRIDE')),
    CONSTRAINT ck_promotion_campaign_status CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'EXPIRED')),
    CONSTRAINT ck_promotion_campaign_channel CHECK (channel IN ('LANDING', 'WHATSAPP', 'BOTH'))
);

CREATE TABLE IF NOT EXISTS promotion_campaign_item (
    id BIGSERIAL PRIMARY KEY,
    promotion_id BIGINT NOT NULL REFERENCES promotion_campaign(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES product(id),
    role VARCHAR(20) NOT NULL DEFAULT 'PRIMARY',
    quantity NUMERIC(14,3) NOT NULL DEFAULT 1,
    promo_price NUMERIC(14,2),
    discount_percent NUMERIC(7,2),
    required BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    stock_limit NUMERIC(14,3),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_promotion_campaign_item_role CHECK (role IN ('PRIMARY', 'ACCESSORY', 'BONUS')),
    CONSTRAINT uq_promotion_campaign_item UNIQUE (promotion_id, product_id, role)
);

ALTER TABLE promotion_campaign
    ADD COLUMN IF NOT EXISTS kind VARCHAR(20) NOT NULL DEFAULT 'PROMOTION';

ALTER TABLE promotion_campaign
    ALTER COLUMN type SET DEFAULT 'COMBO';

ALTER TABLE promotion_campaign
    DROP CONSTRAINT IF EXISTS ck_promotion_campaign_kind;

ALTER TABLE promotion_campaign
    DROP CONSTRAINT IF EXISTS ck_promotion_campaign_type;

UPDATE promotion_campaign
   SET kind = CASE
       WHEN discount_type IN ('PERCENT', 'FIXED') AND COALESCE(discount_value, 0) > 0 THEN 'OFFER'
       ELSE 'PROMOTION'
   END
 WHERE kind IS NULL
    OR kind NOT IN ('PROMOTION', 'OFFER');

UPDATE promotion_campaign
   SET type = CASE
       WHEN type = 'MIXED' THEN 'COMBO'
       WHEN type = 'SPARE_PARTS' THEN 'WHOLESALE_DISCOUNT'
       WHEN type = 'ACCESSORY' THEN 'PRODUCT_DISCOUNT'
       ELSE type
   END
 WHERE type IN ('MIXED', 'SPARE_PARTS', 'ACCESSORY');

ALTER TABLE promotion_campaign
    ADD CONSTRAINT ck_promotion_campaign_kind CHECK (kind IN ('PROMOTION', 'OFFER'));

ALTER TABLE promotion_campaign
    ADD CONSTRAINT ck_promotion_campaign_type
    CHECK (type IN ('COMBO', 'MOTORCYCLE_BUNDLE', 'ACCESSORY_BUNDLE', 'PRODUCT_DISCOUNT', 'CATEGORY_DISCOUNT', 'WHOLESALE_DISCOUNT', 'SPARE_PARTS', 'ACCESSORY', 'MIXED'));

CREATE TABLE IF NOT EXISTS landing_order (
    id BIGSERIAL PRIMARY KEY,
    series VARCHAR(10) NOT NULL,
    number BIGINT NOT NULL,
    customer_name VARCHAR(180),
    customer_document_type VARCHAR(20),
    customer_document_number VARCHAR(30),
    phone VARCHAR(30),
    email VARCHAR(180),
    source VARCHAR(30) NOT NULL DEFAULT 'LANDING',
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    currency VARCHAR(3) NOT NULL DEFAULT 'PEN',
    subtotal NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    total NUMERIC(14,2) NOT NULL DEFAULT 0,
    notes TEXT,
    converted_proforma_id BIGINT NULL REFERENCES proforma(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_landing_order_doc UNIQUE (series, number),
    CONSTRAINT ck_landing_order_status CHECK (status IN ('NEW', 'CONTACTED', 'CONVERTED', 'CANCELLED'))
);

CREATE TABLE IF NOT EXISTS landing_order_item (
    id BIGSERIAL PRIMARY KEY,
    landing_order_id BIGINT NOT NULL REFERENCES landing_order(id) ON DELETE CASCADE,
    promotion_id BIGINT NULL REFERENCES promotion_campaign(id),
    product_id BIGINT NOT NULL REFERENCES product(id),
    description VARCHAR(240) NOT NULL,
    quantity NUMERIC(14,3) NOT NULL DEFAULT 1,
    unit_price NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    total NUMERIC(14,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE SEQUENCE IF NOT EXISTS landing_promotion_request_code_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS landing_promotion_request (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    business_name VARCHAR(180) NOT NULL,
    contact_name VARCHAR(180) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    document_number VARCHAR(30),
    type VARCHAR(30) NOT NULL DEFAULT 'OFFER',
    title VARCHAR(180) NOT NULL,
    description TEXT,
    image_url TEXT,
    starts_at TIMESTAMP NULL,
    ends_at TIMESTAMP NULL,
    items_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    review_notes TEXT,
    created_promotion_id BIGINT NULL REFERENCES promotion_campaign(id),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_landing_promotion_request_type CHECK (type IN ('OFFER', 'WHOLESALE_PROMOTION', 'MOTORCYCLE_BUNDLE')),
    CONSTRAINT ck_landing_promotion_request_status CHECK (status IN ('NEW', 'REVIEWING', 'APPROVED', 'REJECTED', 'PUBLISHED'))
);

CREATE INDEX IF NOT EXISTS ix_promotion_campaign_status_dates
    ON promotion_campaign(status, starts_at, ends_at, priority DESC);

CREATE INDEX IF NOT EXISTS ix_promotion_campaign_item_promotion
    ON promotion_campaign_item(promotion_id, sort_order);

CREATE INDEX IF NOT EXISTS ix_landing_order_status_created
    ON landing_order(status, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_landing_promotion_request_status_created
    ON landing_promotion_request(status, created_at DESC);

ALTER TABLE document_series
    DROP CONSTRAINT IF EXISTS ck_document_series_doc_type;

ALTER TABLE document_series
    ADD CONSTRAINT ck_document_series_doc_type
    CHECK (doc_type IN ('BOLETA', 'GUIDE_REMISSION', 'FACTURA', 'PROFORMA', 'SIMPLE', 'CONTRACT', 'VENTANILLA', 'LANDING_ORDER'));

INSERT INTO document_series (doc_type, series, next_number, enabled, station_id)
SELECT 'LANDING_ORDER', 'LP01', 1, TRUE, NULL
WHERE NOT EXISTS (
    SELECT 1
    FROM document_series
    WHERE doc_type = 'LANDING_ORDER'
      AND series = 'LP01'
);

INSERT INTO permissions (name, domain, description)
SELECT 'MANAGE_PROMOTIONS', 'Promociones', 'Gestionar promociones, ofertas y combos de landing/WhatsApp'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE name = 'MANAGE_PROMOTIONS'
);

INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, p_new.id
FROM role_permissions rp
JOIN permissions p_existing ON p_existing.id = rp.permission_id
JOIN permissions p_new ON p_new.name = 'MANAGE_PROMOTIONS'
WHERE p_existing.name = 'MANAGE_PRODUCTS'
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions rp2
      WHERE rp2.role_id = rp.role_id
        AND rp2.permission_id = p_new.id
  );

-- Datos de prueba controlados para disenar y probar landing.
-- Se eliminan antes de insertar para que el script pueda re-ejecutarse en QA sin duplicados.
DELETE FROM promotion_campaign WHERE code IN (
    'PROMO_COMBO_CAMBIO_ACEITE',
    'PROMO_MOTO_ACCESORIOS_ESSENTIAL',
    'PROMO_KIT_SEGURIDAD_RUTA',
    'PROMO_PACK_MANTENIMIENTO_PREMIUM',
    'PROMO_ACCESORIOS_URBANOS',
    'OFERTA_REPUESTO_20',
    'OFERTA_BAJAS_15',
    'OFERTA_CASCOS_25',
    'OFERTA_STOCK_LIMITADO_50',
    'OFERTA_MANTENIMIENTO_10',
    'OFERTA_MOTO_SEMANA'
);

DO $$
DECLARE
    product_ids BIGINT[];
    p1 BIGINT;
    p2 BIGINT;
    p3 BIGINT;
    p4 BIGINT;
    p5 BIGINT;
    p6 BIGINT;
    p7 BIGINT;
    p8 BIGINT;
    p9 BIGINT;
    p10 BIGINT;
    campaign_id BIGINT;
BEGIN
    SELECT ARRAY_AGG(id ORDER BY id) INTO product_ids
    FROM (
        SELECT id FROM product ORDER BY id LIMIT 10
    ) products_seed;

    p1 := product_ids[1];
    p2 := COALESCE(product_ids[2], p1);
    p3 := COALESCE(product_ids[3], p1);
    p4 := COALESCE(product_ids[4], p1);
    p5 := COALESCE(product_ids[5], p1);
    p6 := COALESCE(product_ids[6], p1);
    p7 := COALESCE(product_ids[7], p1);
    p8 := COALESCE(product_ids[8], p1);
    p9 := COALESCE(product_ids[9], p1);
    p10 := COALESCE(product_ids[10], p1);

    IF p1 IS NOT NULL THEN
        INSERT INTO promotion_campaign (
            code, name, slug, kind, type, title, subtitle, description, badge,
            hero_image_url, banner_image_url, discount_type, discount_value,
            starts_at, ends_at, status, priority, channel
        )
        VALUES (
            'PROMO_COMBO_CAMBIO_ACEITE',
            'Combo cambio de aceite',
            'combo-cambio-aceite',
            'PROMOTION',
            'COMBO',
            'Aceite + filtros a precio paquete',
            'Promocion por paquete: varios productos en una sola propuesta comercial.',
            'Promocion: no es descuento directo; es un combo con precio especial.',
            'Combo',
            'https://images.unsplash.com/photo-1558981806-ec527fa84c39?auto=format&fit=crop&w=1400&q=80',
            'https://images.unsplash.com/photo-1558980664-10e7170e2f9c?auto=format&fit=crop&w=1400&q=80',
            'PRICE_OVERRIDE',
            0,
            NOW(),
            NOW() + INTERVAL '30 days',
            'ACTIVE',
            90,
            'BOTH'
        )
        RETURNING id INTO campaign_id;

        INSERT INTO promotion_campaign_item (
            promotion_id, product_id, role, quantity, promo_price, discount_percent, required, sort_order
        )
        VALUES
            (campaign_id, p1, 'PRIMARY', 1, (SELECT ROUND(GREATEST(price_a * 0.90, 1), 2) FROM product WHERE id = p1), NULL, TRUE, 0),
            (campaign_id, p2, 'ACCESSORY', 1, NULL, NULL, TRUE, 1),
            (campaign_id, p3, 'BONUS', 1, 0, NULL, TRUE, 2)
        ON CONFLICT DO NOTHING;

        INSERT INTO promotion_campaign (
            code, name, slug, kind, type, title, subtitle, description, badge,
            hero_image_url, banner_image_url, discount_type, discount_value,
            starts_at, ends_at, status, priority, channel
        )
        VALUES (
            'PROMO_MOTO_ACCESORIOS_ESSENTIAL',
            'Moto + accesorios essential',
            'moto-accesorios-essential',
            'PROMOTION',
            'MOTORCYCLE_BUNDLE',
            'Moto lista para salir con accesorios incluidos',
            'Incluye accesorios seleccionados para que el cliente compre con mayor valor percibido.',
            'Promocion tipo paquete: la moto mantiene protagonismo y los accesorios se comunican como beneficio incluido.',
            'Incluye accesorios',
            'https://images.unsplash.com/photo-1449426468159-d96dbf08f19f?auto=format&fit=crop&w=1400&q=80',
            'https://images.unsplash.com/photo-1558981285-6f0c94958bb6?auto=format&fit=crop&w=1400&q=80',
            'PRICE_OVERRIDE',
            0,
            NOW(),
            NOW() + INTERVAL '45 days',
            'ACTIVE',
            98,
            'BOTH'
        )
        RETURNING id INTO campaign_id;

        INSERT INTO promotion_campaign_item (
            promotion_id, product_id, role, quantity, promo_price, discount_percent, required, sort_order
        )
        VALUES
            (campaign_id, p4, 'PRIMARY', 1, NULL, NULL, TRUE, 0),
            (campaign_id, p5, 'BONUS', 1, 0, NULL, TRUE, 1),
            (campaign_id, p6, 'ACCESSORY', 1, NULL, NULL, TRUE, 2)
        ON CONFLICT DO NOTHING;

        INSERT INTO promotion_campaign (
            code, name, slug, kind, type, title, subtitle, description, badge,
            hero_image_url, banner_image_url, discount_type, discount_value,
            starts_at, ends_at, status, priority, channel
        )
        VALUES (
            'PROMO_KIT_SEGURIDAD_RUTA',
            'Kit seguridad para ruta',
            'kit-seguridad-ruta',
            'PROMOTION',
            'ACCESSORY_BUNDLE',
            'Casco + accesorio de seguridad en paquete',
            'Promocion ideal para compradores que buscan salir equipados.',
            'Agrupa productos complementarios en una sola propuesta comercial con precio de paquete.',
            'Kit ruta',
            'https://images.unsplash.com/photo-1558981001-5864b3250a69?auto=format&fit=crop&w=1400&q=80',
            'https://images.unsplash.com/photo-1558980394-4c7c9299fe96?auto=format&fit=crop&w=1400&q=80',
            'PRICE_OVERRIDE',
            0,
            NOW(),
            NOW() + INTERVAL '25 days',
            'ACTIVE',
            86,
            'LANDING'
        )
        RETURNING id INTO campaign_id;

        INSERT INTO promotion_campaign_item (
            promotion_id, product_id, role, quantity, promo_price, discount_percent, required, sort_order
        )
        VALUES
            (campaign_id, p5, 'PRIMARY', 1, (SELECT ROUND(GREATEST(price_a * 0.88, 1), 2) FROM product WHERE id = p5), NULL, TRUE, 0),
            (campaign_id, p6, 'ACCESSORY', 1, NULL, NULL, TRUE, 1)
        ON CONFLICT DO NOTHING;

        INSERT INTO promotion_campaign (
            code, name, slug, kind, type, title, subtitle, description, badge,
            hero_image_url, banner_image_url, discount_type, discount_value,
            starts_at, ends_at, status, priority, channel
        )
        VALUES (
            'PROMO_PACK_MANTENIMIENTO_PREMIUM',
            'Pack mantenimiento premium',
            'pack-mantenimiento-premium',
            'PROMOTION',
            'COMBO',
            'Repuestos seleccionados para mantenimiento completo',
            'Varios productos se venden juntos para simplificar la compra.',
            'Promocion por combo: comunica conveniencia, rapidez y precio paquete.',
            'Pack premium',
            'https://images.unsplash.com/photo-1487754180451-c456f719a1fc?auto=format&fit=crop&w=1400&q=80',
            'https://images.unsplash.com/photo-1493238792000-8113da705763?auto=format&fit=crop&w=1400&q=80',
            'PRICE_OVERRIDE',
            0,
            NOW(),
            NOW() + INTERVAL '35 days',
            'ACTIVE',
            75,
            'BOTH'
        )
        RETURNING id INTO campaign_id;

        INSERT INTO promotion_campaign_item (
            promotion_id, product_id, role, quantity, promo_price, discount_percent, required, sort_order
        )
        VALUES
            (campaign_id, p7, 'PRIMARY', 1, (SELECT ROUND(GREATEST(price_a * 0.92, 1), 2) FROM product WHERE id = p7), NULL, TRUE, 0),
            (campaign_id, p8, 'ACCESSORY', 1, NULL, NULL, TRUE, 1),
            (campaign_id, p9, 'ACCESSORY', 1, NULL, NULL, TRUE, 2)
        ON CONFLICT DO NOTHING;

        INSERT INTO promotion_campaign (
            code, name, slug, kind, type, title, subtitle, description, badge,
            hero_image_url, banner_image_url, discount_type, discount_value,
            starts_at, ends_at, status, priority, channel
        )
        VALUES (
            'PROMO_ACCESORIOS_URBANOS',
            'Accesorios urbanos',
            'accesorios-urbanos',
            'PROMOTION',
            'ACCESSORY_BUNDLE',
            'Accesorios para uso diario en un solo paquete',
            'Promocion para clientes que quieren equipar su moto con una compra sencilla.',
            'Paquete de accesorios: no comunica descuento suelto, comunica valor agregado.',
            'Pack urbano',
            'https://images.unsplash.com/photo-1532298229144-0ec0c57515c7?auto=format&fit=crop&w=1400&q=80',
            'https://images.unsplash.com/photo-1568772585407-9361f9bf3a87?auto=format&fit=crop&w=1400&q=80',
            'PRICE_OVERRIDE',
            0,
            NOW(),
            NOW() + INTERVAL '28 days',
            'ACTIVE',
            62,
            'LANDING'
        )
        RETURNING id INTO campaign_id;

        INSERT INTO promotion_campaign_item (
            promotion_id, product_id, role, quantity, promo_price, discount_percent, required, sort_order
        )
        VALUES
            (campaign_id, p8, 'PRIMARY', 1, (SELECT ROUND(GREATEST(price_a * 0.90, 1), 2) FROM product WHERE id = p8), NULL, TRUE, 0),
            (campaign_id, p10, 'ACCESSORY', 1, NULL, NULL, TRUE, 1)
        ON CONFLICT DO NOTHING;

        INSERT INTO promotion_campaign (
            code, name, slug, kind, type, title, subtitle, description, badge,
            hero_image_url, banner_image_url, discount_type, discount_value,
            starts_at, ends_at, status, priority, channel
        )
        VALUES (
            'OFERTA_REPUESTO_20',
            'Oferta 20% en repuesto seleccionado',
            'oferta-repuesto-20',
            'OFFER',
            'PRODUCT_DISCOUNT',
            '20% menos en producto seleccionado',
            'Oferta: descuento directo aplicado al precio normal del producto.',
            'Oferta comercial con descuento porcentual. No agrupa productos ni agrega accesorios.',
            '-20%',
            'https://images.unsplash.com/photo-1558981033-0f0309284409?auto=format&fit=crop&w=1400&q=80',
            'https://images.unsplash.com/photo-1503376780353-7e6692767b70?auto=format&fit=crop&w=1400&q=80',
            'PERCENT',
            20,
            NOW(),
            NOW() + INTERVAL '15 days',
            'ACTIVE',
            80,
            'BOTH'
        )
        RETURNING id INTO campaign_id;

        INSERT INTO promotion_campaign_item (
            promotion_id, product_id, role, quantity, promo_price, discount_percent, required, sort_order
        )
        VALUES
            (campaign_id, p1, 'PRIMARY', 1, NULL, 20, TRUE, 0)
        ON CONFLICT DO NOTHING;

        INSERT INTO promotion_campaign (
            code, name, slug, kind, type, title, subtitle, description, badge,
            hero_image_url, banner_image_url, discount_type, discount_value,
            starts_at, ends_at, status, priority, channel
        )
        VALUES (
            'OFERTA_BAJAS_15',
            'Oferta 15% Bajaj seleccionados',
            'oferta-bajaj-15',
            'OFFER',
            'PRODUCT_DISCOUNT',
            '15% menos en productos seleccionados Bajaj',
            'Oferta con descuento directo para llamar la atencion por precio.',
            'El cliente ve precio real, precio rebajado y ahorro porcentual.',
            '-15%',
            'https://images.unsplash.com/photo-1542362567-b07e54358753?auto=format&fit=crop&w=1400&q=80',
            'https://images.unsplash.com/photo-1558981806-ec527fa84c39?auto=format&fit=crop&w=1400&q=80',
            'PERCENT',
            15,
            NOW(),
            NOW() + INTERVAL '20 days',
            'ACTIVE',
            84,
            'BOTH'
        )
        RETURNING id INTO campaign_id;

        INSERT INTO promotion_campaign_item (
            promotion_id, product_id, role, quantity, promo_price, discount_percent, required, sort_order
        )
        VALUES
            (campaign_id, p2, 'PRIMARY', 1, NULL, 15, TRUE, 0),
            (campaign_id, p3, 'ACCESSORY', 1, NULL, 15, TRUE, 1)
        ON CONFLICT DO NOTHING;

        INSERT INTO promotion_campaign (
            code, name, slug, kind, type, title, subtitle, description, badge,
            hero_image_url, banner_image_url, discount_type, discount_value,
            starts_at, ends_at, status, priority, channel
        )
        VALUES (
            'OFERTA_CASCOS_25',
            'Oferta cascos y seguridad 25%',
            'oferta-cascos-seguridad-25',
            'OFFER',
            'CATEGORY_DISCOUNT',
            '25% menos en seguridad seleccionada',
            'Oferta para destacar ahorro fuerte en accesorios de seguridad.',
            'Descuento porcentual directo; no agrega productos ni beneficios adicionales.',
            '-25%',
            'https://images.unsplash.com/photo-1558981359-219d6364c9c8?auto=format&fit=crop&w=1400&q=80',
            'https://images.unsplash.com/photo-1558981403-c5f9899a28bc?auto=format&fit=crop&w=1400&q=80',
            'PERCENT',
            25,
            NOW(),
            NOW() + INTERVAL '12 days',
            'ACTIVE',
            79,
            'LANDING'
        )
        RETURNING id INTO campaign_id;

        INSERT INTO promotion_campaign_item (
            promotion_id, product_id, role, quantity, promo_price, discount_percent, required, sort_order
        )
        VALUES
            (campaign_id, p5, 'PRIMARY', 1, NULL, 25, TRUE, 0)
        ON CONFLICT DO NOTHING;

        INSERT INTO promotion_campaign (
            code, name, slug, kind, type, title, subtitle, description, badge,
            hero_image_url, banner_image_url, discount_type, discount_value,
            starts_at, ends_at, status, priority, channel
        )
        VALUES (
            'OFERTA_STOCK_LIMITADO_50',
            'Oferta stock limitado S/ 50',
            'oferta-stock-limitado-50',
            'OFFER',
            'PRODUCT_DISCOUNT',
            'S/ 50 menos por stock limitado',
            'Oferta de monto fijo para generar urgencia comercial.',
            'Descuento fijo directo aplicado contra el precio real del producto.',
            'S/ 50 menos',
            'https://images.unsplash.com/photo-1558981852-426c6c22a060?auto=format&fit=crop&w=1400&q=80',
            'https://images.unsplash.com/photo-1517524008697-84bbe3c3fd98?auto=format&fit=crop&w=1400&q=80',
            'FIXED',
            50,
            NOW(),
            NOW() + INTERVAL '10 days',
            'ACTIVE',
            72,
            'BOTH'
        )
        RETURNING id INTO campaign_id;

        INSERT INTO promotion_campaign_item (
            promotion_id, product_id, role, quantity, promo_price, discount_percent, required, sort_order
        )
        VALUES
            (campaign_id, p6, 'PRIMARY', 1, NULL, NULL, TRUE, 0)
        ON CONFLICT DO NOTHING;

        INSERT INTO promotion_campaign (
            code, name, slug, kind, type, title, subtitle, description, badge,
            hero_image_url, banner_image_url, discount_type, discount_value,
            starts_at, ends_at, status, priority, channel
        )
        VALUES (
            'OFERTA_MANTENIMIENTO_10',
            'Oferta mantenimiento 10%',
            'oferta-mantenimiento-10',
            'OFFER',
            'WHOLESALE_DISCOUNT',
            '10% menos en productos de mantenimiento',
            'Oferta simple para rotar repuestos de uso frecuente.',
            'Descuento porcentual para productos seleccionados de mantenimiento.',
            '-10%',
            'https://images.unsplash.com/photo-1487754180451-c456f719a1fc?auto=format&fit=crop&w=1400&q=80',
            'https://images.unsplash.com/photo-1492144534655-ae79c964c9d7?auto=format&fit=crop&w=1400&q=80',
            'PERCENT',
            10,
            NOW(),
            NOW() + INTERVAL '18 days',
            'ACTIVE',
            66,
            'WHATSAPP'
        )
        RETURNING id INTO campaign_id;

        INSERT INTO promotion_campaign_item (
            promotion_id, product_id, role, quantity, promo_price, discount_percent, required, sort_order
        )
        VALUES
            (campaign_id, p7, 'PRIMARY', 1, NULL, 10, TRUE, 0),
            (campaign_id, p8, 'ACCESSORY', 1, NULL, 10, TRUE, 1)
        ON CONFLICT DO NOTHING;

        INSERT INTO promotion_campaign (
            code, name, slug, kind, type, title, subtitle, description, badge,
            hero_image_url, banner_image_url, discount_type, discount_value,
            starts_at, ends_at, status, priority, channel
        )
        VALUES (
            'OFERTA_MOTO_SEMANA',
            'Oferta moto de la semana',
            'oferta-moto-semana',
            'OFFER',
            'PRODUCT_DISCOUNT',
            'Precio especial en la moto de la semana',
            'Oferta con precio rebajado directo para captar prospectos.',
            'Precio especial: se compara contra el precio real para mostrar ahorro.',
            'Precio especial',
            'https://images.unsplash.com/photo-1558981285-6f0c94958bb6?auto=format&fit=crop&w=1400&q=80',
            'https://images.unsplash.com/photo-1449426468159-d96dbf08f19f?auto=format&fit=crop&w=1400&q=80',
            'PRICE_OVERRIDE',
            0,
            NOW(),
            NOW() + INTERVAL '7 days',
            'ACTIVE',
            94,
            'BOTH'
        )
        RETURNING id INTO campaign_id;

        INSERT INTO promotion_campaign_item (
            promotion_id, product_id, role, quantity, promo_price, discount_percent, required, sort_order
        )
        VALUES
            (campaign_id, p10, 'PRIMARY', 1, (SELECT ROUND(GREATEST(price_a * 0.82, 1), 2) FROM product WHERE id = p10), NULL, TRUE, 0)
        ON CONFLICT DO NOTHING;
    END IF;
END $$;
