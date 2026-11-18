-- Run by the operator in the actual ERP database, never an assumed "postgres" database.
-- Presence checks are inventory, not permission grants or proof of all migration definitions.
BEGIN READ ONLY;
SET LOCAL statement_timeout='5s';
SELECT current_database() AS database_name,current_schema() AS schema_name;
SELECT name,to_regclass(name) IS NOT NULL AS present FROM (VALUES
 ('security_role_device_policy'),('security_device'),('security_user_device_binding'),
 ('security_device_certificate'),('security_device_enrollment'),('security_access_session'),
 ('security_access_event'),('security_access_location'),('security_control_session'),
 ('security_user_device_quota'),('security_user_access_control'),('security_access_strike'),
 ('security_ca_revocation_outbox'),('sale'),('credit_note'),('contract_sunat_draft')
) AS required(name);
SELECT e.table_name,e.column_name,c.udt_name AS found_type,c.column_name IS NOT NULL AS present
FROM (VALUES ('security_device','hardware_approved'),('security_device','hardware_evidence_reference'),
 ('security_device_certificate','public_certificate_pem'),('security_user_access_control','session_epoch'),
 ('security_control_session','binding_id'),('security_control_session','certificate_fingerprint'),
 ('users','password'),('users','role_id'),('users','enabled')) AS e(table_name,column_name)
LEFT JOIN information_schema.columns c ON c.table_schema=current_schema() AND c.table_name=e.table_name AND c.column_name=e.column_name;
SELECT name,COUNT(*) AS catalog_entries FROM permissions
WHERE name IN ('VIEW_SECURITY_AUDIT','EXPORT_SECURITY_AUDIT','REVOKE_SECURITY_SESSIONS',
 'VIEW_SECURITY_CONTROL','MANAGE_SECURITY_DEVICES','MANAGE_SECURITY_BLOCKS',
 'MANAGE_SECURITY_HARDWARE','MANAGE_SECURITY_POLICIES','ISSUE_SECURITY_CERTIFICATES','VIEW_SUNAT_DOCUMENTS')
GROUP BY name ORDER BY name;
SELECT tablename,indexname,indexdef FROM pg_indexes WHERE schemaname=current_schema()
AND tablename IN ('security_user_device_binding','security_user_device_quota','security_control_session');
COMMIT;
