package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.eldni;

import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.apisnet.NaturalPerson;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
public class EldniDniScraper {
    private static final String SEARCH_PATH = "/pe/buscar-datos-por-dni";
    private static final String REFERER = "https://eldni.com" + SEARCH_PATH;
    private static final String USER_AGENT_VALUE = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    private final RestClient eldniRestClient;

    public EldniDniScraper(@Qualifier("eldniRestClient") RestClient eldniRestClient) {
        this.eldniRestClient = eldniRestClient;
    }

    public Optional<NaturalPerson> findByDni(String dni) {
        String normalizedDni = onlyDigits(dni);
        if (normalizedDni == null || !normalizedDni.matches("\\d{8}")) {
            return Optional.empty();
        }

        try {
            ResponseEntity<String> initialResponse = loadSearchPage();
            String csrfToken = extractCsrfToken(initialResponse.getBody());
            String cookieHeader = buildCookieHeader(initialResponse.getHeaders().get(HttpHeaders.SET_COOKIE));

            ResponseEntity<String> searchResponse = postDniSearch(normalizedDni, csrfToken, cookieHeader);
            return parseNaturalPerson(searchResponse.getBody(), normalizedDni);
        } catch (RestClientException ex) {
            log.warn("No se pudo consultar eldni.com para DNI {}: {}", normalizedDni, ex.getMessage());
            return Optional.empty();
        } catch (RuntimeException ex) {
            log.warn("No se pudo interpretar la respuesta de eldni.com para DNI {}: {}", normalizedDni, ex.getMessage());
            return Optional.empty();
        }
    }

    private ResponseEntity<String> loadSearchPage() {
        return eldniRestClient.get()
                .uri(SEARCH_PATH)
                .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE)
                .retrieve()
                .toEntity(String.class);
    }

    private ResponseEntity<String> postDniSearch(String dni, String csrfToken, String cookieHeader) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        if (!isBlank(csrfToken)) {
            form.add("_token", csrfToken);
        }
        form.add("dni", dni);

        return eldniRestClient.post()
                .uri(SEARCH_PATH)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.TEXT_HTML)
                .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                .header(HttpHeaders.REFERER, REFERER)
                .headers(headers -> {
                    if (!isBlank(cookieHeader)) {
                        headers.add(HttpHeaders.COOKIE, cookieHeader);
                    }
                })
                .body(form)
                .retrieve()
                .toEntity(String.class);
    }

    private Optional<NaturalPerson> parseNaturalPerson(String html, String requestedDni) {
        if (isBlank(html)) {
            return Optional.empty();
        }

        Document document = Jsoup.parse(html);

        for (Element row : document.select("table tr")) {
            Elements cells = row.select("td");
            if (cells.size() < 4) {
                continue;
            }

            String rowDni = onlyDigits(cells.get(0).text());
            if (!requestedDni.equals(rowDni)) {
                continue;
            }

            String givenNames = normalize(cells.get(1).text());
            String lastName = normalize(cells.get(2).text());
            String secondLastName = normalize(cells.get(3).text());

            if (isBlank(givenNames) || isBlank(lastName) || isBlank(secondLastName)) {
                return Optional.empty();
            }

            return Optional.of(NaturalPerson.builder()
                    .documentNumber(requestedDni)
                    .documentType("DNI")
                    .givenNames(givenNames)
                    .lastName(lastName)
                    .secondLastName(secondLastName)
                    .build());
        }

        return Optional.empty();
    }

    private String extractCsrfToken(String html) {
        if (isBlank(html)) {
            return null;
        }

        Document document = Jsoup.parse(html);

        Element csrfInput = document.selectFirst("input[name=_token]");
        if (csrfInput != null && !isBlank(csrfInput.val())) {
            return csrfInput.val();
        }

        Element csrfMeta = document.selectFirst("meta[name=csrf-token]");
        if (csrfMeta != null && !isBlank(csrfMeta.attr("content"))) {
            return csrfMeta.attr("content");
        }

        return null;
    }

    private String buildCookieHeader(List<String> setCookieHeaders) {
        if (setCookieHeaders == null || setCookieHeaders.isEmpty()) {
            return null;
        }

        return setCookieHeaders.stream()
                .filter(cookie -> !isBlank(cookie))
                .map(cookie -> cookie.split(";", 2)[0])
                .filter(cookie -> !isBlank(cookie))
                .collect(Collectors.joining("; "));
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace('\u00A0', ' ').trim().replaceAll("\\s+", " ");
        return normalized.isBlank() || "-".equals(normalized) ? null : normalized.toUpperCase();
    }

    private String onlyDigits(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        return digits.isBlank() ? null : digits;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
