package com.paulfernandosr.possystembackend.devicecertificate;
import com.paulfernandosr.possystembackend.devicecertificate.CertificateModels.*;
import com.paulfernandosr.possystembackend.trustedclient.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.UUID;
import java.io.StringReader;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CertificatePublicInputTest {
    @TempDir static Path temp;
    private static KeyPair keys;private static X509Certificate issuer;private static Path bundle;
    private final JdbcClient jdbc=mock(JdbcClient.class);
    @BeforeAll static void fixture() throws Exception {
        keys=TestCertificates.rsa(3072);issuer=TestCertificates.ca(keys,"Public input test issuer");bundle=temp.resolve("public-ca.pem");Files.writeString(bundle,TestCertificates.pem(issuer));
    }
    private DeviceCertificateService service() { return service(true,bundle.toString()); }
    private DeviceCertificateService service(boolean enabled,String issuerPath) {
        return new DeviceCertificateService(jdbc,mock(PlatformTransactionManager.class),mock(TrustedClientResolver.class),new GeoLocationService(false,""),enabled,issuerPath);
    }
    @Test void issuerBundleStartupReportsSafeAndDistinctConfigurationFailures() throws Exception {
        Path empty=Files.createFile(temp.resolve("empty-issuer.pem"));
        Path malformed=Files.writeString(temp.resolve("malformed-issuer.pem"),"-----BEGIN CERTIFICATE-----\ninvalid-secret$\n-----END CERTIFICATE-----\n");
        assertIssuerFailure(" ","path is not configured");
        assertIssuerFailure("invalid-secret\0path","path is invalid (InvalidPathException)");
        assertIssuerFailure(temp.resolve("missing-issuer.pem").toString(),"cannot be read (NoSuchFileException)");
        assertIssuerFailure(empty.toString(),"contains no X.509 certificates");
        assertIssuerFailure(malformed.toString(),"cannot be parsed as X.509 (CertificateException)");
        assertThat(service(false,"invalid-secret\0path").isReady()).isFalse();
        verifyNoInteractions(jdbc);
    }
    private void assertIssuerFailure(String issuerPath,String detail) {
        assertThatThrownBy(()->service(true,issuerPath)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Device certificate issuer bundle "+detail).hasNoCause();
    }
    @Test void certificateWithTrailingPrivateKeyOrTextIsRejectedBeforeAnyDatabaseCall() throws Exception {
        UUID device=UUID.randomUUID();String certificate=TestCertificates.pem(TestCertificates.leaf(keys,issuer,keys.getPrivate(),device,true,true));
        for(String appended:new String[]{"accidental secret text",TestCertificates.pem(keys.getPrivate())})
            assertThatThrownBy(()->service().importCertificate(new ImportCertificate(device,certificate+appended,"Importar certificado publico"),"admin"))
                    .isInstanceOfSatisfying(CertificateException.class,e->assertThat(e.getCode()).isEqualTo(CertificateException.Code.CERTIFICATE_INVALID));
        verifyNoInteractions(jdbc);
    }
    @Test void csrWithTrailingTextOrKeyIsRejectedBeforeItCanBePersisted() throws Exception {
        String csr=TestCertificates.csr(keys);
        for(String label:new String[]{csr,csr.replace("CERTIFICATE REQUEST","NEW CERTIFICATE REQUEST")})
            for(String appended:new String[]{"accidental secret text",TestCertificates.pem(keys.getPrivate())})
                assertThatThrownBy(()->service().enroll(new RegisterEnrollment(UUID.randomUUID(),label+appended),"admin"))
                        .isInstanceOfSatisfying(CertificateException.class,e->assertThat(e.getCode()).isEqualTo(CertificateException.Code.CERTIFICATE_REQUEST_INVALID));
        verifyNoInteractions(jdbc);
    }
    @Test void newCertificateRequestLabelUsesTheRealParserAndPreservesSignatureValidation() throws Exception {
        String pem=TestCertificates.csr(keys).replace("CERTIFICATE REQUEST","NEW CERTIFICATE REQUEST");
        try(var parser=new PEMParser(new StringReader(pem))) {
            Object parsed=parser.readObject();assertThat(parsed).isInstanceOf(PKCS10CertificationRequest.class);
            assertThat(((PKCS10CertificationRequest)parsed).isSignatureValid(new JcaContentVerifierProviderBuilder().build(keys.getPublic()))).isTrue();
            assertThat(parser.readObject()).isNull();
        }
        verifyNoInteractions(jdbc);
    }
}
