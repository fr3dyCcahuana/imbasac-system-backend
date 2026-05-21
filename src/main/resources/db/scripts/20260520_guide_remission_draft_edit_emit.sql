-- ============================================================
-- PATCH: guias de remision con guardado local, edicion y emision separada
-- PostgreSQL
-- Ejecutar antes de desplegar el backend actualizado.
-- ============================================================

BEGIN;

CREATE INDEX IF NOT EXISTS ix_guide_remissions_business_status
  ON guide_remissions(company_ruc, serie, numero, status);

COMMIT;
