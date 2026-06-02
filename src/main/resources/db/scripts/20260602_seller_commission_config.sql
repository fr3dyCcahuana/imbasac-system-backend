-- Configuracion persistente de meta mensual y tasas de comision por vendedor.
-- Si un usuario no tiene fila, la aplicacion usa estos mismos valores por defecto.

CREATE TABLE IF NOT EXISTS seller_commission_config (
    seller_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    monthly_goal NUMERIC(14, 2) NOT NULL DEFAULT 50000.00,
    bajaj_rate NUMERIC(10, 6) NOT NULL DEFAULT 0.005000 CHECK (bajaj_rate >= 0),
    ktm_rate NUMERIC(10, 6) NOT NULL DEFAULT 0.003000 CHECK (ktm_rate >= 0),
    imba_rate NUMERIC(10, 6) NOT NULL DEFAULT 0.000200 CHECK (imba_rate >= 0),
    bon_rate NUMERIC(10, 6) NOT NULL DEFAULT 0.000200 CHECK (bon_rate >= 0),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

ALTER TABLE seller_commission_config
    DROP CONSTRAINT IF EXISTS seller_commission_config_monthly_goal_check;

ALTER TABLE seller_commission_config
    DROP CONSTRAINT IF EXISTS ck_seller_commission_config_monthly_goal;

ALTER TABLE seller_commission_config
    ADD CONSTRAINT ck_seller_commission_config_monthly_goal CHECK (monthly_goal > 0);

COMMENT ON TABLE seller_commission_config IS
    'Meta mensual y tasas de comision personalizadas por vendedor.';
