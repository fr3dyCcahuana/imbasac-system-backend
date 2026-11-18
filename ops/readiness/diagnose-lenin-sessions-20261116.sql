-- Read-only follow-up for LENIN. One result set; no session keys or tokens.
BEGIN READ ONLY;
SET LOCAL statement_timeout = '10s';

WITH account AS (
    SELECT id FROM users WHERE username = 'LENIN'
), timeline AS (
    SELECT s.started_at AS occurred_at,
           'SESION_ACEPTADA'::text AS tipo,
           d.display_name::text AS computadora,
           cert.serial_hex::text AS serie_certificado,
           s.state::text AS estado,
           concat('Fin: ', COALESCE((s.ended_at AT TIME ZONE 'America/Lima')::text, 'sin cierre'),
                  '; motivo: ', COALESCE(s.end_reason, 'sin motivo de cierre')) AS detalle
    FROM security_access_session s
    JOIN account u ON u.id = s.user_id
    LEFT JOIN security_control_session cs
           ON cs.access_session_id = s.session_key AND cs.user_id = s.user_id
    LEFT JOIN security_device d ON d.id = cs.device_id
    LEFT JOIN security_device_certificate cert
           ON cert.device_id = cs.device_id
          AND cert.fingerprint_sha256 = cs.certificate_fingerprint
    WHERE s.started_at >= TIMESTAMPTZ '2026-11-16 00:00:00-05'
      AND s.started_at < TIMESTAMPTZ '2026-11-17 00:00:00-05'

    UNION ALL

    SELECT c.occurred_at,
           c.event_type::text,
           NULL::text,
           NULL::text,
           COALESCE(c.actor_username, 'SISTEMA')::text,
           concat(c.reason, '; ', c.details)
    FROM security_access_control_change c
    JOIN account u ON u.id = c.user_id
    WHERE c.occurred_at >= TIMESTAMPTZ '2026-11-16 00:00:00-05'
      AND c.occurred_at < TIMESTAMPTZ '2026-11-17 00:00:00-05'
)
SELECT occurred_at AT TIME ZONE 'America/Lima' AS hora_lima,
       tipo, computadora, serie_certificado, estado, detalle
FROM timeline
ORDER BY occurred_at DESC, tipo
LIMIT 80;

COMMIT;
