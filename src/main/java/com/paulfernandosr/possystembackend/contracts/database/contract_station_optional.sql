-- Permite registrar contratos sin estación/caja.
-- La FK se mantiene: si station_id tiene valor, debe existir en stations; si es NULL, PostgreSQL no valida la FK.

ALTER TABLE contract
ALTER COLUMN station_id DROP NOT NULL;

-- Opcional: limpiar contratos de prueba con station_id inválido antes de quitar NOT NULL.
-- UPDATE contract c
-- SET station_id = NULL
-- WHERE station_id IS NOT NULL
--   AND NOT EXISTS (SELECT 1 FROM stations s WHERE s.id = c.station_id);
