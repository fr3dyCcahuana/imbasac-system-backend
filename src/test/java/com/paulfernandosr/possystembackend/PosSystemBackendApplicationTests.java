package com.paulfernandosr.possystembackend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import javax.sql.DataSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockingDetails;

// Wiring smoke test. Real PostgreSQL/Redis behavior has separate opt-in integration tests.
@SpringBootTest(properties = {
        "spring.config.location=classpath:/application.yml",
        "spring.config.additional-location=",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unused_context_test",
        "spring.datasource.username=unused-test-user",
        "spring.datasource.password=unused-test-password",
        "spring.sql.init.mode=never",
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=1",
        "logging.file.name=",
        "security.token.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "security.access-control.enabled=false",
        "security.access-audit.enabled=false",
        "security.device-policy.enabled=false",
        "security.device-policy.individual-enabled=false",
        "security.device-certificates.enabled=false",
        "security.device-certificates.enrollment-enabled=false",
        "security.ca-signer.enabled=false",
        "security.trusted-proxy.enabled=false",
        "security.sunat-files.enabled=false",
        "security.geoip.enabled=false",
        "integration.mtc-license.assisted.enabled=false",
        "integration.guide-remission.auth.guias-client-id=unused-test-client",
        "integration.guide-remission.auth.guias-client-secret=unused-test-secret",
        "integration.guide-remission.auth.usu-secundario-produccion-user=unused-test-user",
        "integration.guide-remission.auth.usu-secundario-produccion-password=unused-test-password",
        "sale.sunat.username=unused-test-user",
        "sale.sunat.password=unused-test-password",
        "common.hashedPassword=unused-test-hash",
        "app.customer-map.geocoder.auto-job.enabled=false"
})
@ActiveProfiles("build-test")
class PosSystemBackendApplicationTests {

    @MockitoBean private DataSource dataSource;
    @MockitoBean private LettuceConnectionFactory redisConnectionFactory;
    @MockitoBean private TaskScheduler scheduler;
    @Autowired private ApplicationContext context;
    @Autowired private Environment environment;

	@Test
	void contextLoads() {

        assertThat(context.getBean(JdbcClient.class)).isNotNull();
        assertThat(context.getBean(DataSource.class)).isSameAs(dataSource);
        assertThat(mockingDetails(dataSource).isMock()).isTrue();
        assertThat(mockingDetails(redisConnectionFactory).isMock()).isTrue();
        assertThat(mockingDetails(scheduler).isMock()).isTrue();
        assertThat(environment.getActiveProfiles()).containsExactly("build-test");
        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://127.0.0.1:1/unused_context_test");
	}

}
