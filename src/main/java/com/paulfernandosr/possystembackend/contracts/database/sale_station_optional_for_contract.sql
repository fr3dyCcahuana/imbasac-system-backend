BEGIN;

-- Permitir ventas generadas desde contrato sin caja/estación.
-- La FK se mantiene: si station_id tiene valor, debe existir en stations; si es NULL, no valida.
ALTER TABLE sale
  ALTER COLUMN station_id DROP NOT NULL;

-- Asegurar numeración global, sin depender de station_id.
ALTER TABLE sale
  DROP CONSTRAINT IF EXISTS uq_sale_doc;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.table_constraints
    WHERE table_name = 'sale'
      AND constraint_name = 'uq_sale_doc_global'
  ) THEN
    ALTER TABLE sale
      ADD CONSTRAINT uq_sale_doc_global UNIQUE (doc_type, series, number);
  END IF;
END $$;

-- Asegurar serie SIMPLE global para ventas internas desde contrato.
INSERT INTO document_series (station_id, doc_type, series, next_number, enabled)
VALUES (NULL, 'SIMPLE', 'S001', 1, TRUE)
ON CONFLICT (doc_type, series) DO NOTHING;

COMMIT;
