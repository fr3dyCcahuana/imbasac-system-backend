-- Read-only: run against the same production database used by the ERP.
-- No passwords, JWTs, refresh tokens, private keys or stored session keys are selected.
BEGIN READ ONLY;
SET LOCAL statement_timeout = '10s';

SELECT current_database() AS base_actual, current_user AS usuario_conexion;

-- 1. Current account policy, administrative quota and restriction.
SELECT u.id AS usuario_id,u.username,r.name::text AS rol,u.enabled AS habilitado,
       p.mode AS politica_del_rol,
       COALESCE((to_jsonb(c)->>'certificate_required')::boolean,false) AS certificado_individual,
       COALESCE(q.device_quota,1) AS cupo_pc,
       (SELECT COUNT(*) FROM security_user_device_binding b WHERE b.user_id=u.id AND b.revoked_at IS NULL) AS pc_asociadas,
       c.strike_count AS contador_guardado,to_jsonb(c)->>'strike_day' AS dia_del_contador,
       c.blocked_until AT TIME ZONE 'America/Lima' AS bloqueo_hasta_lima
FROM users u JOIN roles r ON r.id=u.role_id
LEFT JOIN security_role_device_policy p ON p.role_id=r.id
LEFT JOIN security_user_access_control c ON c.user_id=u.id
LEFT JOIN security_user_device_quota q ON q.user_id=u.id
WHERE lower(u.username)='lenin';

-- 2. Both PC authorizations and their registered public certificates.
SELECT u.username,b.device_id,d.display_name AS computadora,d.status AS estado_pc,
       d.hardware_approved AS hardware_aprobado,
       b.created_at AT TIME ZONE 'America/Lima' AS autorizada_desde,
       b.revoked_at AT TIME ZONE 'America/Lima' AS autorizacion_retirada,
       c.serial_hex AS serie_certificado,c.fingerprint_sha256 AS huella_publica,
       c.not_before AT TIME ZONE 'America/Lima' AS certificado_desde,
       c.expires_at AT TIME ZONE 'America/Lima' AS certificado_hasta,
       c.revoked_at AT TIME ZONE 'America/Lima' AS certificado_revocado
FROM users u JOIN security_user_device_binding b ON b.user_id=u.id
JOIN security_device d ON d.id=b.device_id
LEFT JOIN security_device_certificate c ON c.device_id=d.id
WHERE lower(u.username)='lenin'
ORDER BY b.created_at DESC,c.created_at DESC
LIMIT 30;

-- 3. Actual strikes, correlated with the request that generated each one.
SELECT u.username,s.occurred_at AT TIME ZONE 'America/Lima' AS strike_hora,
       s.reason AS motivo_strike,s.device_id,d.display_name AS pc_observada,
       s.threshold AS umbral,s.blocked_until AT TIME ZONE 'America/Lima' AS bloqueo_generado_hasta,
       e.event_type AS evento,e.reason AS motivo_evento,e.peer_address AS ip,e.user_agent AS navegador
FROM security_access_strike s JOIN users u ON u.id=s.user_id
LEFT JOIN security_device d ON d.id=s.device_id
LEFT JOIN security_access_event e ON e.correlation_id=s.attempt_id
WHERE lower(u.username)='lenin'
  AND s.occurred_at>=TIMESTAMPTZ '2026-11-16 00:00:00-05'
  AND s.occurred_at<TIMESTAMPTZ '2026-11-17 00:00:00-05'
ORDER BY s.occurred_at DESC
LIMIT 30;

-- 4. Login and session history for the incident day.
SELECT e.occurred_at AT TIME ZONE 'America/Lima' AS hora,
       e.last_occurred_at AT TIME ZONE 'America/Lima' AS ultima_repeticion,
       e.event_type AS evento,e.outcome AS resultado,e.username AS identificado,
       e.attempted_username AS cuenta_intentada,e.reason AS motivo,
       e.peer_address AS ip,e.occurrences AS repeticiones
FROM security_access_event e
WHERE (lower(e.username)='lenin' OR lower(e.attempted_username)='lenin')
  AND e.occurred_at>=TIMESTAMPTZ '2026-11-16 00:00:00-05'
  AND e.occurred_at<TIMESTAMPTZ '2026-11-17 00:00:00-05'
  AND e.event_type NOT IN ('AUDIT_VIEWED','AUDIT_EXPORTED','BROWSER_COMPLETED')
ORDER BY e.occurred_at DESC
LIMIT 80;

COMMIT;
