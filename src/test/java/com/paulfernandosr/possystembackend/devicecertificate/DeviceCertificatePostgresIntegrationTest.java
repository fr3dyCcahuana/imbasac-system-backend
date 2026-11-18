package com.paulfernandosr.possystembackend.devicecertificate;

import com.paulfernandosr.possystembackend.devicecertificate.CertificateModels.*;
import com.paulfernandosr.possystembackend.accessaudit.domain.AccessAuditModels.Metadata;
import com.paulfernandosr.possystembackend.accessaudit.infrastructure.PostgresAccessAuditRepository;
import com.paulfernandosr.possystembackend.accesscontrol.domain.AccessControlException;
import com.paulfernandosr.possystembackend.accesscontrol.application.AccessControlService;
import com.paulfernandosr.possystembackend.accesscontrol.infrastructure.PostgresAccessControlRepository;
import com.paulfernandosr.possystembackend.deviceaccess.application.DeviceAccessPolicyService;
import com.paulfernandosr.possystembackend.deviceaccess.domain.DeviceAccessMode;
import com.paulfernandosr.possystembackend.deviceaccess.domain.DevicePolicyException;
import com.paulfernandosr.possystembackend.deviceaccess.infrastructure.adapter.output.PostgresDevicePolicyRepository;
import com.paulfernandosr.possystembackend.security.domain.AccessSessionId;
import com.paulfernandosr.possystembackend.trustedclient.*;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.role.domain.Role;
import com.paulfernandosr.possystembackend.role.domain.RoleName;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.paulfernandosr.possystembackend.devicecertificate.CertificateException.Code.*;

@EnabledIfEnvironmentVariable(named="CERTIFICATES_TEST_JDBC_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/certificates_test")
class DeviceCertificatePostgresIntegrationTest {
    @TempDir static Path temp;
    private static KeyPair issuerKeys,deviceKeys,otherKeys,weakKeys;
    private static X509Certificate issuer,otherIssuer;
    private static Path issuerFile;
    private DriverManagerDataSource admin;
    private com.zaxxer.hikari.HikariDataSource data;
    private JdbcTemplate jdbc;
    private JdbcClient client;
    private DataSourceTransactionManager transactions;
    private DeviceCertificateService certificates;
    private PostgresAccessControlRepository control;
    private ControlSessionGuard guard;
    private TrustedClientResolver transport;
    private MockHttpServletRequest request;
    private String schema;
    private final User user=User.builder().id(1L).username("cliente_uno")
            .role(Role.builder().id(1L).name(RoleName.CLIENTE).build()).enabled(true).build();
    private final String reason="Revision administrativa de prueba";

    @BeforeAll static void keys() throws Exception {
        issuerKeys=TestCertificates.rsa(3072);deviceKeys=TestCertificates.rsa(3072);otherKeys=TestCertificates.rsa(3072);weakKeys=TestCertificates.rsa(2048);
        issuer=TestCertificates.ca(issuerKeys,"IMBASAC test issuer");otherIssuer=TestCertificates.ca(otherKeys,"Foreign test issuer");
        issuerFile=temp.resolve("issuer-public.pem");Files.writeString(issuerFile,TestCertificates.pem(issuer));
    }
    @BeforeEach void setup() throws Exception {
        String url=System.getenv("CERTIFICATES_TEST_JDBC_URL");
        admin=new DriverManagerDataSource(url,"postgres",System.getenv("CERTIFICATES_TEST_PASSWORD"));
        schema="certificates_test_"+UUID.randomUUID().toString().replace("-","");new JdbcTemplate(admin).execute("CREATE SCHEMA "+schema);
        data=new com.zaxxer.hikari.HikariDataSource();data.setJdbcUrl(url+"?currentSchema="+schema);data.setUsername("postgres");data.setPassword(System.getenv("CERTIFICATES_TEST_PASSWORD"));data.setMaximumPoolSize(12);data.setMinimumIdle(0);
        jdbc=new JdbcTemplate(data);client=JdbcClient.create(data);transactions=new DataSourceTransactionManager(data);
        jdbc.execute("CREATE TABLE roles(id BIGINT PRIMARY KEY,name TEXT);CREATE TABLE users(id BIGINT PRIMARY KEY,username TEXT UNIQUE,role_id BIGINT,enabled BOOLEAN,password TEXT);CREATE TABLE permissions(id BIGSERIAL PRIMARY KEY,name TEXT,domain TEXT,description TEXT)");
        jdbc.execute("INSERT INTO roles VALUES(1,'CLIENTE'),(2,'ADMINISTRATOR');INSERT INTO users VALUES(1,'cliente_uno',1,TRUE,'test-placeholder'),(2,'cliente_dos',1,TRUE,'test-placeholder'),(3,'admin',2,TRUE,'test-placeholder')");
        for(String file:List.of("20261029_device_access_foundation.sql","20261029_access_audit.sql","20261030_access_control.sql","20261030_device_certificates.sql","20261030_ca_revocation_outbox.sql","20261112_daily_access_strikes.sql")) migrate(file);
        transport=mock(TrustedClientResolver.class);when(transport.isEnabled()).thenReturn(true);
        certificates=new DeviceCertificateService(client,transactions,transport,new GeoLocationService(false,""),true,issuerFile.toString());
        control=new PostgresAccessControlRepository(client,transactions);guard=new ControlSessionGuard(client,transactions,transport,true);
        request=new MockHttpServletRequest();RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
    @AfterEach void cleanup() {
        RequestContextHolder.resetRequestAttributes();if(data!=null)data.close();
        if(schema!=null&&schema.matches("certificates_test_[a-f0-9]{32}"))new JdbcTemplate(admin).execute("DROP SCHEMA "+schema+" CASCADE");
    }
    private void migrate(String file) throws Exception { jdbc.execute(new String(new ClassPathResource("db/scripts/"+file).getInputStream().readAllBytes(),StandardCharsets.UTF_8)); }
    private UUID newDevice() { return certificates.createDevice(new NewDevice("Equipo de prueba","WINDOWS",reason),"admin").id(); }
    private X509Certificate leaf(UUID device) throws Exception {return TestCertificates.leaf(deviceKeys,issuer,issuerKeys.getPrivate(),device,true,true);}
    private CertificateRow register(UUID device,X509Certificate certificate) throws Exception {
        certificates.enroll(new RegisterEnrollment(device,TestCertificates.csr(deviceKeys)),"admin");
        return certificates.importCertificate(new ImportCertificate(device,TestCertificates.pem(certificate),reason),"admin");
    }
    private void approve(UUID device) { certificates.hardware(device,new HardwareApproval(true,"homologacion-local-evidencia-001",reason),"admin"); }
    private void presented(UUID device,X509Certificate certificate) {
        when(transport.current()).thenReturn(new TrustedClientContext(true,"198.51.100.12",UUID.randomUUID(),"SUCCESS",certificate));
        if(device==null)request.removeAttribute("imbasac.authorizedDeviceId");else request.setAttribute("imbasac.authorizedDeviceId",device);
    }
    private UUID validSession(UUID device,X509Certificate certificate) throws Exception {
        register(device,certificate);approve(device);control.bind(1,device,"admin",reason);presented(device,certificate);
        UUID refresh=UUID.randomUUID();guard.register(user,refresh);assertThat(guard.active(sid(refresh),user.getUsername(),true)).isTrue();return refresh;
    }
    private static String sid(UUID refresh) {return AccessSessionId.fromRefreshCredential(refresh);}

    @Test void fullMigrationCanBeRepeatedWithoutActivatingOrGrantingPermissions() throws Exception {
        migrate("20261030_device_certificates.sql");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM permissions",Integer.class)).isEqualTo(9);
        assertThat(jdbc.queryForObject("SELECT COUNT(*)-COUNT(DISTINCT name) FROM permissions",Integer.class)).isZero();
        assertThat(certificates.policies()).filteredOn(p->p.roleName().equals("CLIENTE")).extracting(RolePolicy::mode).containsExactly("LEGACY_BROWSER_HASH");
    }
    @Test void disabledCertificatesDoNotReadAnUnconfiguredIssuerFile() {
        var disabled=new DeviceCertificateService(client,transactions,transport,new GeoLocationService(false,""),false,temp.resolve("not-configured-issuer.pem").toString());
        assertThat(disabled.isReady()).isFalse();assertThat(disabled.status().enabled()).isFalse();
        assertCert(()->disabled.createDevice(new NewDevice("No activar","WINDOWS",reason),"admin"),DEVICE_REGISTRATION_DISABLED);
    }
    @Test void deviceRegistrationWorksBeforeConfiguringTheCertificateAuthorityWithoutGrantingAccess() {
        var signer=mock(CertificateSigner.class);
        var registration=new DeviceCertificateService(client,transactions,transport,new GeoLocationService(false,""),false,
                temp.resolve("missing-issuer.pem").toString(),signer,true);
        assertThat(registration.status().registrationEnabled()).isTrue();assertThat(registration.status().enabled()).isFalse();
        var device=registration.createDevice(new NewDevice("PC por preparar","WINDOWS",reason),"admin");
        assertThat(device.status()).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT hardware_approved FROM security_device WHERE id=?",Boolean.class,device.id())).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_certificate_change WHERE device_id=? AND action='DEVICE_REGISTERED'",Integer.class,device.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_user_device_binding",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_device_certificate",Integer.class)).isZero();
        assertCert(()->registration.createDevice(new NewDevice("Celular fuera del alcance","ANDROID",reason),"admin"),CERTIFICATE_REQUEST_INVALID);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_device",Integer.class)).isEqualTo(1);
        assertCert(()->registration.enroll(new RegisterEnrollment(device.id(),"public CSR"),"admin"),CERTIFICATES_DISABLED);
        assertCert(()->registration.issue(UUID.randomUUID(),reason,"admin"),CERTIFICATES_DISABLED);
        verify(signer,never()).sign(any(),any(),any());assertThat(registration.isReady()).isFalse();
    }
    @Test void publicEnrollmentCanBeStoredBeforeConfiguringTheCertificateAuthority() throws Exception {
        var signer=mock(CertificateSigner.class);
        var service=new DeviceCertificateService(client,transactions,transport,new GeoLocationService(false,""),false,
                temp.resolve("missing-issuer.pem").toString(),signer,true,true);
        UUID device=newDevice();
        assertThat(service.status().enrollmentEnabled()).isTrue();
        assertCert(()->service.enroll(new RegisterEnrollment(device,TestCertificates.csr(weakKeys)),"admin"),CERTIFICATE_REQUEST_INVALID);
        Enrollment enrollment=service.enroll(new RegisterEnrollment(device,TestCertificates.csr(deviceKeys)),"admin");
        assertThat(service.enrollments(device)).containsExactly(enrollment);
        assertThat(enrollment.status()).isEqualTo("PENDING");
        assertCert(()->service.issue(enrollment.id(),reason,"admin"),CERTIFICATES_DISABLED);
        verify(signer,never()).sign(any(),any(),any());
        assertThat(service.isReady()).isFalse();
    }
    @Test void certificateFeatureCannotEnableRegistrationWhileAccessControlIsOff() {
        var service=new DeviceCertificateService(client,transactions,transport,new GeoLocationService(false,""),true,issuerFile.toString(),null,false);
        assertThat(service.status().registrationEnabled()).isFalse();
        assertCert(()->service.createDevice(new NewDevice("No registrar","WINDOWS",reason),"admin"),DEVICE_REGISTRATION_DISABLED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_device",Integer.class)).isZero();
    }
    @Test void individualRequirementUsesCertificateOnlyForTheSelectedClient() throws Exception {
        migrate("20261107_individual_device_policy.sql");
        migrate("20261107_individual_device_policy.sql");
        var repository=new PostgresDevicePolicyRepository(client);
        var access=new AccessControlService(control,true,3,60,true,true,certificates);
        var policy=new DeviceAccessPolicyService(repository,null,true,certificates,access,transport);
        org.springframework.test.util.ReflectionTestUtils.setField(policy,"individualEnabled",true);
        var other=User.builder().id(2L).username("cliente_dos").role(user.getRole()).enabled(true).build();
        UUID device=newDevice();
        X509Certificate certificate=leaf(device);
        assertThatThrownBy(()->access.setCertificateRequired(1L,true,"admin",reason)).isInstanceOf(AccessControlException.class);
        register(device,certificate); approve(device); control.bind(1,device,"admin",reason);
        UUID oldSession=UUID.randomUUID(), otherSession=UUID.randomUUID();
        guard.register(user,oldSession);
        guard.register(other,otherSession);
        access.setCertificateRequired(1L,true,"admin",reason);
        access.setCertificateRequired(1L,true,"admin",reason);
        assertThat(access.certificateRequired(1L)).isTrue();
        assertThat(guard.active(sid(oldSession),user.getUsername(),true)).isFalse();
        assertThat(guard.active(sid(otherSession),other.getUsername(),true)).isTrue();
        assertThat(repository.findForUser(2L,1L)).contains(DeviceAccessMode.LEGACY_BROWSER_HASH);
        assertThat(repository.findByRole(1L,RoleName.CLIENTE)).contains(DeviceAccessMode.LEGACY_BROWSER_HASH);
        when(transport.current()).thenReturn(new TrustedClientContext(true,"198.51.100.12",UUID.randomUUID(),"NONE",null));
        assertThatThrownBy(()->policy.check(user)).isInstanceOf(DevicePolicyException.class);
        policy.check(other);
        presented(null,certificate);
        policy.check(user);
        assertThat(request.getAttribute("imbasac.authorizedDeviceId")).isEqualTo(device);
        assertThat(control.history(1L,0,25).items()).filteredOn(change->change.eventType().equals("INDIVIDUAL_DEVICE_POLICY_CHANGED")).hasSize(1);
        access.setCertificateRequired(1L,false,"admin",reason);
        assertThat(repository.findForUser(1L,1L)).contains(DeviceAccessMode.LEGACY_BROWSER_HASH);
    }
    @Test void dailyStrikeResetDoesNotInvalidateTheAuthorizedPcSession() throws Exception {
        UUID device=newDevice(); X509Certificate certificate=leaf(device); UUID session=validSession(device,certificate);
        var nextMidnight=Instant.now().atZone(java.time.ZoneId.of("America/Lima")).toLocalDate().plusDays(1)
                .atStartOfDay(java.time.ZoneId.of("America/Lima")).toInstant();
        var moment=new java.util.concurrent.atomic.AtomicReference<>(nextMidnight.minusSeconds(10));
        var clock=mock(java.time.Clock.class); when(clock.instant()).thenAnswer(invocation->moment.get());
        var dailyStore=new PostgresAccessControlRepository(client,transactions,clock);
        dailyStore.recordUnauthorizedDevice(1L,null,UUID.randomUUID(),3,"a".repeat(64),60);
        UUID epoch=jdbc.queryForObject("SELECT session_epoch FROM security_user_access_control WHERE user_id=1",UUID.class);
        moment.set(nextMidnight);
        assertThat(dailyStore.checkAccount(1L).strikes()).isZero();
        assertThat(jdbc.queryForObject("SELECT session_epoch FROM security_user_access_control WHERE user_id=1",UUID.class)).isEqualTo(epoch);
        assertThat(guard.active(sid(session),user.getUsername(),true)).isTrue();
        assertThat(dailyStore.strikes(1L,0,25).total()).isEqualTo(1);
    }
    @Test void invalidWeakAndIncorrectlySignedCsrsNeverCreateEnrollment() throws Exception {
        UUID device=newDevice();
        assertCert(()->certificates.enroll(new RegisterEnrollment(device,"not a CSR"),"admin"),CERTIFICATE_REQUEST_INVALID);
        String weak=TestCertificates.csr(weakKeys),wrong=TestCertificates.csr(deviceKeys,otherKeys.getPrivate());
        assertCert(()->certificates.enroll(new RegisterEnrollment(device,weak),"admin"),CERTIFICATE_REQUEST_INVALID);
        assertCert(()->certificates.enroll(new RegisterEnrollment(device,wrong),"admin"),CERTIFICATE_REQUEST_INVALID);
        assertThat(certificates.enrollments(device)).isEmpty();
    }
    @ParameterizedTest
    @EnumSource(RoleName.class)
    void individualCertificateRequirementProtectsEveryRoleWithoutAffectingOtherAccounts(RoleName role) throws Exception {
        migrate("20261107_individual_device_policy.sql");
        long roleId=10L;
        String inherited=role==RoleName.CLIENTE?"LEGACY_BROWSER_HASH":"PASSWORD_ONLY";
        jdbc.update("INSERT INTO roles(id,name) VALUES(?,?)",roleId,role.name());
        jdbc.update("INSERT INTO security_role_device_policy(role_id,mode) VALUES(?,?)",roleId,inherited);
        jdbc.update("UPDATE users SET role_id=? WHERE id=3",roleId);
        jdbc.update("INSERT INTO users VALUES(4,'other_same_role',?,TRUE,'test-placeholder')",roleId);
        Role targetRole=Role.builder().id(roleId).name(role).build();
        var target=User.builder().id(3L).username("admin").role(targetRole).enabled(true)
                .status(com.paulfernandosr.possystembackend.user.domain.UserStatus.ON_REGISTER).build();
        var other=User.builder().id(4L).username("other_same_role").role(targetRole).enabled(true).build();
        var policies=new PostgresDevicePolicyRepository(client);
        var access=new AccessControlService(control,true,3,60,true,true,certificates);
        var policy=new DeviceAccessPolicyService(policies,null,true,certificates,access,transport);
        org.springframework.test.util.ReflectionTestUtils.setField(policy,"individualEnabled",true);
        UUID device=newDevice(); X509Certificate certificate=leaf(device);
        register(device,certificate); approve(device); control.bind(3,device,"admin",reason);
        control.bind(4,device,"admin",reason);
        UUID oldTarget=UUID.randomUUID(),otherSession=UUID.randomUUID();
        guard.register(target,oldTarget); guard.register(other,otherSession);
        access.setCertificateRequired(3L,true,"admin",reason);
        assertThat(guard.active(sid(oldTarget),"admin",true)).isFalse();
        assertThat(guard.active(sid(otherSession),"other_same_role",true)).isTrue();
        assertThat(policies.findForUser(3L,roleId)).contains(DeviceAccessMode.CERTIFICATE_REQUIRED);
        assertThat(policies.findForUser(4L,roleId)).contains(DeviceAccessMode.valueOf(inherited));
        assertThat(policies.findByRole(roleId,role)).contains(DeviceAccessMode.valueOf(inherited));
        assertThat(policies.findForUser(3L,1L)).isEmpty();

        var credentials=mock(com.paulfernandosr.possystembackend.security.domain.port.output.AuthenticationPort.class);
        var tokens=mock(com.paulfernandosr.possystembackend.security.domain.port.output.TokenGenerationPort.class);
        var sessions=mock(com.paulfernandosr.possystembackend.security.domain.port.output.SessionRepository.class);
        var users=mock(com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository.class);
        when(users.findByUsername("admin")).thenReturn(Optional.of(target));
        var login=new com.paulfernandosr.possystembackend.security.application.LoginService(credentials,tokens,sessions,users,policy,guard);
        when(transport.current()).thenReturn(new TrustedClientContext(true,"198.51.100.12",UUID.randomUUID(),"NONE",null));
        assertThatThrownBy(()->login.login("admin","incorrect-test-password"))
                .isInstanceOf(com.paulfernandosr.possystembackend.security.domain.exception.InvalidCredentialsException.class);
        assertThat(control.strikes(3L,0,25).total()).isZero();
        when(credentials.authenticate("admin","correct-test-password")).thenReturn(true);
        assertThatThrownBy(()->login.login("admin","correct-test-password"))
                .isInstanceOfSatisfying(AccessControlException.class,e->assertThat(e.getCode())
                        .isEqualTo(AccessControlException.Code.ACCESS_DEVICE_NOT_AUTHORIZED));
        assertThat(control.strikes(3L,0,25).total()).isEqualTo(1);
        verifyNoInteractions(tokens,sessions);
        policy.check(other);
        assertThat(guard.active(sid(otherSession),"other_same_role",true)).isTrue();

        presented(null,certificate);
        var accepted=login.login("admin","correct-test-password");
        verify(sessions).create(accepted);
        assertThat(guard.active(sid(accepted.getId()),"admin",true)).isTrue();
        assertThat(control.strikes(3L,0,25).total()).isEqualTo(1);

        UUID foreignDevice=newDevice(); X509Certificate foreignCertificate=leaf(foreignDevice);
        register(foreignDevice,foreignCertificate); approve(foreignDevice);
        presented(null,foreignCertificate);
        assertThatThrownBy(()->login.login("admin","correct-test-password")).isInstanceOf(AccessControlException.class);
        assertThat(control.strikes(3L,0,25).total()).isEqualTo(2);
        assertThat(control.strikes(4L,0,25).total()).isZero();
        assertThat(guard.active(sid(otherSession),"other_same_role",true)).isTrue();

        access.setCertificateRequired(3L,false,"admin",reason);
        assertThat(policies.findForUser(3L,roleId)).contains(DeviceAccessMode.valueOf(inherited));
        assertThat(guard.active(sid(accepted.getId()),"admin",false)).isFalse();
        jdbc.update("UPDATE security_role_device_policy SET mode='CERTIFICATE_REQUIRED' WHERE role_id=?",roleId);
        assertThat(policies.findForUser(3L,roleId)).contains(DeviceAccessMode.CERTIFICATE_REQUIRED);
        when(transport.current()).thenReturn(new TrustedClientContext(true,"198.51.100.12",UUID.randomUUID(),"NONE",null));
        request.removeAttribute("imbasac.authorizedDeviceId");
        assertThatThrownBy(()->policy.check(target)).isInstanceOf(DevicePolicyException.class);
    }
    @Test void newCertificateRequestPemLabelCanEnrollAndImportTheMatchingPublicCertificate() throws Exception {
        UUID device=newDevice();String pem=TestCertificates.csr(deviceKeys).replace("CERTIFICATE REQUEST","NEW CERTIFICATE REQUEST");
        var enrollment=certificates.enroll(new RegisterEnrollment(device,pem),"admin");
        assertThat(enrollment.csrPem()).isEqualTo(pem);assertThat(enrollment.publicKeySha256()).matches("[a-f0-9]{64}");
        X509Certificate certificate=leaf(device);
        var imported=certificates.importCertificate(new ImportCertificate(device,TestCertificates.pem(certificate),reason),"admin");
        assertThat(imported.deviceId()).isEqualTo(device);assertThat(imported.hardwareApproved()).isFalse();
    }
    @Test void importedCertificateRequiresMatchingEnrollmentKeyAndDeviceIdentity() throws Exception {
        UUID device=newDevice(),other=newDevice();X509Certificate certificate=leaf(device);
        assertCert(()->certificates.importCertificate(new ImportCertificate(device,TestCertificates.pem(certificate),reason),"admin"),CERTIFICATE_ENROLLMENT_REQUIRED);
        certificates.enroll(new RegisterEnrollment(device,TestCertificates.csr(otherKeys)),"admin");
        assertCert(()->certificates.importCertificate(new ImportCertificate(device,TestCertificates.pem(certificate),reason),"admin"),CERTIFICATE_ENROLLMENT_REQUIRED);
        assertCert(()->certificates.importCertificate(new ImportCertificate(other,TestCertificates.pem(certificate),reason),"admin"),CERTIFICATE_INVALID);
        register(device,certificate);assertThat(certificates.certificates(device)).hasSize(1);
    }
    @Test void wrongIssuerMissingDeviceSanAndWrongEkuAreRejected() throws Exception {
        UUID device=newDevice();
        X509Certificate foreign=TestCertificates.leaf(deviceKeys,otherIssuer,otherKeys.getPrivate(),device,true,true);
        X509Certificate noSan=TestCertificates.leaf(deviceKeys,issuer,issuerKeys.getPrivate(),device,true,false);
        X509Certificate wrongEku=TestCertificates.leaf(deviceKeys,issuer,issuerKeys.getPrivate(),device,false,true);
        X509Certificate weak=TestCertificates.leaf(weakKeys,issuer,issuerKeys.getPrivate(),device,true,true);
        assertCert(()->certificates.validateCertificate(foreign),CERTIFICATE_INVALID);
        assertCert(()->certificates.validateCertificate(noSan),CERTIFICATE_INVALID);
        assertCert(()->certificates.validateCertificate(wrongEku),CERTIFICATE_INVALID);
        assertCert(()->certificates.validateCertificate(weak),CERTIFICATE_INVALID);
    }
    @Test void explicitlyAllowedP256CsrAndRsaSignedCertificateUseTheSameEnrollmentControls() throws Exception {
        UUID device=newDevice();KeyPair ec=TestCertificates.ec("secp256r1");
        X509Certificate certificate=TestCertificates.leaf(ec,issuer,issuerKeys.getPrivate(),device,true,true);
        certificates.enroll(new RegisterEnrollment(device,TestCertificates.csr(ec)),"admin");
        certificates.importCertificate(new ImportCertificate(device,TestCertificates.pem(certificate),reason),"admin");
        approve(device);control.bind(1,device,"admin",reason);presented(device,certificate);
        assertThat(certificates.presentedDevice()).isEqualTo(device);
        UUID refresh=UUID.randomUUID();guard.register(user,refresh);assertThat(guard.active(sid(refresh),user.getUsername(),true)).isTrue();
    }
    @Test void unapprovedEcCurveCannotEnterThroughCsrOrCertificate() throws Exception {
        UUID device=newDevice();KeyPair ec=TestCertificates.ec("secp384r1");
        String csr=TestCertificates.csr(ec);X509Certificate certificate=TestCertificates.leaf(ec,issuer,issuerKeys.getPrivate(),device,true,true);
        assertCert(()->certificates.enroll(new RegisterEnrollment(device,csr),"admin"),CERTIFICATE_REQUEST_INVALID);
        assertCert(()->certificates.validateCertificate(certificate),CERTIFICATE_INVALID);
    }
    @Test void certificateImportDoesNotAutomaticallyApproveHardwareOrAuthorizeUsers() throws Exception {
        UUID device=newDevice();X509Certificate certificate=leaf(device);register(device,certificate);presented(device,certificate);
        assertCert(()->certificates.presentedDevice(),CERTIFICATE_DEVICE_NOT_AUTHORIZED);
        assertThat(control.isDeviceAuthorized(1,device)).isFalse();approve(device);
        assertThat(certificates.presentedDevice()).isEqualTo(device);assertThat(control.isDeviceAuthorized(1,device)).isFalse();
        control.bind(1,device,"admin",reason);assertThat(control.isDeviceAuthorized(1,device)).isTrue();
    }
    @Test void ownAndForeignCertificateClassificationIsBasedOnTheIndividualBinding() throws Exception {
        UUID device=newDevice();X509Certificate certificate=leaf(device);register(device,certificate);approve(device);control.bind(1,device,"admin",reason);
        assertThat(certificates.isRegisteredForUser(1,certificate)).isTrue();assertThat(certificates.isRegisteredForUser(2,certificate)).isFalse();
        assertThat(certificates.isRegisteredForUser(1,null)).isFalse();
    }
    @Test void absentOrUntrustedCertificateTransportCannotPresentADevice() throws Exception {
        UUID device=newDevice();X509Certificate certificate=leaf(device);register(device,certificate);approve(device);
        when(transport.current()).thenReturn(new TrustedClientContext(false,"198.51.100.12",UUID.randomUUID(),"SUCCESS",certificate));
        assertCert(()->certificates.presentedDevice(),CERTIFICATE_DEVICE_NOT_AUTHORIZED);
        assertThat(request.getAttribute("imbasac.auditFailureReason")).isEqualTo("CERTIFICATE_TRANSPORT_UNTRUSTED");
        when(transport.current()).thenReturn(new TrustedClientContext(true,"198.51.100.12",UUID.randomUUID(),"NONE",null));
        assertCert(()->certificates.presentedDevice(),CERTIFICATE_DEVICE_NOT_AUTHORIZED);
        assertThat(request.getAttribute("imbasac.auditFailureReason")).isEqualTo("CERTIFICATE_MISSING");
    }
    @Test void revokedCertificateDeniesPresentedDeviceAndExactExistingSessionImmediately() throws Exception {
        UUID device=newDevice();X509Certificate certificate=leaf(device);UUID refresh=validSession(device,certificate);
        UUID id=certificates.certificates(device).getFirst().id();certificates.revoke(id,"admin",reason);
        assertCert(()->certificates.presentedDevice(),CERTIFICATE_DEVICE_NOT_AUTHORIZED);
        assertThat(request.getAttribute("imbasac.auditFailureReason")).isEqualTo("CERTIFICATE_REVOKED");
        assertThat(guard.active(sid(refresh),user.getUsername(),true)).isFalse();
        assertThatThrownBy(()->guard.checkRefresh(refresh,user.getUsername())).isInstanceOf(AccessControlException.class);
        certificates.revoke(id,"admin",reason);assertThat(certificates.certificates(device).getFirst().revokedAt()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_ca_revocation_outbox WHERE certificate_id=? AND status='PENDING'",Integer.class,id)).isEqualTo(1);
    }
    @Test void outboxInsertFailureRollsBackTheWholeLocalRevocation() throws Exception {
        UUID device=newDevice();X509Certificate certificate=leaf(device);CertificateRow registered=register(device,certificate);
        jdbc.execute("ALTER TABLE security_ca_revocation_outbox ADD CONSTRAINT test_reject_outbox CHECK(FALSE)");
        assertCert(()->certificates.revoke(registered.id(),"admin",reason),CERTIFICATE_UNAVAILABLE);
        assertThat(certificates.certificates(device).getFirst().revokedAt()).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_ca_revocation_outbox",Integer.class)).isZero();
    }
    @Test void aLegacyCertificateWithoutPublicPemIsRevokedLocallyAndQueuedForReview() throws Exception {
        UUID device=newDevice();X509Certificate certificate=leaf(device);CertificateRow registered=register(device,certificate);
        jdbc.update("UPDATE security_device_certificate SET public_certificate_pem=NULL WHERE id=?",registered.id());
        certificates.revoke(registered.id(),"admin",reason);
        assertThat(certificates.certificates(device).getFirst().revokedAt()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT status FROM security_ca_revocation_outbox WHERE certificate_id=?",String.class,registered.id())).isEqualTo("NEEDS_REVIEW");
        assertThat(jdbc.queryForObject("SELECT last_error_code FROM security_ca_revocation_outbox WHERE certificate_id=?",String.class,registered.id())).isEqualTo("CERTIFICATE_PEM_UNAVAILABLE");
    }
    @Test void exactSessionUserCertificateAndDeviceAreRequired() throws Exception {
        UUID device=newDevice();X509Certificate certificate=leaf(device);UUID refresh=validSession(device,certificate);
        assertThat(guard.active(sid(UUID.randomUUID()),user.getUsername(),true)).isFalse();assertThat(guard.active(sid(refresh),"cliente_dos",true)).isFalse();
        X509Certificate renewed=leaf(device);presented(device,renewed);assertThat(guard.active(sid(refresh),user.getUsername(),true)).isFalse();
        presented(UUID.randomUUID(),certificate);assertThat(guard.active(sid(refresh),user.getUsername(),true)).isFalse();
    }
    @Test void unboundDeviceCannotRegisterEvenIfPolicyWasCheckedBeforeTheConcurrentUnbind() throws Exception {
        UUID device=newDevice();X509Certificate certificate=leaf(device);register(device,certificate);approve(device);presented(device,certificate);
        assertThatThrownBy(()->guard.register(user,UUID.randomUUID())).isInstanceOf(AccessControlException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_control_session",Integer.class)).isZero();
    }
    @Test void unbindThenRebindSamePcDoesNotReviveTheOldSession() throws Exception {
        UUID device=newDevice();X509Certificate certificate=leaf(device);UUID refresh=validSession(device,certificate);
        control.unbind(1,control.bindings(1).getFirst().id(),"admin",reason);assertThat(guard.active(sid(refresh),user.getUsername(),true)).isFalse();
        control.bind(1,device,"admin",reason);assertThat(guard.active(sid(refresh),user.getUsername(),true)).isFalse();
        UUID current=UUID.randomUUID();guard.register(user,current);assertThat(guard.active(sid(current),user.getUsername(),true)).isTrue();
    }
    @Test void hardwareWithdrawalAndReapprovalCannotReviveOldCredentials() throws Exception {
        UUID device=newDevice();X509Certificate certificate=leaf(device);UUID refresh=validSession(device,certificate);
        certificates.hardware(device,new HardwareApproval(false,null,reason),"admin");approve(device);
        assertThat(guard.active(sid(refresh),user.getUsername(),true)).isFalse();
    }
    @Test void administratorUnblockAndAutomaticExpiryDoNotReviveOldSessions() throws Exception {
        UUID device=newDevice();X509Certificate certificate=leaf(device);UUID refresh=validSession(device,certificate);
        for(int i=0;i<3;i++)control.recordUnauthorizedDevice(1,null,UUID.randomUUID(),3,Integer.toHexString(i).repeat(64),60);
        assertThat(guard.active(sid(refresh),user.getUsername(),true)).isFalse();control.unblock(1,"admin",reason);
        assertThat(guard.active(sid(refresh),user.getUsername(),true)).isFalse();
        UUID newer=UUID.randomUUID();guard.register(user,newer);
        for(int i=0;i<3;i++)control.recordUnauthorizedDevice(1,null,UUID.randomUUID(),3,Integer.toHexString(i).repeat(64),60);
        jdbc.update("UPDATE security_user_access_control SET blocked_until=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE user_id=1");control.checkAccount(1);
        assertThat(guard.active(sid(newer),user.getUsername(),true)).isFalse();
    }
    @Test void passwordRoleAndEnabledChangesInvalidateOldSessionsPermanently() throws Exception {
        UUID device=newDevice();X509Certificate certificate=leaf(device);UUID refresh=validSession(device,certificate);
        jdbc.update("UPDATE users SET password='changed-test-placeholder' WHERE id=1");assertThat(guard.active(sid(refresh),user.getUsername(),true)).isFalse();
        refresh=UUID.randomUUID();guard.register(user,refresh);jdbc.update("UPDATE users SET enabled=FALSE WHERE id=1");jdbc.update("UPDATE users SET enabled=TRUE WHERE id=1");assertThat(guard.active(sid(refresh),user.getUsername(),true)).isFalse();
        refresh=UUID.randomUUID();guard.register(user,refresh);jdbc.update("UPDATE users SET role_id=2 WHERE id=1");jdbc.update("UPDATE users SET role_id=1 WHERE id=1");assertThat(guard.active(sid(refresh),user.getUsername(),true)).isFalse();
    }
    @Test void staleCredentialSnapshotCannotRegisterAfterPasswordOrRoleChanges() throws Exception {
        UUID device=newDevice();X509Certificate certificate=leaf(device);register(device,certificate);approve(device);control.bind(1,device,"admin",reason);presented(device,certificate);
        var beforePassword=guard.capture(user.getUsername());
        jdbc.update("UPDATE users SET password='changed-before-session-test-placeholder' WHERE id=1");
        assertThatThrownBy(()->guard.register(user,UUID.randomUUID(),beforePassword)).isInstanceOf(AccessControlException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_control_session",Integer.class)).isZero();
        var beforeRole=guard.capture(user.getUsername());jdbc.update("UPDATE users SET role_id=2 WHERE id=1");
        assertThatThrownBy(()->guard.register(user,UUID.randomUUID(),beforeRole)).isInstanceOf(AccessControlException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_control_session",Integer.class)).isZero();
    }
    @Test void rolePolicyChangeAndExemptToRequiredContextRejectTheOldSession() throws Exception {
        UUID refresh=UUID.randomUUID();guard.register(user,refresh);assertThat(guard.active(sid(refresh),user.getUsername(),true)).isTrue();
        UUID device=newDevice();X509Certificate certificate=leaf(device);register(device,certificate);approve(device);control.bind(1,device,"admin",reason);presented(device,certificate);
        assertThat(guard.active(sid(refresh),user.getUsername(),true)).isFalse();
        certificates.policy(1,new PolicyChange("CERTIFICATE_REQUIRED",reason),"admin");assertThat(guard.active(sid(refresh),user.getUsername(),false)).isFalse();
    }
    @Test void lostControlOrCertificateStateFailsClosed() throws Exception {
        UUID device=newDevice();X509Certificate certificate=leaf(device);UUID refresh=validSession(device,certificate);
        jdbc.execute("DROP TABLE security_control_session");
        assertThatThrownBy(()->guard.active(sid(refresh),user.getUsername(),true)).isInstanceOfSatisfying(AccessControlException.class,e->assertThat(e.getCode()).isEqualTo(AccessControlException.Code.ACCESS_CONTROL_UNAVAILABLE));
        jdbc.execute("ALTER TABLE security_device_certificate RENAME TO test_hidden_certificate_state");
        assertCert(()->certificates.presentedDevice(),CERTIFICATE_UNAVAILABLE);
    }
    @Test void eventGeolocationIsAnImmutableSnapshotAndNeverCreatesStrikes() {
        UUID correlation=UUID.randomUUID();Instant observed=Instant.now();
        var location=new GeoLocationService.Location("ESTIMATED","PE","La Libertad","Trujillo",50,"TEST_FIXTURE","test-version-1",observed);
        var audit=new PostgresAccessAuditRepository(client);audit.record("LOGIN_REJECTED","REJECTED","BACKEND","cliente_uno",null,null,null,"TEST_LOCATION",new Metadata("198.51.100.12","Test Browser",correlation,location),false);
        UUID event=jdbc.queryForObject("SELECT id FROM security_access_event WHERE correlation_id=?",UUID.class,correlation);
        var saved=certificates.eventLocation(event);assertThat(saved.city()).isEqualTo("Trujillo");assertThat(saved.accuracyRadiusKm()).isEqualTo(50);assertThat(saved.databaseVersion()).isEqualTo("test-version-1");
        var newer=new GeoLocationService.Location("ESTIMATED","PE","Lima","Lima",10,"TEST_FIXTURE","test-version-2",Instant.now());
        audit.record("LOGIN_REJECTED","REJECTED","BACKEND","cliente_uno",null,null,null,"TEST_LOCATION_RETRY",new Metadata("198.51.100.12","Test Browser",correlation,newer),false);
        assertThat(certificates.eventLocation(event).city()).isEqualTo("Trujillo");
        assertThat(certificates.eventLocation(event).databaseVersion()).isEqualTo("test-version-1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_access_strike",Integer.class)).isZero();
    }
    private static void assertCert(org.assertj.core.api.ThrowableAssert.ThrowingCallable action,CertificateException.Code code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(CertificateException.class,e->assertThat(e.getCode()).isEqualTo(code));
    }
    @Test void administrativeIssuanceImportsPublicCertificateWithoutApprovingHardwareAndRetriesAreStable() throws Exception {
        UUID device=newDevice();var enrollment=certificates.enroll(new RegisterEnrollment(device,TestCertificates.csr(deviceKeys)),"admin");
        var signer=mock(CertificateSigner.class);when(signer.isConfigured()).thenReturn(true);
        String pem=TestCertificates.pem(leaf(device));when(signer.sign(enrollment.id(),device,enrollment.csrPem())).thenReturn(pem);
        var issuing=new DeviceCertificateService(client,transactions,transport,new GeoLocationService(false,""),true,issuerFile.toString(),signer);
        var first=issuing.issue(enrollment.id(),reason,"admin");var again=issuing.issue(enrollment.id(),reason,"admin");
        assertThat(again.id()).isEqualTo(first.id());assertThat(first.hardwareApproved()).isFalse();
        assertThat(issuing.download(first.id())).isEqualTo(pem).doesNotContain("PRIVATE KEY");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_device_certificate",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_user_device_binding",Integer.class)).isZero();
    }
    @Test void signerCannotImportCertificateForDifferentDeviceOrHideIssuerFailure() throws Exception {
        UUID device=newDevice();var enrollment=certificates.enroll(new RegisterEnrollment(device,TestCertificates.csr(deviceKeys)),"admin");
        var signer=mock(CertificateSigner.class);when(signer.isConfigured()).thenReturn(true);
        when(signer.sign(enrollment.id(),device,enrollment.csrPem())).thenReturn(TestCertificates.pem(leaf(UUID.randomUUID())));
        var issuing=new DeviceCertificateService(client,transactions,transport,new GeoLocationService(false,""),true,issuerFile.toString(),signer);
        assertCert(()->issuing.issue(enrollment.id(),reason,"admin"),CERTIFICATE_INVALID);
        when(signer.sign(enrollment.id(),device,enrollment.csrPem())).thenThrow(new CertificateException(CERTIFICATE_SIGNER_UNAVAILABLE));
        assertCert(()->issuing.issue(enrollment.id(),reason,"admin"),CERTIFICATE_SIGNER_UNAVAILABLE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_device_certificate",Integer.class)).isZero();
    }
    @Test void disabledIssuerNeverStartsExternalCommandAndPrivatePathsAreNotClientInputs() {
        var signer=new ExternalCaSigner(false,new com.fasterxml.jackson.databind.ObjectMapper());
        assertCert(()->signer.sign(UUID.randomUUID(),UUID.randomUUID(),"public csr"),CERTIFICATE_SIGNER_UNAVAILABLE);
        assertThat(signer.isConfigured()).isFalse();
    }
}
