package com.paulfernandosr.possystembackend.customer.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.input.CustomerMapRestController;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.input.dto.CustomerMapDtos.CustomerAddressGeolocationDetailResponse;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.input.dto.CustomerMapDtos.GeocodingSuggestionResponse;
import com.paulfernandosr.possystembackend.customer.infrastructure.config.CustomerMapProperties;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Uses only a local HTTP stub: no customer database or public geocoder is contacted. */
class CustomerMapGeocoderPerformanceTest {
    private static final String MATCH = """
            [{"lat":"-12.05","lon":"-77.04","display_name":"Test street, Peru","type":"house"}]
            """;
    private final AtomicInteger requests = new AtomicInteger();
    private final List<URI> requestUris = Collections.synchronizedList(new ArrayList<>());
    private final AtomicReference<Responder> responder = new AtomicReference<>();
    private final JdbcClient jdbc = mock(JdbcClient.class);
    private HttpServer server;
    private ExecutorService serverThreads;
    private CustomerMapProperties properties;
    private CustomerMapService service;

    @BeforeEach
    void setUp() throws IOException {
        responder.set(exchange -> respond(exchange, 200, MATCH));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverThreads = Executors.newCachedThreadPool();
        server.setExecutor(serverThreads);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            requestUris.add(exchange.getRequestURI());
            try {
                responder.get().respond(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
        properties = new CustomerMapProperties();
        properties.getGeocoder().setEnabled(true);
        properties.getGeocoder().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.getGeocoder().setRateLimitPerMinute(60);
        properties.getGeocoder().setTimeoutMs(5000);
        service = spy(new CustomerMapService(jdbc, mock(UserRepository.class),
                new CustomerAddressHashService(), properties, new ObjectMapper()));
        doReturn(detail("TEST STREET 10", null)).when(service).detail(4L, 1225L);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        serverThreads.shutdownNow();
    }

    @Test
    void repeatingAProposalAvoidsTheProvidersSimulatedTwoPointNineSecondDelay() {
        responder.set(exchange -> {
            try {
                Thread.sleep(2900);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, MATCH);
        });
        long firstStarted = System.nanoTime();
        GeocodingSuggestionResponse first = service.suggest(4L, 1225L);
        long firstMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - firstStarted);
        long cachedStarted = System.nanoTime();
        GeocodingSuggestionResponse repeated = service.suggest(4L, 1225L);
        long cachedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - cachedStarted);

        assertThat(first.isAccepted()).isTrue();
        assertThat(repeated.getLatitude()).isEqualTo(first.getLatitude());
        assertThat(requests.get()).isEqualTo(1);
        verify(service, times(2)).detail(4L, 1225L);
        verifyNoInteractions(jdbc);
        System.out.printf("Local geocoder benchmark (upstream delay 2900ms): first=%dms, cached=%dms, upstreamRequests=%d%n",
                firstMs, cachedMs, requests.get());
    }

    @Test
    void emptyResultsAdvanceToTheNextDistinctQueryWithoutRetryAndAreCached() {
        responder.set(exchange -> respond(exchange, 200, "[]"));
        assertThat(service.suggest(4L, 1225L).isAccepted()).isFalse();
        assertThat(requests.get()).isEqualTo(2);
        assertThat(requestUris).doesNotHaveDuplicates();

        assertThat(service.suggest(4L, 1225L).isAccepted()).isFalse();
        assertThat(requests.get()).isEqualTo(2);
    }

    @Test
    void cachedProviderResultsAreRevalidatedAgainstTheCurrentUbigeo() {
        doReturn(detail("TEST STREET 10", "150101")).when(service).detail(4L, 1225L);
        assertThat(service.suggest(4L, 1225L).isAccepted()).isTrue();
        doReturn(detail("TEST STREET 10", "040101")).when(service).detail(4L, 1225L);

        GeocodingSuggestionResponse changed = service.suggest(4L, 1225L);

        assertThat(changed.isAccepted()).isFalse();
        assertThat(changed.getRejectionReason()).contains("fuera");
        assertThat(requests.get()).isEqualTo(2);
        verifyNoInteractions(jdbc);
    }

    @Test
    void changingTheAddressMakesANewProviderQuery() {
        service.suggest(4L, 1225L);
        doReturn(detail("OTHER STREET 20", null)).when(service).detail(4L, 1225L);
        service.suggest(4L, 1225L);
        assertThat(requests.get()).isEqualTo(2);
        assertThat(requestUris.get(0)).isNotEqualTo(requestUris.get(1));
    }

    @Test
    void changingTheProviderUrlDoesNotReuseThePreviousProvidersCache() {
        service.suggest(4L, 1225L);
        properties.getGeocoder().setBaseUrl(properties.getGeocoder().getBaseUrl() + "/another-provider");
        service.suggest(4L, 1225L);
        assertThat(requests.get()).isEqualTo(2);
        assertThat(requestUris.get(1).getPath()).isEqualTo("/another-provider/search");
    }

    @Test
    void expiredProposalsAreFetchedAgain() throws InterruptedException {
        properties.getGeocoder().setCacheTtlSeconds(1);
        service.suggest(4L, 1225L);
        Thread.sleep(1100);
        service.suggest(4L, 1225L);
        assertThat(requests.get()).isEqualTo(2);
    }

    @Test
    void cacheEvictsOldQueriesWhenItsConfiguredCapacityIsReached() {
        properties.getGeocoder().setCacheMaxEntries(1);
        service.suggest(4L, 1225L);
        doReturn(detail("OTHER STREET 20", null)).when(service).detail(4L, 1225L);
        service.suggest(4L, 1225L);
        doReturn(detail("TEST STREET 10", null)).when(service).detail(4L, 1225L);
        service.suggest(4L, 1225L);
        assertThat(requests.get()).isEqualTo(3);
    }

    @Test
    void simultaneousIdenticalRequestsShareTheExternalLookup() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch detailsRead = new CountDownLatch(2);
        responder.set(exchange -> {
            started.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, MATCH);
        });
        doAnswer(invocation -> {
            detailsRead.countDown();
            return detail("TEST STREET 10", null);
        }).when(service).detail(4L, 1225L);

        try (ExecutorService callers = Executors.newFixedThreadPool(2)) {
            var first = callers.submit(() -> service.suggest(4L, 1225L));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            var second = callers.submit(() -> service.suggest(4L, 1225L));
            try {
                assertThat(detailsRead.await(5, TimeUnit.SECONDS)).isTrue();
            } finally {
                release.countDown();
            }
            assertThat(first.get(5, TimeUnit.SECONDS).isAccepted()).isTrue();
            assertThat(second.get(5, TimeUnit.SECONDS).isAccepted()).isTrue();
            assertThat(requests.get()).isEqualTo(1);
        } finally {
            release.countDown();
        }
    }

    @Test
    void aProviderFailureIsNotCachedOrRepeatedForEveryAddressVariant() {
        properties.getGeocoder().setMaxRetries(0);
        responder.set(exchange -> respond(exchange, 503, "{}"));
        assertBadGateway();
        assertThat(requests.get()).isEqualTo(1);
        responder.set(exchange -> respond(exchange, 200, MATCH));

        assertThat(service.suggest(4L, 1225L).isAccepted()).isTrue();
        assertThat(requests.get()).isEqualTo(2);
    }

    @Test
    void transientProviderFailureCanBeRetriedWithinTheConfiguredLimit() {
        responder.set(exchange -> respond(exchange, requests.get() == 1 ? 503 : 200,
                requests.get() == 1 ? "{}" : MATCH));
        assertThat(service.suggest(4L, 1225L).isAccepted()).isTrue();
        assertThat(requests.get()).isEqualTo(2);
    }

    @Test
    void aPermanentClientErrorIsNotRetried() {
        properties.getGeocoder().setMaxRetries(3);
        responder.set(exchange -> respond(exchange, 400, "{}"));
        assertBadGateway();
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void uncachedQueriesStillRespectTheConfiguredRateLimit() {
        List<Long> arrivals = Collections.synchronizedList(new ArrayList<>());
        responder.set(exchange -> {
            arrivals.add(System.nanoTime());
            respond(exchange, 200, MATCH);
        });
        properties.getGeocoder().setCacheMaxEntries(0);
        service.suggest(4L, 1225L);
        service.suggest(4L, 1225L);
        assertThat(requests.get()).isEqualTo(2);
        assertThat(TimeUnit.NANOSECONDS.toMillis(arrivals.get(1) - arrivals.get(0))).isGreaterThanOrEqualTo(900);
    }

    @Test
    void endpointExposesBackendDurationWithoutChangingItsPayload() {
        CustomerMapService api = mock(CustomerMapService.class);
        GeocodingSuggestionResponse proposal = GeocodingSuggestionResponse.builder().configured(true).accepted(true).build();
        when(api.suggest(4L, 1225L)).thenReturn(proposal);

        var response = new CustomerMapRestController(api).suggest(4L, 1225L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("Server-Timing")).matches("geolocation;dur=\\d+\\.\\d");
        assertThat(response.getBody().getPayload()).isSameAs(proposal);
    }

    private void assertBadGateway() {
        assertThatThrownBy(() -> service.suggest(4L, 1225L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    private CustomerAddressGeolocationDetailResponse detail(String address, String ubigeo) {
        return CustomerAddressGeolocationDetailResponse.builder().customerId(4L).addressId(1225L)
                .addressText(address).ubigeo(ubigeo).build();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @FunctionalInterface
    private interface Responder {
        void respond(HttpExchange exchange) throws IOException;
    }
}
