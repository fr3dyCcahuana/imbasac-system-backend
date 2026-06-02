-- ===============================================================================
-- MÓDULO DE PROMOCIONES/OFERTAS PARA LANDING PÚBLICA IMBASAC
-- Flujo separado de ofertas básicas por producto (product_offer)
-- Soporta imágenes, configuración "featured" para carrusel, y múltiples canales
-- ===============================================================================
-- EJECUTAR UNA SOLA VEZ: Elimina todo y recrea desde cero
-- Apto para desarrollo/QA donde se requiere limpiar y recrear completamente
-- ===============================================================================

-- ===============================================================================
-- PARTE 1: DELETE/DROP - Limpia el flujo anterior completamente
-- ===============================================================================

-- Elimina datos de prueba
DELETE FROM landing_promotion_request WHERE id > 0;
DELETE FROM landing_order_item WHERE id > 0;
DELETE FROM landing_order WHERE id > 0;
DELETE FROM promotion_campaign_item WHERE id > 0;
DELETE FROM promotion_campaign_image WHERE id > 0;
DELETE FROM promotion_campaign WHERE id > 0;

-- Elimina tablas
DROP TABLE IF EXISTS landing_promotion_request CASCADE;
DROP TABLE IF EXISTS landing_order_item CASCADE;
DROP TABLE IF EXISTS landing_order CASCADE;
DROP TABLE IF EXISTS promotion_campaign_item CASCADE;
DROP TABLE IF EXISTS promotion_campaign_image CASCADE;
DROP TABLE IF EXISTS promotion_campaign CASCADE;

-- Limpia secuencias
DROP SEQUENCE IF EXISTS landing_promotion_request_code_seq;

-- ===============================================================================
-- PARTE 2: CREATE - Define las tablas nuevas sin ALTER
-- ===============================================================================

-- Tabla principal: campañas de promociones/ofertas
CREATE TABLE promotion_campaign (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(160) NOT NULL,
    slug VARCHAR(180) NOT NULL UNIQUE,
    kind VARCHAR(20) NOT NULL DEFAULT 'PROMOTION'
        CHECK (kind IN ('PROMOTION', 'OFFER')),
    type VARCHAR(40) NOT NULL DEFAULT 'COMBO'
        CHECK (type IN ('COMBO', 'MOTORCYCLE_BUNDLE', 'ACCESSORY_BUNDLE', 'PRODUCT_DISCOUNT', 'CATEGORY_DISCOUNT', 'WHOLESALE_DISCOUNT', 'SPARE_PARTS', 'ACCESSORY', 'MIXED')),
    title VARCHAR(180) NOT NULL,
    subtitle VARCHAR(240),
    description TEXT,
    badge VARCHAR(80),
    discount_type VARCHAR(20) NOT NULL DEFAULT 'PERCENT'
        CHECK (discount_type IN ('PERCENT', 'FIXED', 'PRICE_OVERRIDE')),
    discount_value NUMERIC(14,2) NOT NULL DEFAULT 0,
    starts_at TIMESTAMP NULL,
    ends_at TIMESTAMP NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'ACTIVE', 'PAUSED', 'EXPIRED')),
    priority INTEGER NOT NULL DEFAULT 0,
    channel VARCHAR(20) NOT NULL DEFAULT 'LANDING'
        CHECK (channel IN ('LANDING', 'WHATSAPP', 'BOTH')),
    featured BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Tabla de items dentro de una promoción (relación promoción-productos)
CREATE TABLE promotion_campaign_item (
    id BIGSERIAL PRIMARY KEY,
    promotion_id BIGINT NOT NULL REFERENCES promotion_campaign(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
    role VARCHAR(20) NOT NULL DEFAULT 'PRIMARY'
        CHECK (role IN ('PRIMARY', 'ACCESSORY', 'BONUS')),
    quantity NUMERIC(14,3) NOT NULL DEFAULT 1,
    promo_price NUMERIC(14,2),
    discount_percent NUMERIC(7,2),
    required BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    stock_limit NUMERIC(14,3),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_promotion_campaign_item UNIQUE (promotion_id, product_id, role)
);

-- Tabla de imágenes para promociones
-- Almacena KEY en image_url (ej: "123/a1b2c3d4.jpg")
-- El servicio PromotionImagePublicUrlService convierte KEY a URL pública
CREATE TABLE promotion_campaign_image (
    id BIGSERIAL PRIMARY KEY,
    campaign_id BIGINT NOT NULL REFERENCES promotion_campaign(id) ON DELETE CASCADE,
    image_url TEXT NOT NULL,
    position SMALLINT NOT NULL DEFAULT 1,
    is_main BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_promotion_campaign_image UNIQUE (campaign_id, position)
);

-- Tabla de órdenes generadas desde landing
CREATE TABLE landing_order (
    id BIGSERIAL PRIMARY KEY,
    series VARCHAR(10) NOT NULL,
    number BIGINT NOT NULL,
    customer_name VARCHAR(180),
    customer_document_type VARCHAR(20),
    customer_document_number VARCHAR(30),
    phone VARCHAR(30),
    email VARCHAR(180),
    source VARCHAR(30) NOT NULL DEFAULT 'LANDING',
    status VARCHAR(20) NOT NULL DEFAULT 'NEW'
        CHECK (status IN ('NEW', 'CONTACTED', 'CONVERTED', 'CANCELLED')),
    currency VARCHAR(3) NOT NULL DEFAULT 'PEN',
    subtotal NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount_total NUMERIC(14,2) NOT NULL DEFAULT 0,
    total NUMERIC(14,2) NOT NULL DEFAULT 0,
    notes TEXT,
    converted_proforma_id BIGINT NULL REFERENCES proforma(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_landing_order_doc UNIQUE (series, number)
);

-- Tabla de items dentro de una orden de landing
CREATE TABLE landing_order_item (
    id BIGSERIAL PRIMARY KEY,
    landing_order_id BIGINT NOT NULL REFERENCES landing_order(id) ON DELETE CASCADE,
    promotion_id BIGINT NULL REFERENCES promotion_campaign(id) ON DELETE SET NULL,
    product_id BIGINT NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
    description VARCHAR(240) NOT NULL,
    quantity NUMERIC(14,3) NOT NULL DEFAULT 1,
    unit_price NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    total NUMERIC(14,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Tabla de solicitudes de promociones (flujo de revisión)
CREATE TABLE landing_promotion_request (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(20) NOT NULL UNIQUE,
    business_name VARCHAR(180) NOT NULL,
    contact_name VARCHAR(180) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    document_number VARCHAR(30),
    type VARCHAR(30) NOT NULL DEFAULT 'OFFER'
        CHECK (type IN ('OFFER', 'WHOLESALE_PROMOTION', 'MOTORCYCLE_BUNDLE')),
    title VARCHAR(180) NOT NULL,
    description TEXT,
    image_url TEXT,
    starts_at TIMESTAMP NULL,
    ends_at TIMESTAMP NULL,
    items_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW'
        CHECK (status IN ('NEW', 'REVIEWING', 'APPROVED', 'REJECTED', 'PUBLISHED')),
    review_notes TEXT,
    created_promotion_id BIGINT NULL REFERENCES promotion_campaign(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- ===============================================================================
-- ÍNDICES PARA OPTIMIZAR BÚSQUEDAS Y FILTROS
-- ===============================================================================

-- Índice para búsquedas de promociones activas por fecha y prioridad
CREATE INDEX ix_promotion_campaign_status_dates
    ON promotion_campaign(status, starts_at, ends_at, priority DESC);

-- Índice filtrado para promociones destacadas en carrusel
-- Solo indexa donde featured = TRUE para mejor rendimiento
CREATE INDEX ix_promotion_campaign_featured
    ON promotion_campaign(featured, status)
    WHERE featured = TRUE;

-- Índice para items de una promoción ordenados
CREATE INDEX ix_promotion_campaign_item_promotion
    ON promotion_campaign_item(promotion_id, sort_order);

-- Índice para imágenes de una campaña ordenadas
CREATE INDEX ix_promotion_campaign_image_campaign
    ON promotion_campaign_image(campaign_id, position);

-- Índice para órdenes por estado y fecha
CREATE INDEX ix_landing_order_status_created
    ON landing_order(status, created_at DESC);

-- Índice para solicitudes de promociones por estado y fecha
CREATE INDEX ix_landing_promotion_request_status_created
    ON landing_promotion_request(status, created_at DESC);

-- ===============================================================================
-- ACTUALIZA PERMISOS Y SERIES DE DOCUMENTOS
-- ===============================================================================

-- Agrega tipo de documento LANDING_ORDER a los valores válidos
DO $$
BEGIN
    BEGIN
        ALTER TABLE document_series
        ADD CONSTRAINT ck_document_series_doc_type
        CHECK (doc_type IN ('BOLETA', 'GUIDE_REMISSION', 'FACTURA', 'PROFORMA',
                             'SIMPLE', 'CONTRACT', 'VENTANILLA', 'LANDING_ORDER'));
    EXCEPTION WHEN duplicate_object THEN
        NULL;
    END;
END $$;

-- Inserta serie de documentos para órdenes de landing
INSERT INTO document_series (doc_type, series, next_number, enabled, station_id)
SELECT 'LANDING_ORDER', 'LP01', 1, TRUE, NULL
WHERE NOT EXISTS (
    SELECT 1 FROM document_series
    WHERE doc_type = 'LANDING_ORDER' AND series = 'LP01'
);

-- Inserta permiso de gestión de promociones
INSERT INTO permissions (name, domain, description)
SELECT 'MANAGE_PROMOTIONS', 'Promociones',
       'Gestionar promociones, ofertas y combos de landing/WhatsApp'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE name = 'MANAGE_PROMOTIONS'
);

-- Asigna permiso a roles que pueden gestionar productos
INSERT INTO role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, p_new.id
FROM role_permissions rp
JOIN permissions p_existing ON p_existing.id = rp.permission_id
JOIN permissions p_new ON p_new.name = 'MANAGE_PROMOTIONS'
WHERE p_existing.name = 'MANAGE_PRODUCTS'
  AND NOT EXISTS (
      SELECT 1 FROM role_permissions rp2
      WHERE rp2.role_id = rp.role_id AND rp2.permission_id = p_new.id
  );

-- ===============================================================================
-- DATOS DE PRUEBA (Controlados para diseño de landing)
-- Se pueden eliminar antes de insertar para re-ejecutar sin duplicados
-- ===============================================================================

CREATE SEQUENCE IF NOT EXISTS landing_promotion_request_code_seq START WITH 1 INCREMENT BY 1;

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
    -- Obtiene los primeros 10 productos disponibles
    SELECT ARRAY_AGG(id ORDER BY id) INTO product_ids
    FROM (
        SELECT id FROM product ORDER BY id LIMIT 10
    ) products_seed;

    -- Asigna variables de producto
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
        -- PROMOCIÓN 1: Combo cambio de aceite (featured=true para carrusel)
        INSERT INTO promotion_campaign (
            code, name, slug, kind, type, title, subtitle, description, badge,
            discount_type, discount_value, starts_at, ends_at, status, priority,
            channel, featured
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
            'PRICE_OVERRIDE',
            0,
            NOW(),
            NOW() + INTERVAL '30 days',
            'ACTIVE',
            90,
            'BOTH',
            TRUE
        )
        RETURNING id INTO campaign_id;

        INSERT INTO promotion_campaign_item (promotion_id, product_id, role, quantity, promo_price, discount_percent, required, sort_order)
        VALUES
            (campaign_id, p1, 'PRIMARY', 1, (SELECT ROUND(GREATEST(price_a * 0.90, 1), 2) FROM product WHERE id = p1), NULL, TRUE, 0),
            (campaign_id, p2, 'ACCESSORY', 1, NULL, NULL, TRUE, 1),
            (campaign_id, p3, 'BONUS', 1, 0, NULL, TRUE, 2)
        ON CONFLICT DO NOTHING;

        -- PROMOCIÓN 2: Moto + accesorios essential (featured=true para carrusel)
        INSERT INTO promotion_campaign (
            code, name, slug, kind, type, title, subtitle, description, badge,
            discount_type, discount_value, starts_at, ends_at, status, priority,
            channel, featured
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
            'PRICE_OVERRIDE',
            0,
            NOW(),
            NOW() + INTERVAL '45 days',
            'ACTIVE',
            98,
            'BOTH',
            TRUE
        )
        RETURNING id INTO campaign_id;

        INSERT INTO promotion_campaign_item (promotion_id, product_id, role, quantity, promo_price, discount_percent, required, sort_order)
        VALUES
            (campaign_id, p4, 'PRIMARY', 1, NULL, NULL, TRUE, 0),
            (campaign_id, p5, 'BONUS', 1, 0, NULL, TRUE, 1),
            (campaign_id, p6, 'ACCESSORY', 1, NULL, NULL, TRUE, 2)
        ON CONFLICT DO NOTHING;

        -- PROMOCIÓN 3: Kit seguridad para ruta (featured=true para carrusel)
        INSERT INTO promotion_campaign (
            code, name, slug, kind, type, title, subtitle, description, badge,
            discount_type, discount_value, starts_at, ends_at, status, priority,
            channel, featured
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
            'PRICE_OVERRIDE',
            0,
            NOW(),
            NOW() + INTERVAL '25 days',
            'ACTIVE',
            86,
            'LANDING',
            TRUE
        )
        RETURNING id INTO campaign_id;

        INSERT INTO promotion_campaign_item (promotion_id, product_id, role, quantity, promo_price, discount_percent, required, sort_order)
        VALUES
            (campaign_id, p5, 'PRIMARY', 1, (SELECT ROUND(GREATEST(price_a * 0.88, 1), 2) FROM product WHERE id = p5), NULL, TRUE, 0),
            (campaign_id, p6, 'ACCESSORY', 1, NULL, NULL, TRUE, 1)
        ON CONFLICT DO NOTHING;

        -- PROMOCIÓN 4: Pack mantenimiento premium (featured=false, no aparece en carrusel)
        INSERT INTO promotion_campaign (
            code, name, slug, kind, type, title, subtitle, description, badge,
            discount_type, discount_value, starts_at, ends_at, status, priority,
            channel, featured
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
            'PRICE_OVERRIDE',
            0,
            NOW(),
            NOW() + INTERVAL '35 days',
            'ACTIVE',
            75,
            'BOTH',
            FALSE
        )
        RETURNING id INTO campaign_id;

        INSERT INTO promotion_campaign_item (promotion_id, product_id, role, quantity, promo_price, discount_percent, required, sort_order)
        VALUES
            (campaign_id, p7, 'PRIMARY', 1, (SELECT ROUND(GREATEST(price_a * 0.92, 1), 2) FROM product WHERE id = p7), NULL, TRUE, 0),
            (campaign_id, p8, 'ACCESSORY', 1, NULL, NULL, TRUE, 1),
            (campaign_id, p9, 'ACCESSORY', 1, NULL, NULL, TRUE, 2)
        ON CONFLICT DO NOTHING;

        -- OFERTA 1: Repuesto 20% (featured=false)
        INSERT INTO promotion_campaign (
            code, name, slug, kind, type, title, subtitle, description, badge,
            discount_type, discount_value, starts_at, ends_at, status, priority,
            channel, featured
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
            'PERCENT',
            20,
            NOW(),
            NOW() + INTERVAL '15 days',
            'ACTIVE',
            80,
            'BOTH',
            FALSE
        )
        RETURNING id INTO campaign_id;

        INSERT INTO promotion_campaign_item (promotion_id, product_id, role, quantity, promo_price, discount_percent, required, sort_order)
        VALUES
            (campaign_id, p1, 'PRIMARY', 1, NULL, 20, TRUE, 0)
        ON CONFLICT DO NOTHING;

        -- OFERTA 2: Bajaj 15% (featured=false)
        INSERT INTO promotion_campaign (
            code, name, slug, kind, type, title, subtitle, description, badge,
            discount_type, discount_value, starts_at, ends_at, status, priority,
            channel, featured
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
            'PERCENT',
            15,
            NOW(),
            NOW() + INTERVAL '20 days',
            'ACTIVE',
            84,
            'BOTH',
            FALSE
        )
        RETURNING id INTO campaign_id;

        INSERT INTO promotion_campaign_item (promotion_id, product_id, role, quantity, promo_price, discount_percent, required, sort_order)
        VALUES
            (campaign_id, p2, 'PRIMARY', 1, NULL, 15, TRUE, 0),
            (campaign_id, p3, 'ACCESSORY', 1, NULL, 15, TRUE, 1)
        ON CONFLICT DO NOTHING;

        -- OFERTA 3: Cascos 25% (featured=false)
        INSERT INTO promotion_campaign (
            code, name, slug, kind, type, title, subtitle, description, badge,
            discount_type, discount_value, starts_at, ends_at, status, priority,
            channel, featured
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
            'PERCENT',
            25,
            NOW(),
            NOW() + INTERVAL '12 days',
            'ACTIVE',
            79,
            'LANDING',
            FALSE
        )
        RETURNING id INTO campaign_id;

        INSERT INTO promotion_campaign_item (promotion_id, product_id, role, quantity, promo_price, discount_percent, required, sort_order)
        VALUES
            (campaign_id, p5, 'PRIMARY', 1, NULL, 25, TRUE, 0)
        ON CONFLICT DO NOTHING;

    END IF;
END $$;

-- ===============================================================================
-- FIN DEL SCRIPT
-- Próximos pasos en backend:
-- 1. Spring Resource Handler en PromotionImagesWebConfig para servir imágenes
-- 2. PromotionImageFileStorageService para guardar archivos en disco
-- 3. PromotionImagePublicUrlService para convertir KEYs a URLs públicas
-- 4. PromotionImageRestController para endpoint POST de upload
-- ===============================================================================
