package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.sunat;

import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.apisnet.JuridicalPerson;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.config.CustomerLookupProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SunatRucScraperTest {
    private static final String RUC = "20616704142";
    private static final MediaType HTML = MediaType.parseMediaType("text/html;charset=UTF-8");
    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://e-consultaruc.sunat.gob.pe");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final SunatRucScraper scraper = new SunatRucScraper(builder.build(),
            mock(SunatAddressParser.class), new CustomerLookupProperties());

    @ParameterizedTest
    @ValueSource(strings = {"NINGUNO", "NO REGISTRA", "NO EXISTE"})
    void keepsValidRucWhenAnOptionalSectionHasNoData(String optionalValue) {
        setupSearch(result(RUC, optionalValue));
        Optional<JuridicalPerson> person = lookup();
        assertThat(person).isPresent();
        assertThat(person.orElseThrow().getBusinessName()).isEqualTo("LIDEM SUR S.A.C.");
        assertThat(person.orElseThrow().getStatus()).isEqualTo("ACTIVO");
        assertThat(person.orElseThrow().getCondition()).isEqualTo("HABIDO");
        server.verify(); // A valid POST result must not be replaced by a GET fallback.
    }

    @ParameterizedTest
    @ValueSource(strings = {"<h1>Servicio no disponible</h1>",
            "<p>Ingrese el código mostrado (captcha)</p>", "",
            "<p>20616704142 - Error al procesar la solicitud</p>"})
    void unknownResponsesAreUnavailableRatherThanMissing(String html) {
        setupSearchWithFallback(html);
        assertUnavailable();
        server.verify();
    }

    @Test
    void rejectsAResultForAnotherRuc() {
        setupSearchWithFallback(result("20100070970", "NINGUNO"));
        assertUnavailable();
        server.verify();
    }

    @Test
    void onlyAnExplicitNegativeResultIsMissing() {
        setupSearchWithFallback("<p>El número de RUC ingresado no existe.</p>");
        assertThat(lookup()).isEmpty();
        server.verify();
    }

    @Test
    void upstreamHttpFailureIsUnavailable() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertUnavailable();
        server.verify();
    }

    private void assertUnavailable() {
        assertThatThrownBy(this::lookup).isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private Optional<JuridicalPerson> lookup() {
        // Exercise the HTTP fallback without launching a browser or calling SUNAT/DB.
        return ReflectionTestUtils.invokeMethod(scraper, "findByRucWithRestClient", RUC);
    }

    private void setupSearch(String html) {
        server.expect(anything()).andRespond(withSuccess("<html>Consulta RUC</html>", HTML));
        server.expect(anything()).andRespond(withSuccess("1234", MediaType.TEXT_PLAIN));
        server.expect(anything()).andRespond(withSuccess(html, HTML));
    }

    private void setupSearchWithFallback(String html) {
        setupSearch(html);
        server.expect(anything()).andRespond(withSuccess(html, HTML));
    }

    private String result(String ruc, String optionalValue) {
        return """
                <h3>Resultado de la Búsqueda</h3><table>
                <tr><td>Número de RUC:</td><td>%s - LIDEM SUR S.A.C.</td></tr>
                <tr><td>Tipo Contribuyente:</td><td>SOCIEDAD ANONIMA CERRADA</td></tr>
                <tr><td>Estado del Contribuyente:</td><td>ACTIVO</td></tr>
                <tr><td>Condición del Contribuyente:</td><td>HABIDO</td></tr>
                <tr><td>Comprobantes de Pago:</td><td>%s</td></tr></table>
                """.formatted(ruc, optionalValue);
    }
}
