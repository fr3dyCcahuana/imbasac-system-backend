-- Emergency console recovery for one enabled administrator, not a bulk unlock.
-- psql requires: -v ON_ERROR_STOP=1 -v target_user=admin
-- Compatible with both the earlier and the daily-strike schema.
BEGIN;
SET LOCAL lock_timeout = '3s';
SET LOCAL statement_timeout = '10s';

SELECT u.id AS cuenta_a_revisar
FROM users u JOIN roles r ON r.id=u.role_id
WHERE u.username=:'target_user' AND r.name::text='ADMINISTRATOR' AND u.enabled=TRUE
FOR UPDATE OF u;

WITH released AS (
    UPDATE security_user_access_control c
    SET strike_count=0, blocked_until=NULL, cycle_id=gen_random_uuid(),
        session_epoch=gen_random_uuid(), updated_at=CURRENT_TIMESTAMP
    FROM users u JOIN roles r ON r.id=u.role_id
    WHERE c.user_id=u.id AND u.username=:'target_user'
      AND r.name::text='ADMINISTRATOR' AND u.enabled=TRUE
      AND c.blocked_until>CURRENT_TIMESTAMP
    RETURNING c.user_id,c.cycle_id
), recorded AS (
    INSERT INTO security_access_control_change
        (id,user_id,occurred_at,event_type,actor_user_id,actor_username,reason,details)
    SELECT gen_random_uuid(),user_id,CURRENT_TIMESTAMP,'ACCOUNT_RELEASED',NULL,
        'HOSTINGER_ROOT_CONSOLE','Desbloqueo administrativo solicitado y ejecutado desde consola del VPS',
        'newCycleId='||cycle_id::text
    FROM released RETURNING user_id
)
SELECT u.username,'DESBLOQUEADO' AS resultado
FROM recorded r JOIN users u ON u.id=r.user_id;

COMMIT;
