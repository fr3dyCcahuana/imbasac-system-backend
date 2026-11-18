package com.paulfernandosr.possystembackend.devicecertificate;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CaRevocationOutboxTest {
    @Test void disabledWorkerDoesNotReadTablesOrCallTheSigner() {
        var jdbc=mock(JdbcClient.class);var transactions=mock(PlatformTransactionManager.class);var signer=mock(CertificateSigner.class);
        var outbox=new CaRevocationOutbox(jdbc,transactions,signer,false);outbox.runOnce();outbox.scheduledTick();outbox.close();verifyNoInteractions(jdbc,transactions,signer);
    }
    @Test void unconfiguredSignerDoesNotClaimJobs() {
        var jdbc=mock(JdbcClient.class);var transactions=mock(PlatformTransactionManager.class);var signer=mock(CertificateSigner.class);
        new CaRevocationOutbox(jdbc,transactions,signer,true).runOnce();verify(signer).isConfigured();verifyNoInteractions(jdbc,transactions);
    }
    @Test void deliveryCanBeDisabledSeparatelyAndOtherwiseInheritsTheSignerFlag() {
        var jdbc=mock(JdbcClient.class);var transactions=mock(PlatformTransactionManager.class);var signer=mock(CertificateSigner.class);
        runWithProperties(jdbc,transactions,signer,Map.of("security.ca-signer.enabled",true,"security.ca-signer.revocation-delivery-enabled",false));
        verifyNoInteractions(jdbc,transactions,signer);
        runWithProperties(jdbc,transactions,signer,Map.of("security.ca-signer.enabled",true));
        verify(signer).isConfigured();verifyNoInteractions(jdbc,transactions);
    }
    private static void runWithProperties(JdbcClient jdbc,PlatformTransactionManager transactions,CertificateSigner signer,Map<String,Object> properties) {
        try(var context=new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test-signer",properties));
            context.registerBean(JdbcClient.class,()->jdbc);
            context.registerBean(PlatformTransactionManager.class,()->transactions);
            context.registerBean(CertificateSigner.class,()->signer);
            context.register(CaRevocationOutbox.class);
            context.refresh();
            context.getBean(CaRevocationOutbox.class).runOnce();
        }
    }
    @Test void enqueueRejectsPrivateOrOversizedMaterialBeforeDatabaseCalls() {
        var jdbc=mock(JdbcClient.class);var signer=mock(CertificateSigner.class);var outbox=new CaRevocationOutbox(jdbc,mock(PlatformTransactionManager.class),signer,false);
        assertThatIllegalArgumentException().isThrownBy(()->outbox.enqueue(UUID.randomUUID(),UUID.randomUUID(),"-----BEGIN PRIVATE KEY-----"+"x".repeat(100)));
        assertThatIllegalArgumentException().isThrownBy(()->outbox.enqueue(UUID.randomUUID(),UUID.randomUUID(),"-----BEGIN CERTIFICATE-----"+"x".repeat(16000)));
        verifyNoInteractions(jdbc,signer);
    }
    @Test void oldSignerImplementationsCannotReportSuccessFromTheDefaultMethod() {
        CertificateSigner signer=new CertificateSigner() {
            public boolean isConfigured(){return true;}
            public String sign(UUID request,UUID device,String csr){throw new UnsupportedOperationException();}
        };
        assertThatThrownBy(()->signer.revoke(UUID.randomUUID(),UUID.randomUUID(),"public certificate"))
                .isInstanceOfSatisfying(CertificateException.class,e->assertThat(e.getCode()).isEqualTo(CertificateException.Code.CERTIFICATE_SIGNER_UNAVAILABLE));
    }
}
