package com.paulfernandosr.possystembackend.deviceaccess.infrastructure;
import com.paulfernandosr.possystembackend.deviceaccess.domain.DeviceAccessMode;
import com.paulfernandosr.possystembackend.deviceaccess.infrastructure.adapter.output.PostgresDevicePolicyRepository;
import com.paulfernandosr.possystembackend.role.domain.RoleName;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="DEVICE_ACCESS_TEST_JDBC_URL", matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/device_access_test")
class PostgresDeviceAccessFoundationIntegrationTest {
    private DriverManagerDataSource admin;
    private JdbcTemplate jdbc;
    private DriverManagerDataSource data;
    private String schema;
    private PostgresDevicePolicyRepository policies;
    @BeforeEach void setup() {
        String url=System.getenv("DEVICE_ACCESS_TEST_JDBC_URL");
        admin=new DriverManagerDataSource(url,"postgres",System.getenv("DEVICE_ACCESS_TEST_PASSWORD"));
        schema="device_test_"+UUID.randomUUID().toString().replace("-","");
        new JdbcTemplate(admin).execute("CREATE SCHEMA "+schema);
        data=new DriverManagerDataSource(url+"?currentSchema="+schema,"postgres",System.getenv("DEVICE_ACCESS_TEST_PASSWORD"));
        jdbc=new JdbcTemplate(data);
        jdbc.execute("CREATE TABLE roles (id BIGINT PRIMARY KEY,name TEXT NOT NULL UNIQUE); CREATE TABLE users (id BIGINT PRIMARY KEY)");
        jdbc.execute("INSERT INTO roles VALUES (1,'ADMINISTRATOR'),(2,'CASHIER'),(3,'WHOLESALER'),(4,'WAREHOUSE'),(5,'CLIENTE'),(6,'UNKNOWN'); INSERT INTO users VALUES(7),(8)");
        migrate(); policies=new PostgresDevicePolicyRepository(JdbcClient.create(data));
    }
    @AfterEach void cleanup() {
        if(schema!=null && schema.matches("device_test_[a-f0-9]{32}")) new JdbcTemplate(admin).execute("DROP SCHEMA "+schema+" CASCADE");
    }
    @Test void seedsCompatibilityAndDoesNotExemptUnknownRole() {
        assertThat(policies.findByRole(5L,RoleName.CLIENTE)).contains(DeviceAccessMode.LEGACY_BROWSER_HASH);
        assertThat(policies.findByRole(1L,RoleName.ADMINISTRATOR)).contains(DeviceAccessMode.PASSWORD_ONLY);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_role_device_policy WHERE role_id=6",Integer.class)).isZero();
        assertThat(policies.findByRole(1L,RoleName.CLIENTE)).isEmpty();
    }
    @Test void rerunningMigrationNeverDowngradesAnExistingCertificatePolicy() {
        jdbc.update("UPDATE security_role_device_policy SET mode='CERTIFICATE_REQUIRED' WHERE role_id=5");
        migrate(); assertThat(policies.findByRole(5L,RoleName.CLIENTE)).contains(DeviceAccessMode.CERTIFICATE_REQUIRED);
    }
    @Test void sharedPcAllowsSeveralUsersButEachUserHasOnlyOneActivePc() {
        UUID first=device(),second=device();binding(7,first);binding(8,first);
        assertThatThrownBy(() -> binding(7,second)).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_user_device_binding",Integer.class)).isEqualTo(2);
    }
    @Test void revokedAssignmentDoesNotPreventAnExplicitNewAssignment() {
        UUID first=device(),second=device();binding(7,first);
        jdbc.update("UPDATE security_user_device_binding SET revoked_at=CURRENT_TIMESTAMP WHERE user_id=7");
        binding(7,second);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_user_device_binding WHERE revoked_at IS NULL AND user_id=7",Integer.class)).isEqualTo(1);
    }
    @Test void concurrentAssignmentsCannotAuthorizeTwoPcsForOneUser() throws Exception {
        UUID first=device(),second=device();
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> a=() -> tryBinding(first), b=() -> tryBinding(second);
            var results=pool.invokeAll(List.of(a,b));
            int successful=0; for(var result:results) if(result.get()) successful++;
            assertThat(successful).isEqualTo(1);
        }
    }
    @Test void certificateOwnershipAndValidityAreConstrainedWithoutStoringPrivateKeys() {
        UUID device=device();
        jdbc.update("INSERT INTO security_device_certificate(id,device_id,kind,fingerprint_sha256,issuer_id,serial_hex,not_before,expires_at) VALUES(?,?,'DEVICE',?,'issuer','01',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+INTERVAL '1 day')",UUID.randomUUID(),device,"a".repeat(64));
        assertThatThrownBy(() -> jdbc.update("INSERT INTO security_device_certificate(id,device_id,kind,owner_user_id,fingerprint_sha256,issuer_id,serial_hex,not_before,expires_at) VALUES(?,?,'DEVICE',7,?,'issuer','02',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+INTERVAL '1 day')",UUID.randomUUID(),device,"b".repeat(64)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO security_device_certificate(id,device_id,kind,fingerprint_sha256,issuer_id,serial_hex,not_before,expires_at) VALUES(?,?,'USER_DEVICE',?,'issuer','03',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+INTERVAL '1 day')",UUID.randomUUID(),device,"c".repeat(64)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO security_device_certificate(id,device_id,kind,fingerprint_sha256,issuer_id,serial_hex,not_before,expires_at) VALUES(?,?,'DEVICE',?,'issuer','04',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP-INTERVAL '1 day')",UUID.randomUUID(),device,"d".repeat(64)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void mobilesAndInvalidModesAreRejected() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO security_device(id,display_name,platform) VALUES(?,'Phone','ANDROID')",UUID.randomUUID()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE security_role_device_policy SET mode='UNKNOWN' WHERE role_id=5"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    private boolean tryBinding(UUID device) {
        try { binding(7,device); return true; } catch(org.springframework.dao.DuplicateKeyException exception) { return false; }
    }
    private UUID device() {
        UUID id=UUID.randomUUID();jdbc.update("INSERT INTO security_device(id,display_name,platform,status) VALUES(?,'Test PC','WINDOWS','ACTIVE')",id);return id;
    }
    private void binding(long user,UUID device) {
        jdbc.update("INSERT INTO security_user_device_binding(id,user_id,device_id) VALUES(?,?,?)",UUID.randomUUID(),user,device);
    }
    private void migrate() {
        new ResourceDatabasePopulator(new ClassPathResource("db/scripts/20261029_device_access_foundation.sql")).execute(data);
    }
}
