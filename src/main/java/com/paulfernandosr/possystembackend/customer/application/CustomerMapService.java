package com.paulfernandosr.possystembackend.customer.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.customer.domain.CustomerAddress;
import com.paulfernandosr.possystembackend.customer.domain.GeolocationSource;
import com.paulfernandosr.possystembackend.customer.domain.GeolocationStatus;
import com.paulfernandosr.possystembackend.customer.domain.exception.InvalidCustomerException;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.input.dto.CustomerMapDtos.*;
import com.paulfernandosr.possystembackend.customer.infrastructure.config.CustomerMapProperties;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.security.Principal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerMapService {
    private final JdbcClient jdbcClient;
    private final UserRepository userRepository;
    private final CustomerAddressHashService addressHashService;
    private final CustomerMapProperties properties;
    private final ObjectMapper objectMapper;
    private Instant lastGeocoderRequest = Instant.EPOCH;
    private volatile AdminAreaBounds adminAreaBounds;
    private final AtomicBoolean automaticBatchRunning = new AtomicBoolean(false);
    private volatile GeolocationBatchResponse lastAutomaticBatch = idleAutomaticBatch();

    public CustomerMapConfigResponse config() {
        CustomerMapProperties.MapSettings map = properties.getMap();
        CustomerMapProperties.GeocoderSettings geocoder = properties.getGeocoder();
        boolean geocoderEnabled = geocoder.isEnabled() && hasText(geocoder.getBaseUrl());
        boolean batchEnabled = geocoderEnabled && geocoder.isBatchEnabled();
        CustomerMapProperties.AutoJobSettings autoJob = geocoder.getAutoJob();
        return CustomerMapConfigResponse.builder()
                .googleMapsApiKey(map.getGoogleMapsApiKey())
                .googleMapsMapId(map.getGoogleMapsMapId())
                .bounds(map.getBounds())
                .center(map.getCenter())
                .minZoom(map.getMinZoom())
                .maxZoom(map.getMaxZoom())
                .urbanZoom(map.getUrbanZoom())
                .geocoderEnabled(geocoderEnabled)
                .batchEnabled(batchEnabled)
                .autoJobEnabled(batchEnabled && autoJob.isEnabled())
                .autoJobBatchSize(normalizeLimit(autoJob.getBatchSize()))
                .autoJobCron(autoJob.getCron())
                .build();
    }

    public List<CustomerAddressMarkerResponse> markers(Map<String, String> filters) {
        QueryParts query = buildBaseQuery(filters, false);
        return jdbcClient.sql(query.sql)
                .params(query.params.toArray())
                .query(this::mapMarker)
                .list();
    }

    public CustomerMapSummaryResponse summary(Map<String, String> filters) {
        QueryParts query = buildBaseQuery(filters, true);
        return jdbcClient.sql(query.sql)
                .params(query.params.toArray())
                .query((rs, rowNum) -> CustomerMapSummaryResponse.builder()
                        .totalAddresses(rs.getLong("total_addresses"))
                        .withCoordinates(rs.getLong("with_coordinates"))
                        .verified(rs.getLong("verified"))
                        .approximate(rs.getLong("approximate"))
                        .pending(rs.getLong("pending"))
                        .needsReview(rs.getLong("needs_review"))
                        .notFound(rs.getLong("not_found"))
                        .build())
                .single();
    }

    public List<CustomerAddressMarkerResponse> pending(Map<String, String> filters) {
        Map<String, String> next = new HashMap<>(filters == null ? Map.of() : filters);
        next.put("geolocationStatusGroup", "PENDING_REVIEW");
        return markers(next);
    }

    public CustomerAddressGeolocationDetailResponse detail(Long customerId, Long addressId) {
        return jdbcClient.sql(detailSql() + " WHERE ca.customer_id = ? AND ca.id = ? AND ca.enabled = TRUE")
                .params(customerId, addressId)
                .query(this::mapDetail)
                .optional()
                .orElseThrow(() -> new InvalidCustomerException("La direccion no pertenece al cliente"));
    }

    @Transactional
    public CustomerAddressGeolocationDetailResponse update(Long customerId,
                                                           Long addressId,
                                                           CustomerAddressGeolocationUpdateRequest request,
                                                           Principal principal,
                                                           boolean verified) {
        User actor = currentUser(principal);
        validateCoordinates(request == null ? null : request.getLatitude(), request == null ? null : request.getLongitude());
        CustomerAddress locked = lockAddress(customerId, addressId);

        if (request != null && request.getExpectedUpdatedAt() != null && locked.getGeolocatedAt() != null
                && locked.getGeolocatedAt().isAfter(request.getExpectedUpdatedAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La ubicacion fue modificada por otro usuario. Actualice la informacion.");
        }

        GeolocationSource source = request.getSource() == null ? GeolocationSource.MANUAL : request.getSource();
        GeolocationStatus status = verified ? GeolocationStatus.VERIFIED : GeolocationStatus.APPROXIMATE;
        String hash = addressHashService.calculate(locked);

        String sql = """
                UPDATE customer_address
                SET latitude = ?,
                    longitude = ?,
                    geolocation_status = ?,
                    geolocation_source = ?,
                    geolocation_accuracy_meters = ?,
                    geolocated_at = CURRENT_TIMESTAMP,
                    geolocated_by = ?,
                    geolocation_verified_at = CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE geolocation_verified_at END,
                    geolocation_verified_by = CASE WHEN ? THEN ? ELSE geolocation_verified_by END,
                    geolocation_address_hash = ?
                WHERE id = ?
                  AND customer_id = ?
                  AND enabled = TRUE
                """;

        int updated = jdbcClient.sql(sql)
                .params(request.getLatitude(),
                        request.getLongitude(),
                        status.name(),
                        source.name(),
                        request.getAccuracyMeters(),
                        actor.getId(),
                        verified,
                        verified,
                        actor.getId(),
                        hash,
                        addressId,
                        customerId)
                .update();

        if (updated != 1) {
            throw new InvalidCustomerException("La direccion no pertenece al cliente");
        }

        return detail(customerId, addressId);
    }

    public GeocodingSuggestionResponse suggest(Long customerId, Long addressId) {
        CustomerMapProperties.GeocoderSettings geocoder = properties.getGeocoder();
        if (!geocoder.isEnabled() || !hasText(geocoder.getBaseUrl())) {
            return GeocodingSuggestionResponse.builder()
                    .configured(false)
                    .accepted(false)
                    .message("La busqueda automatica no esta configurada. Use pin manual, GPS o pegue coordenadas.")
                    .build();
        }

        CustomerAddressGeolocationDetailResponse detail = detail(customerId, addressId);
        if (!"NOMINATIM".equalsIgnoreCase(geocoder.getProvider())) {
            return GeocodingSuggestionResponse.builder()
                    .configured(true)
                    .accepted(false)
                    .message("Proveedor de geocodificacion no soportado: " + geocoder.getProvider())
                    .build();
        }

        NominatimMatch match = searchNominatim(detail, geocoder);
        if (match == null || match.result() == null || !hasText(match.result().lat()) || !hasText(match.result().lon())) {
            return GeocodingSuggestionResponse.builder()
                    .configured(true)
                    .accepted(false)
                    .message("No se encontro una coordenada confiable para esta direccion. Use pin manual o GPS.")
                    .build();
        }

        NominatimResult result = match.result();
        if (hasText(match.rejectionReason())) {
            return GeocodingSuggestionResponse.builder()
                    .configured(true)
                    .accepted(false)
                    .message(match.rejectionReason())
                    .latitude(Double.valueOf(result.lat()))
                    .longitude(Double.valueOf(result.lon()))
                    .accuracyMeters(estimateAccuracyMeters(result))
                    .interpretedAddress(result.displayName())
                    .precision(result.type())
                    .partialMatch(true)
                    .withinAdministrativeArea(false)
                    .rejectionReason(match.rejectionReason())
                    .build();
        }

        return GeocodingSuggestionResponse.builder()
                .configured(true)
                .accepted(true)
                .message(match.approximate()
                        ? "Propuesta aproximada por zona. Revise y mueva el pin hasta la direccion antes de confirmar."
                        : "Propuesta encontrada. Revise el punto en el mapa antes de confirmar.")
                .latitude(Double.valueOf(result.lat()))
                .longitude(Double.valueOf(result.lon()))
                .accuracyMeters(estimateAccuracyMeters(result))
                .interpretedAddress(result.displayName())
                .precision(result.type())
                .partialMatch(match.approximate())
                .withinAdministrativeArea(true)
                .build();
    }

    public GeolocationBatchResponse batch(GeolocationBatchRequest request, Principal principal) {
        User actor = currentUser(principal);
        return processBatch(request, actor.getId(), null, "manual", true);
    }

    public GeolocationBatchResponse automaticBatchStatus() {
        return lastAutomaticBatch;
    }

    public GeolocationBatchResponse startAutomaticBatchNow(Principal principal) {
        Long actorId = principal == null ? null : currentUser(principal).getId();
        if (!isBatchEnabled()) {
            return GeolocationBatchResponse.builder()
                    .jobId("auto")
                    .status("DISABLED")
                    .message("Batch deshabilitado hasta configurar geocoder.")
                    .requested(0)
                    .queued(0)
                    .processed(0)
                    .saved(0)
                    .rejected(0)
                    .failed(0)
                    .build();
        }
        if (!automaticBatchRunning.compareAndSet(false, true)) {
            return lastAutomaticBatch;
        }

        int queued = countBatchTargets(false);
        lastAutomaticBatch = GeolocationBatchResponse.builder()
                .jobId("auto")
                .status(queued == 0 ? "DONE" : "RUNNING")
                .message(queued == 0
                        ? "No hay direcciones pendientes para geocodificar."
                        : "Geocodificacion masiva iniciada. Pendientes: " + queued + ".")
                .requested(queued)
                .queued(queued)
                .processed(0)
                .saved(0)
                .rejected(0)
                .failed(0)
                .build();

        if (queued == 0) {
            automaticBatchRunning.set(false);
            return lastAutomaticBatch;
        }

        CompletableFuture.runAsync(() -> runAutomaticBatchUntilEmpty(actorId));
        return lastAutomaticBatch;
    }

    @Scheduled(cron = "${app.customer-map.geocoder.auto-job.cron:0 */5 * * * *}", zone = "America/Lima")
    public void scheduledGeolocationBatch() {
        CustomerMapProperties.GeocoderSettings geocoder = properties.getGeocoder();
        CustomerMapProperties.AutoJobSettings autoJob = geocoder.getAutoJob();
        if (!autoJob.isEnabled()) {
            return;
        }
        if (!automaticBatchRunning.compareAndSet(false, true)) {
            return;
        }

        try {
            GeolocationBatchRequest request = new GeolocationBatchRequest();
            request.setLimit(autoJob.getBatchSize());
            GeolocationBatchResponse response = processBatch(request, null, "auto", "automatico", false);
            lastAutomaticBatch = response;
            if (response.getProcessed() > 0 || response.getFailed() > 0) {
                log.info("Customer map auto geocoding: {}", response.getMessage());
            }
        } catch (Exception ex) {
            lastAutomaticBatch = GeolocationBatchResponse.builder()
                    .jobId("auto")
                    .status("FAILED")
                    .message("Job automatico fallo: " + ex.getMessage())
                    .requested(0)
                    .queued(0)
                    .processed(0)
                    .saved(0)
                    .rejected(0)
                    .failed(1)
                    .build();
            log.warn("Customer map auto geocoding failed", ex);
        } finally {
            automaticBatchRunning.set(false);
        }
    }

    private void runAutomaticBatchUntilEmpty(Long actorId) {
        int requested = countBatchTargets(false);
        int processed = 0;
        int saved = 0;
        int rejected = 0;
        int failed = 0;

        try {
            while (true) {
                GeolocationBatchRequest request = new GeolocationBatchRequest();
                request.setLimit(properties.getGeocoder().getAutoJob().getBatchSize());
                GeolocationBatchResponse response = processBatch(request, actorId, "auto", "automatico masivo", false);

                processed += response.getProcessed();
                saved += response.getSaved();
                rejected += response.getRejected();
                failed += response.getFailed();

                int queued = countBatchTargets(false);
                lastAutomaticBatch = GeolocationBatchResponse.builder()
                        .jobId("auto")
                        .status(queued == 0 && failed == 0 ? "DONE" : "RUNNING")
                        .message("Geocodificacion masiva en curso. Procesadas: " + processed + ". Guardadas: " + saved + ". A revision/no encontradas: " + rejected + ". Pendientes: " + queued + ". Errores: " + failed + ".")
                        .requested(requested)
                        .queued(queued)
                        .processed(processed)
                        .saved(saved)
                        .rejected(rejected)
                        .failed(failed)
                        .build();

                if (queued == 0 || response.getProcessed() == 0 || response.getFailed() > 0) {
                    break;
                }
            }

            int queued = countBatchTargets(false);
            lastAutomaticBatch = GeolocationBatchResponse.builder()
                    .jobId("auto")
                    .status(failed == 0 ? "DONE" : "DONE_WITH_ERRORS")
                    .message("Geocodificacion masiva finalizada. Procesadas: " + processed + ". Guardadas: " + saved + ". A revision/no encontradas: " + rejected + ". Pendientes: " + queued + ". Errores: " + failed + ".")
                    .requested(requested)
                    .queued(queued)
                    .processed(processed)
                    .saved(saved)
                    .rejected(rejected)
                    .failed(failed)
                    .build();
            log.info("Customer map massive geocoding: {}", lastAutomaticBatch.getMessage());
        } catch (Exception ex) {
            lastAutomaticBatch = GeolocationBatchResponse.builder()
                    .jobId("auto")
                    .status("FAILED")
                    .message("Geocodificacion masiva fallo: " + ex.getMessage())
                    .requested(requested)
                    .queued(countBatchTargets(false))
                    .processed(processed)
                    .saved(saved)
                    .rejected(rejected)
                    .failed(failed + 1)
                    .build();
            log.warn("Customer map massive geocoding failed", ex);
        } finally {
            automaticBatchRunning.set(false);
        }
    }

    private GeolocationBatchResponse processBatch(GeolocationBatchRequest request,
                                                  Long actorId,
                                                  String jobId,
                                                  String sourceLabel,
                                                  boolean includeReviewStatuses) {
        if (!isBatchEnabled()) {
            int requested = request == null || request.getAddressIds() == null ? 0 : request.getAddressIds().size();
            return GeolocationBatchResponse.builder()
                    .jobId(jobId)
                    .status("DISABLED")
                    .message("Batch deshabilitado hasta configurar geocoder.")
                    .requested(requested)
                    .queued(0)
                    .processed(0)
                    .saved(0)
                    .rejected(0)
                    .failed(0)
                    .build();
        }

        int limit = normalizeLimit(request == null || request.getLimit() == null ? 20 : request.getLimit());
        List<BatchTarget> targets = batchTargets(request == null ? null : request.getAddressIds(), limit, includeReviewStatuses);
        boolean explicitAddresses = request != null && request.getAddressIds() != null && !request.getAddressIds().isEmpty();
        int requested = explicitAddresses ? request.getAddressIds().size() : countBatchTargets(includeReviewStatuses);
        int saved = 0;
        int rejected = 0;
        int failed = 0;

        for (BatchTarget target : targets) {
            try {
                CustomerAddressGeolocationDetailResponse detail = detail(target.customerId(), target.addressId());
                NominatimMatch match = searchNominatim(detail, properties.getGeocoder());
                if (match == null || match.result() == null || !hasText(match.result().lat()) || !hasText(match.result().lon())) {
                    markAddressStatus(target.customerId(), target.addressId(), GeolocationStatus.NOT_FOUND, actorId);
                    rejected++;
                    continue;
                }
                if (hasText(match.rejectionReason())) {
                    markAddressStatus(target.customerId(), target.addressId(), GeolocationStatus.NEEDS_REVIEW, actorId);
                    rejected++;
                    continue;
                }
                if (match.approximate()) {
                    markAddressStatus(target.customerId(), target.addressId(), GeolocationStatus.NEEDS_REVIEW, actorId);
                    rejected++;
                    continue;
                }

                saveGeocoderLocation(target, match.result(), actorId);
                saved++;
            } catch (Exception ex) {
                failed++;
                log.warn("No se pudo geocodificar direccion {} desde batch {}", target.addressId(), sourceLabel, ex);
            }
        }

        int processed = saved + rejected + failed;
        return GeolocationBatchResponse.builder()
                .jobId(jobId)
                .status(failed == 0 ? "DONE" : "DONE_WITH_ERRORS")
                .message("Batch " + sourceLabel + ": procesadas " + processed + " direcciones. Guardadas: " + saved + ". A revision/no encontradas: " + rejected + ". Errores: " + failed + ".")
                .requested(requested)
                .queued(Math.max(0, requested - processed))
                .processed(processed)
                .saved(saved)
                .rejected(rejected)
                .failed(failed)
                .build();
    }

    private boolean isBatchEnabled() {
        return properties.getGeocoder().isEnabled()
                && properties.getGeocoder().isBatchEnabled()
                && hasText(properties.getGeocoder().getBaseUrl());
    }

    private int normalizeLimit(Integer limit) {
        return Math.max(1, Math.min(limit == null ? 20 : limit, 25));
    }

    private GeolocationBatchResponse idleAutomaticBatch() {
        return GeolocationBatchResponse.builder()
                .jobId("auto")
                .status("IDLE")
                .message("Job automatico aun no ejecutado.")
                .requested(0)
                .queued(0)
                .processed(0)
                .saved(0)
                .rejected(0)
                .failed(0)
                .build();
    }

    private NominatimMatch searchNominatim(CustomerAddressGeolocationDetailResponse detail,
                                            CustomerMapProperties.GeocoderSettings geocoder) {
        List<GeocoderAttempt> attemptsToSearch = buildGeocoderQueries(detail);
        double[] bounds = properties.getMap().getBounds();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(geocoder.getTimeoutMs());
        requestFactory.setReadTimeout(geocoder.getTimeoutMs());
        RestClient client = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, geocoder.getUserAgent())
                .defaultHeader(HttpHeaders.ACCEPT_LANGUAGE, geocoder.getLanguage())
                .build();

        Exception lastError = null;
        NominatimMatch firstRejected = null;
        int retries = Math.max(1, geocoder.getMaxRetries() + 1);
        for (GeocoderAttempt searchAttempt : attemptsToSearch) {
            URI uri = UriComponentsBuilder.fromHttpUrl(geocoder.getBaseUrl())
                    .path("/search")
                    .queryParam("format", "jsonv2")
                    .queryParam("q", searchAttempt.query())
                    .queryParam("countrycodes", geocoder.getCountry().toLowerCase(Locale.ROOT))
                    .queryParam("accept-language", geocoder.getLanguage())
                    .queryParam("addressdetails", 1)
                    .queryParam("limit", 3)
                    .queryParam("bounded", 1)
                    .queryParam("viewbox", bounds[0] + "," + bounds[3] + "," + bounds[2] + "," + bounds[1])
                    .build()
                    .encode()
                    .toUri();

            for (int attempt = 0; attempt < retries; attempt++) {
                waitForGeocoderSlot(geocoder);
                try {
                    NominatimResult[] results = client
                            .get()
                            .uri(uri)
                            .retrieve()
                            .body(NominatimResult[].class);
                    if (results != null && results.length > 0) {
                        for (NominatimResult result : results) {
                            String rejectionReason = geocoderRejectionReason(detail, result);
                            NominatimMatch match = new NominatimMatch(result, searchAttempt.approximate(), rejectionReason);
                            if (!hasText(rejectionReason)) {
                                return match;
                            }
                            if (firstRejected == null) {
                                firstRejected = match;
                            }
                        }
                    }
                } catch (Exception ex) {
                    lastError = ex;
                }
            }
        }

        if (lastError != null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo consultar el geocoder configurado", lastError);
        }
        return firstRejected;
    }

    private String geocoderRejectionReason(CustomerAddressGeolocationDetailResponse detail, NominatimResult result) {
        double latitude;
        double longitude;
        try {
            latitude = Double.parseDouble(result.lat());
            longitude = Double.parseDouble(result.lon());
        } catch (Exception ex) {
            return "El geocoder devolvio coordenadas invalidas.";
        }

        AreaBounds expected = expectedBounds(detail.getUbigeo());
        if (expected != null && !contains(expected.bounds(), longitude, latitude)) {
            return "Propuesta no confiable: cae fuera del " + expected.label() + " registrado en el UBIGEO " + detail.getUbigeo() + ". Use pin manual o GPS.";
        }

        return null;
    }

    private AreaBounds expectedBounds(String ubigeo) {
        if (!hasText(ubigeo)) return null;
        String clean = ubigeo.trim();
        AdminAreaBounds bounds = adminAreaBounds();
        if (clean.length() >= 6 && bounds.districts().containsKey(clean.substring(0, 6))) {
            return new AreaBounds(bounds.districts().get(clean.substring(0, 6)), "distrito");
        }
        if (clean.length() >= 4 && bounds.provinces().containsKey(clean.substring(0, 4))) {
            return new AreaBounds(bounds.provinces().get(clean.substring(0, 4)), "provincia");
        }
        if (clean.length() >= 2 && bounds.departments().containsKey(clean.substring(0, 2))) {
            return new AreaBounds(bounds.departments().get(clean.substring(0, 2)), "departamento");
        }
        return null;
    }

    private boolean contains(double[] bounds, double longitude, double latitude) {
        double tolerance = 0.01D;
        return longitude >= bounds[0] - tolerance
                && latitude >= bounds[1] - tolerance
                && longitude <= bounds[2] + tolerance
                && latitude <= bounds[3] + tolerance;
    }

    private AdminAreaBounds adminAreaBounds() {
        AdminAreaBounds current = adminAreaBounds;
        if (current != null) return current;
        synchronized (this) {
            if (adminAreaBounds != null) return adminAreaBounds;
            try {
                Map<String, Object> raw = objectMapper.readValue(
                        new ClassPathResource("customer-map/admin-area-bounds.json").getInputStream(),
                        new TypeReference<>() {
                        });
                adminAreaBounds = new AdminAreaBounds(
                        boundsMap(raw.get("departments")),
                        boundsMap(raw.get("provinces")),
                        boundsMap(raw.get("districts")));
                return adminAreaBounds;
            } catch (Exception ex) {
                adminAreaBounds = new AdminAreaBounds(Map.of(), Map.of(), Map.of());
                return adminAreaBounds;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, double[]> boundsMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) return Map.of();
        Map<String, double[]> result = new HashMap<>();
        map.forEach((key, value) -> {
            if (!(key instanceof String code) || !(value instanceof List<?> values) || values.size() != 4) return;
            double[] bounds = new double[4];
            for (int i = 0; i < 4; i++) {
                Object item = values.get(i);
                if (!(item instanceof Number number)) return;
                bounds[i] = number.doubleValue();
            }
            result.put(code, bounds);
        });
        return result;
    }

    private List<GeocoderAttempt> buildGeocoderQueries(CustomerAddressGeolocationDetailResponse detail) {
        String cleanAddress = normalizeAddressForGeocoder(detail.getAddressText());
        String compactAddress = compactAddressForGeocoder(detail.getAddressText());
        List<GeocoderAttempt> attempts = new ArrayList<>();
        addGeocoderAttempt(attempts, false, detail.getAddressText(), detail.getDistrictName(), detail.getProvinceName(), detail.getDepartmentName(), "Peru");
        addGeocoderAttempt(attempts, false, compactAddress, detail.getDistrictName(), detail.getDepartmentName(), "Peru");
        addGeocoderAttempt(attempts, false, compactAddress, detail.getDistrictName(), "Peru");
        addGeocoderAttempt(attempts, false, cleanAddress, detail.getDistrictName(), detail.getProvinceName(), detail.getDepartmentName(), "Peru");
        addGeocoderAttempt(attempts, false, cleanAddress, detail.getDistrictName(), detail.getDepartmentName(), "Peru");
        addGeocoderAttempt(attempts, true, detail.getDistrictName(), detail.getProvinceName(), detail.getDepartmentName(), "Peru");
        addGeocoderAttempt(attempts, true, detail.getProvinceName(), detail.getDepartmentName(), "Peru");
        return attempts;
    }

    private void addGeocoderAttempt(List<GeocoderAttempt> attempts, boolean approximate, String... parts) {
        String query = String.join(", ", Arrays.stream(parts)
                .filter(this::hasText)
                .map(String::trim)
                .distinct()
                .toList());
        if (hasText(query) && attempts.stream().noneMatch(item -> item.query().equalsIgnoreCase(query))) {
            attempts.add(new GeocoderAttempt(query, approximate));
        }
    }

    private String normalizeAddressForGeocoder(String value) {
        if (!hasText(value)) return value;
        return value.trim()
                .replaceAll("(?i)\\bAV\\.?", "Avenida")
                .replaceAll("(?i)\\bJR\\.?", "Jiron")
                .replaceAll("(?i)\\bCAL\\.?", "Calle")
                .replaceAll("(?i)\\bNRO\\.?", "")
                .replaceAll("(?i)\\bNUM\\.?", "")
                .replaceAll("(?i)\\bMZA\\.?", "Manzana")
                .replaceAll("(?i)\\bLT\\.?", "Lote")
                .replaceAll("(?i)\\bLOTE\\.?", "Lote")
                .replaceAll("\\([^)]*\\)", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String compactAddressForGeocoder(String value) {
        if (!hasText(value)) return value;
        return value.trim()
                .replaceAll("(?i)\\bAV\\.?", "")
                .replaceAll("(?i)\\bAVENIDA\\b", "")
                .replaceAll("(?i)\\bJR\\.?", "")
                .replaceAll("(?i)\\bJIRON\\b", "")
                .replaceAll("(?i)\\bJIRÓN\\b", "")
                .replaceAll("(?i)\\bCAL\\.?", "")
                .replaceAll("(?i)\\bCALLE\\b", "")
                .replaceAll("(?i)\\bNRO\\.?", "")
                .replaceAll("(?i)\\bNUM\\.?", "")
                .replaceAll("(?i)\\bNUMERO\\b", "")
                .replaceAll("(?i)\\bNÚMERO\\b", "")
                .replaceAll("(?i)\\bMZA\\.?", "")
                .replaceAll("(?i)\\bMANZANA\\b", "")
                .replaceAll("(?i)\\bLT\\.?", "")
                .replaceAll("(?i)\\bLOTE\\b", "")
                .replaceAll("\\([^)]*\\)", " ")
                .replaceAll("[.,;#-]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private synchronized void waitForGeocoderSlot(CustomerMapProperties.GeocoderSettings geocoder) {
        int perMinute = Math.max(1, geocoder.getRateLimitPerMinute());
        long minDelayMs = Math.max(1000, 60_000L / perMinute);
        long elapsedMs = Duration.between(lastGeocoderRequest, Instant.now()).toMillis();
        if (elapsedMs < minDelayMs) {
            try {
                Thread.sleep(minDelayMs - elapsedMs);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Consulta de geocoder interrumpida", ex);
            }
        }
        lastGeocoderRequest = Instant.now();
    }

    private Double estimateAccuracyMeters(NominatimResult result) {
        if ("house".equalsIgnoreCase(result.type()) || "building".equalsIgnoreCase(result.type())) {
            return 30D;
        }
        if ("road".equalsIgnoreCase(result.type()) || "residential".equalsIgnoreCase(result.type())) {
            return 250D;
        }
        if ("suburb".equalsIgnoreCase(result.type()) || "city_district".equalsIgnoreCase(result.type())) {
            return 1000D;
        }
        return 3000D;
    }

    private QueryParts buildBaseQuery(Map<String, String> filters, boolean summary) {
        String select = summary ? """
                SELECT
                    COUNT(*) AS total_addresses,
                    COUNT(*) FILTER (WHERE ca.latitude IS NOT NULL AND ca.longitude IS NOT NULL) AS with_coordinates,
                    COUNT(*) FILTER (WHERE ca.geolocation_status = 'VERIFIED') AS verified,
                    COUNT(*) FILTER (WHERE ca.geolocation_status = 'APPROXIMATE') AS approximate,
                    COUNT(*) FILTER (WHERE ca.geolocation_status = 'PENDING') AS pending,
                    COUNT(*) FILTER (WHERE ca.geolocation_status = 'NEEDS_REVIEW') AS needs_review,
                    COUNT(*) FILTER (WHERE ca.geolocation_status = 'NOT_FOUND') AS not_found
                """ : """
                SELECT
                    ca.id AS address_id,
                    ca.customer_id,
                    c.legal_name AS customer_name,
                    c.document_type,
                    c.document_number,
                    ca.fiscal AS main_address,
                    ca.address AS address_text,
                    ca.phone,
                    ca.district AS district_name,
                    ca.province AS province_name,
                    ca.department AS department_name,
                    ca.ubigeo,
                    trim(concat(COALESCE(u.first_name, ''), ' ', COALESCE(u.last_name, ''))) AS responsible_name,
                    ass.user_id AS responsible_user_id,
                    ca.latitude,
                    ca.longitude,
                    ca.geolocation_status
                """;

        StringBuilder sql = new StringBuilder(select).append("""
                FROM customer_address ca
                INNER JOIN customers c ON c.id = ca.customer_id
                LEFT JOIN customer_assignments ass ON ass.customer_id = c.id AND ass.active = TRUE
                LEFT JOIN users u ON u.id = ass.user_id
                WHERE ca.enabled = TRUE
                  AND c.enabled = TRUE
                  AND NOT (c.document_type = 'GEN' OR c.document_number = '0')
                """);

        List<Object> params = new ArrayList<>();
        String search = value(filters, "search");
        if (hasText(search)) {
            sql.append("""
                      AND (
                          c.legal_name ILIKE ?
                          OR c.document_number ILIKE ?
                          OR ca.phone ILIKE ?
                          OR ca.address ILIKE ?
                      )
                    """);
            String like = "%" + search.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }

        addAdministrativeFilter(sql, params, "ca.department", value(filters, "departmentCode"), 2, false);
        addAdministrativeFilter(sql, params, "ca.province", value(filters, "provinceCode"), 4, false);
        addAdministrativeFilter(sql, params, "ca.district", value(filters, "districtCode"), 6, true);
        addStringFilter(sql, params, "ca.geolocation_status", value(filters, "geolocationStatus"));

        if ("PENDING_REVIEW".equals(value(filters, "geolocationStatusGroup"))) {
            sql.append(" AND ca.geolocation_status IN ('PENDING', 'NEEDS_REVIEW', 'NOT_FOUND')");
        }
        if (hasText(value(filters, "responsibleUserId"))) {
            sql.append(" AND ass.user_id = ?");
            params.add(Long.valueOf(value(filters, "responsibleUserId")));
        }
        if (hasText(value(filters, "mainAddress"))) {
            sql.append(" AND ca.fiscal = ?");
            params.add(Boolean.valueOf(value(filters, "mainAddress")));
        }
        if ("true".equalsIgnoreCase(value(filters, "withCoordinatesOnly"))) {
            sql.append(" AND ca.latitude IS NOT NULL AND ca.longitude IS NOT NULL");
        }
        addDoubleRange(sql, params, "ca.latitude", ">=", value(filters, "minLatitude"));
        addDoubleRange(sql, params, "ca.latitude", "<=", value(filters, "maxLatitude"));
        addDoubleRange(sql, params, "ca.longitude", ">=", value(filters, "minLongitude"));
        addDoubleRange(sql, params, "ca.longitude", "<=", value(filters, "maxLongitude"));

        if (!summary) {
            sql.append(" ORDER BY ca.geolocation_status, c.legal_name, ca.fiscal DESC, ca.position ASC LIMIT 5000");
        }

        return new QueryParts(sql.toString(), params);
    }

    private String detailSql() {
        return """
                SELECT
                    ca.id AS address_id,
                    ca.customer_id,
                    c.legal_name AS customer_name,
                    c.document_type,
                    c.document_number,
                    ca.fiscal AS main_address,
                    ca.address AS address_text,
                    ca.phone,
                    ca.email,
                    ca.department AS department_name,
                    ca.province AS province_name,
                    ca.district AS district_name,
                    ca.ubigeo,
                    trim(concat(COALESCE(u.first_name, ''), ' ', COALESCE(u.last_name, ''))) AS responsible_name,
                    ass.user_id AS responsible_user_id,
                    ca.latitude,
                    ca.longitude,
                    ca.geolocation_status,
                    ca.geolocation_source,
                    ca.geolocation_accuracy_meters,
                    ca.geolocated_at,
                    trim(concat(COALESCE(geo_user.first_name, ''), ' ', COALESCE(geo_user.last_name, ''))) AS geolocated_by_name,
                    ca.geolocation_verified_at,
                    trim(concat(COALESCE(ver_user.first_name, ''), ' ', COALESCE(ver_user.last_name, ''))) AS geolocation_verified_by_name,
                    ca.updated_at
                FROM customer_address ca
                INNER JOIN customers c ON c.id = ca.customer_id
                LEFT JOIN customer_assignments ass ON ass.customer_id = c.id AND ass.active = TRUE
                LEFT JOIN users u ON u.id = ass.user_id
                LEFT JOIN users geo_user ON geo_user.id = ca.geolocated_by
                LEFT JOIN users ver_user ON ver_user.id = ca.geolocation_verified_by
                """;
    }

    private List<BatchTarget> batchTargets(List<Long> addressIds, int limit, boolean includeReviewStatuses) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT ca.id AS address_id,
                       ca.customer_id
                FROM customer_address ca
                INNER JOIN customers c ON c.id = ca.customer_id
                WHERE ca.enabled = TRUE
                  AND c.enabled = TRUE
                  AND NOT (c.document_type = 'GEN' OR c.document_number = '0')
                """);
        addBatchStatusFilter(sql, includeReviewStatuses);

        List<Long> cleanIds = addressIds == null ? List.of() : addressIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (!cleanIds.isEmpty()) {
            sql.append(" AND ca.id IN (");
            sql.append(String.join(",", Collections.nCopies(cleanIds.size(), "?")));
            sql.append(")");
            params.addAll(cleanIds);
        }

        sql.append(" ORDER BY ca.updated_at ASC NULLS FIRST, ca.id ASC LIMIT ?");
        params.add(limit);

        return jdbcClient.sql(sql.toString())
                .params(params.toArray())
                .query((rs, rowNum) -> new BatchTarget(rs.getLong("customer_id"), rs.getLong("address_id")))
                .list();
    }

    private int countBatchTargets(boolean includeReviewStatuses) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM customer_address ca
                INNER JOIN customers c ON c.id = ca.customer_id
                WHERE ca.enabled = TRUE
                  AND c.enabled = TRUE
                  AND NOT (c.document_type = 'GEN' OR c.document_number = '0')
                """);
        addBatchStatusFilter(sql, includeReviewStatuses);
        Long count = jdbcClient.sql(sql.toString())
                .query(Long.class)
                .single();
        return count == null ? 0 : Math.toIntExact(count);
    }

    private void addBatchStatusFilter(StringBuilder sql, boolean includeReviewStatuses) {
        if (includeReviewStatuses) {
            sql.append(" AND ca.geolocation_status IN ('PENDING', 'NEEDS_REVIEW', 'NOT_FOUND')");
        } else {
            sql.append(" AND ca.geolocation_status = 'PENDING'");
        }
    }

    private void markAddressStatus(Long customerId, Long addressId, GeolocationStatus status, Long actorId) {
        jdbcClient.sql("""
                UPDATE customer_address
                SET geolocation_status = ?,
                    geolocation_source = ?,
                    geolocated_at = CURRENT_TIMESTAMP,
                    geolocated_by = ?
                WHERE id = ?
                  AND customer_id = ?
                  AND enabled = TRUE
                """)
                .params(status.name(), GeolocationSource.GEOCODER.name(), actorId, addressId, customerId)
                .update();
    }

    private void saveGeocoderLocation(BatchTarget target, NominatimResult result, Long actorId) {
        double latitude = Double.parseDouble(result.lat());
        double longitude = Double.parseDouble(result.lon());
        validateCoordinates(latitude, longitude);

        CustomerAddress locked = lockAddress(target.customerId(), target.addressId());
        String hash = addressHashService.calculate(locked);

        int updated = jdbcClient.sql("""
                UPDATE customer_address
                SET latitude = ?,
                    longitude = ?,
                    geolocation_status = ?,
                    geolocation_source = ?,
                    geolocation_accuracy_meters = ?,
                    geolocated_at = CURRENT_TIMESTAMP,
                    geolocated_by = ?,
                    geolocation_address_hash = ?
                WHERE id = ?
                  AND customer_id = ?
                  AND enabled = TRUE
                """)
                .params(latitude,
                        longitude,
                        GeolocationStatus.APPROXIMATE.name(),
                        GeolocationSource.GEOCODER.name(),
                        estimateAccuracyMeters(result),
                        actorId,
                        hash,
                        target.addressId(),
                        target.customerId())
                .update();

        if (updated != 1) {
            throw new InvalidCustomerException("La direccion no pertenece al cliente");
        }
    }

    private CustomerAddress lockAddress(Long customerId, Long addressId) {
        return jdbcClient.sql("""
                SELECT id, customer_id, address, ubigeo, department, province, district,
                       phone, email, fiscal, enabled, position, latitude, longitude,
                       geolocation_status, geolocation_source, geolocation_accuracy_meters,
                       geolocated_at, geolocated_by, geolocation_verified_at,
                       geolocation_verified_by, geolocation_address_hash
                FROM customer_address
                WHERE id = ?
                  AND customer_id = ?
                  AND enabled = TRUE
                FOR UPDATE
                """)
                .params(addressId, customerId)
                .query(CustomerAddress.class)
                .optional()
                .orElseThrow(() -> new InvalidCustomerException("La direccion no pertenece al cliente"));
    }

    private CustomerAddressMarkerResponse mapMarker(ResultSet rs, int rowNum) throws SQLException {
        return CustomerAddressMarkerResponse.builder()
                .addressId(rs.getLong("address_id"))
                .customerId(rs.getLong("customer_id"))
                .customerName(rs.getString("customer_name"))
                .documentType(rs.getString("document_type"))
                .documentNumber(rs.getString("document_number"))
                .mainAddress(rs.getBoolean("main_address"))
                .addressText(rs.getString("address_text"))
                .phone(rs.getString("phone"))
                .districtName(rs.getString("district_name"))
                .provinceName(rs.getString("province_name"))
                .departmentName(rs.getString("department_name"))
                .ubigeo(rs.getString("ubigeo"))
                .responsibleName(blankToNull(rs.getString("responsible_name")))
                .responsibleUserId(nullableLong(rs, "responsible_user_id"))
                .latitude(nullableDouble(rs, "latitude"))
                .longitude(nullableDouble(rs, "longitude"))
                .geolocationStatus(GeolocationStatus.valueOf(rs.getString("geolocation_status")))
                .build();
    }

    private CustomerAddressGeolocationDetailResponse mapDetail(ResultSet rs, int rowNum) throws SQLException {
        Double latitude = nullableDouble(rs, "latitude");
        Double longitude = nullableDouble(rs, "longitude");
        return CustomerAddressGeolocationDetailResponse.builder()
                .addressId(rs.getLong("address_id"))
                .customerId(rs.getLong("customer_id"))
                .customerName(rs.getString("customer_name"))
                .documentType(rs.getString("document_type"))
                .documentNumber(rs.getString("document_number"))
                .mainAddress(rs.getBoolean("main_address"))
                .addressText(rs.getString("address_text"))
                .phone(rs.getString("phone"))
                .email(rs.getString("email"))
                .departmentName(rs.getString("department_name"))
                .provinceName(rs.getString("province_name"))
                .districtName(rs.getString("district_name"))
                .ubigeo(rs.getString("ubigeo"))
                .responsibleName(blankToNull(rs.getString("responsible_name")))
                .responsibleUserId(nullableLong(rs, "responsible_user_id"))
                .latitude(latitude)
                .longitude(longitude)
                .geolocationStatus(GeolocationStatus.valueOf(rs.getString("geolocation_status")))
                .geolocationSource(nullableEnum(rs.getString("geolocation_source")))
                .geolocationAccuracyMeters(nullableDouble(rs, "geolocation_accuracy_meters"))
                .geolocatedAt(toLocalDateTime(rs.getTimestamp("geolocated_at")))
                .geolocatedByName(blankToNull(rs.getString("geolocated_by_name")))
                .geolocationVerifiedAt(toLocalDateTime(rs.getTimestamp("geolocation_verified_at")))
                .geolocationVerifiedByName(blankToNull(rs.getString("geolocation_verified_by_name")))
                .googleMapsDirectionsUrl(latitude == null || longitude == null ? null :
                        "https://www.google.com/maps/dir/?api=1&destination=" + latitude + "," + longitude)
                .updatedAt(toLocalDateTime(rs.getTimestamp("updated_at")))
                .build();
    }

    private User currentUser(Principal principal) {
        if (principal == null || !hasText(principal.getName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado");
        }
        return userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado"));
    }

    private void validateCoordinates(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            throw new InvalidCustomerException("Latitude and longitude are required together");
        }
        if (latitude < -90 || latitude > 90) {
            throw new InvalidCustomerException("Latitude is invalid");
        }
        if (longitude < -180 || longitude > 180) {
            throw new InvalidCustomerException("Longitude is invalid");
        }
        if (latitude < -18.5 || latitude > 0.5 || longitude < -81.5 || longitude > -68.5) {
            throw new InvalidCustomerException("Coordinates are outside Peru bounds");
        }
    }

    private void addStringFilter(StringBuilder sql, List<Object> params, String column, String value) {
        if (!hasText(value)) return;
        sql.append(" AND ").append(column).append(" = ?");
        params.add(value.trim());
    }

    private void addAdministrativeFilter(StringBuilder sql,
                                         List<Object> params,
                                         String nameColumn,
                                         String value,
                                         int expectedCodeLength,
                                         boolean exactUbigeo) {
        if (!hasText(value)) return;
        String clean = value.trim();
        if (clean.length() == expectedCodeLength && clean.chars().allMatch(Character::isDigit)) {
            sql.append(exactUbigeo ? " AND ca.ubigeo = ?" : " AND ca.ubigeo LIKE ?");
            params.add(exactUbigeo ? clean : clean + "%");
            return;
        }

        sql.append(" AND UPPER(").append(nameColumn).append(") = UPPER(?)");
        params.add(clean);
    }

    private void addDoubleRange(StringBuilder sql, List<Object> params, String column, String operator, String value) {
        if (!hasText(value)) return;
        sql.append(" AND ").append(column).append(" ").append(operator).append(" ?");
        params.add(Double.valueOf(value));
    }

    private String value(Map<String, String> filters, String key) {
        return filters == null ? null : filters.get(key);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private GeolocationSource nullableEnum(String value) {
        return hasText(value) ? GeolocationSource.valueOf(value) : null;
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private record QueryParts(String sql, List<Object> params) {
    }

    private record GeocoderAttempt(String query, boolean approximate) {
    }

    private record BatchTarget(Long customerId, Long addressId) {
    }

    private record AreaBounds(double[] bounds, String label) {
    }

    private record AdminAreaBounds(Map<String, double[]> departments,
                                   Map<String, double[]> provinces,
                                   Map<String, double[]> districts) {
    }

    private record NominatimMatch(NominatimResult result, boolean approximate, String rejectionReason) {
    }

    private record NominatimResult(String lat,
                                   String lon,
                                   @com.fasterxml.jackson.annotation.JsonProperty("display_name") String displayName,
                                   String type,
                                   Map<String, String> address) {
    }
}
