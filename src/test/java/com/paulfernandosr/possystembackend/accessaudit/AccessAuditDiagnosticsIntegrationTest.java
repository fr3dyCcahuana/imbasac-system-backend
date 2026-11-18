package com.paulfernandosr.possystembackend.accessaudit;

import com.paulfernandosr.possystembackend.accessaudit.application.AccessAuditService;
import com.paulfernandosr.possystembackend.accessaudit.domain.*;
import com.paulfernandosr.possystembackend.accessaudit.domain.AccessAuditModels.*;
import com.paulfernandosr.possystembackend.accessaudit.infrastructure.PostgresAccessAuditRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="ACCESS_DIAGNOSTICS_TEST_JDBC_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/access_diagnostics_test")
class AccessAuditDiagnosticsIntegrationTest {
    private JdbcTemplate admin,jdbc;
    private String schema;
    private PostgresAccessAuditRepository store;
    private Metadata metadata(String state,String fingerprint) {
        return new Metadata("192.0.2.10","Test browser",UUID.randomUUID(),null,
                new AuthenticationContext(state,fingerprint,fingerprint==null?null:"1234",null,null,"CERTIFICATE_MISSING"));
    }
    @BeforeEach void setup() throws Exception {
        String url=System.getenv("ACCESS_DIAGNOSTICS_TEST_JDBC_URL");
        String password=System.getenv("ACCESS_DIAGNOSTICS_TEST_PASSWORD");
        admin=new JdbcTemplate(new DriverManagerDataSource(url,"postgres",password));
        schema="audit_diag_"+UUID.randomUUID().toString().replace("-","");
        admin.execute("CREATE SCHEMA "+schema);
        var data=new DriverManagerDataSource(url+"?currentSchema="+schema,"postgres",password);
        jdbc=new JdbcTemplate(data);
        jdbc.execute("CREATE TABLE roles(id BIGINT PRIMARY KEY,name TEXT); CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT,role_id BIGINT,enabled BOOLEAN); CREATE TABLE permissions(id BIGSERIAL PRIMARY KEY,name TEXT,domain TEXT,description TEXT)");
        jdbc.execute("INSERT INTO roles VALUES(1,'ADMINISTRATOR'); INSERT INTO users VALUES(1,'admin',1,true)");
        migrate("20261029_access_audit.sql");
        store=new PostgresAccessAuditRepository(JdbcClient.create(data));
    }
    @AfterEach void cleanup() {
        if(schema!=null && schema.matches("audit_diag_[a-f0-9]{32}")) admin.execute("DROP SCHEMA "+schema+" CASCADE");
    }
    private void migrate(String file) throws Exception {
        jdbc.execute(new String(new ClassPathResource("db/scripts/"+file).getInputStream().readAllBytes(),StandardCharsets.UTF_8));
    }
    private void enableContext() throws Exception {
        migrate("20261116_access_audit_diagnostics.sql");
        ReflectionTestUtils.setField(store,"schemaCheckedAt",0L);
    }
    private AuditQuery query(String category) {
        var today=LocalDate.now(AuditQuery.LIMA);
        return AuditQuery.of(today,today,null,null,null,null,0,25,category);
    }
    private void rejected(String reason,Metadata meta) {
        store.record("LOGIN_REJECTED","REJECTED","BACKEND",null,"unknown-user",null,null,reason,meta,true);
    }
    @Test void oldSchemaKeepsLoginAndQueriesWorkingWithoutInventingCertificateEvidence() {
        assertThat(store.authenticationContextReady()).isFalse();
        store.start("a".repeat(64),"admin",300,metadata("NONE",null));
        rejected("CERTIFICATE_MISSING",metadata("NONE",null));
        var page=store.events(query("CERTIFICATE"),25,0);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items().getFirst().authentication()).isNull();
        assertThat(page.items().getFirst().username()).isNull();
        assertThat(page.items().getFirst().attemptedUsername()).isEqualTo("unknown-user");
        assertThat(store.event(page.items().getFirst().id()).orElseThrow().category()).isEqualTo("CERTIFICATE");
        assertThat(store.summary(query("CERTIFICATE")).rejectedAttempts()).isEqualTo(1);
    }
    @Test void migrationIsRepeatableAndStoresMissingCertificateSnapshot() throws Exception {
        enableContext(); enableContext();
        assertThat(store.authenticationContextReady()).isTrue();
        rejected("CERTIFICATE_MISSING",metadata("NONE",null));
        var event=store.events(query("CERTIFICATE"),25,0).items().getFirst();
        assertThat(event.authentication().certificateState()).isEqualTo("NONE");
        assertThat(event.authentication().fingerprintSha256()).isNull();
        assertThat(event.authentication().deviceId()).isNull();
        assertThat(store.event(event.id()).orElseThrow().authentication()).isEqualTo(event.authentication());
        assertThat(jdbc.queryForObject("SELECT authentication_context::text FROM security_access_event",String.class))
                .doesNotContain("password","token","privateKey");
    }
    @Test void differentCertificateContextsDoNotCollapseIntoTheSameIncidentAndPcNameIsASnapshot() throws Exception {
        enableContext();
        jdbc.execute("CREATE TABLE security_device(id UUID PRIMARY KEY,display_name TEXT); CREATE TABLE security_device_certificate(device_id UUID,fingerprint_sha256 TEXT)");
        UUID device=UUID.randomUUID(); String fingerprint="a".repeat(64);
        jdbc.update("INSERT INTO security_device VALUES(?,?)",device,"PC original");
        jdbc.update("INSERT INTO security_device_certificate VALUES(?,?)",device,fingerprint);
        rejected("ACCESS_DEVICE_NOT_AUTHORIZED",metadata("NONE",null));
        rejected("ACCESS_DEVICE_NOT_AUTHORIZED",metadata("NONE",null));
        rejected("ACCESS_DEVICE_NOT_AUTHORIZED",metadata("SUCCESS",fingerprint));
        jdbc.update("UPDATE security_device SET display_name='PC renombrada'");
        var page=store.events(query("DEVICE"),25,0);
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).anySatisfy(e->{
            assertThat(e.authentication().deviceId()).isEqualTo(device);
            assertThat(e.authentication().deviceName()).isEqualTo("PC original");
            assertThat(e.authentication().fingerprintSha256()).isEqualTo(fingerprint);
        });
        assertThat(page.items()).anySatisfy(e->{ assertThat(e.occurrences()).isEqualTo(2); assertThat(e.authentication().certificateState()).isEqualTo("NONE"); });
    }
    @Test void sessionRejectionKeepsVerifiedUsernameAndExplainsReplacementWithoutRecordingAnotherLogin() {
        var meta=new Metadata("192.0.2.10","Test",UUID.randomUUID());
        String old="a".repeat(64), fresh="b".repeat(64);
        store.start(old,"admin",300,meta); store.start(fresh,"admin",300,meta);
        var audit=new AccessAuditService(store,()->meta,true,(key,user)->{});
        audit.denied("SESSION_REJECTED","admin",old,"SESSION_NOT_ACTIVE",meta);
        var denied=store.events(query("SESSION"),25,0).items().stream().filter(e->e.eventType().equals("SESSION_REJECTED")).findFirst().orElseThrow();
        assertThat(denied.reason()).isEqualTo("SESSION_REPLACED");
        assertThat(denied.username()).isEqualTo("admin");
        assertThat(denied.attemptedUsername()).isNull();
        assertThat(denied.sessionId()).isNotNull();
        assertThat(store.summary(query(null)).grantedLogins()).isEqualTo(2);
    }
    @Test void filteredExportIncludesDiagnosticColumnsWithoutAnyPasswordOrSessionCredential() throws Exception {
        enableContext(); var meta=metadata("NONE",null);
        rejected("CERTIFICATE_MISSING",meta);
        var audit=new AccessAuditService(store,()->meta,true,(key,user)->{});
        String csv=new String(audit.export(query("CERTIFICATE"),"admin"),StandardCharsets.UTF_8);
        assertThat(csv).contains("Categoría","Certificado","unknown-user","Estado del certificado","NONE");
        assertThat(csv).doesNotContain("password","Contraseña","session_key","accessToken","refreshToken");
        assertThat(csv.lines().toList()).hasSize(2);
        assertThat(csv.lines().toList().getFirst().split(";",-1)).hasSize(21);
        assertThat(csv.lines().toList().get(1).split(";",-1)).hasSize(21);
    }
}
