package com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.sunat;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.apisnet.JuridicalPerson;
import com.paulfernandosr.possystembackend.customer.infrastructure.adapter.output.apisnet.JuridicalPersonLocal;
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

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SunatRucScraper {
    private static final String BASE_URL = "https://e-consultaruc.sunat.gob.pe";
    private static final String SEARCH_PATH = "/cl-ti-itmrconsruc/FrameCriterioBusquedaWeb.jsp";
    private static final String SEARCH_URL = BASE_URL + SEARCH_PATH;
    private static final String RESULT_PATH = "/cl-ti-itmrconsruc/jcrS00Alias";
    private static final String RANDOM_CODE_PATH = "/cl-ti-itmrconsruc/captcha?accion=random";
    private static final String RANDOM_CODE_FALLBACK_PATH = "/cl-ti-itmrconsmulruc/captcha?accion=random";
    private static final String USER_AGENT_VALUE = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    private static final Pattern RUC_AND_NAME_PATTERN = Pattern.compile("^(\\d{11})\\s*-\\s*(.+)$");

    private final RestClient sunatRucRestClient;
    private final SunatAddressParser addressParser;

    public SunatRucScraper(@Qualifier("sunatRucRestClient") RestClient sunatRucRestClient,
                           SunatAddressParser addressParser) {
        this.sunatRucRestClient = sunatRucRestClient;
        this.addressParser = addressParser;
    }

    public Optional<JuridicalPerson> findByRuc(String ruc) {
        String normalizedRuc = onlyDigits(ruc);
        if (normalizedRuc == null || !normalizedRuc.matches("\\d{11}")) {
            log.info("SUNAT RUC: número inválido recibido: {}", ruc);
            return Optional.empty();
        }

        log.info("SUNAT RUC: iniciando consulta para {}", normalizedRuc);

        Optional<JuridicalPerson> browserResult = findByRucWithBrowser(normalizedRuc);
        if (browserResult.isPresent()) {
            return browserResult;
        }

        log.info("SUNAT RUC: Playwright no devolvió resultado. Intentando fallback RestClient para {}", normalizedRuc);
        return findByRucWithRestClient(normalizedRuc);
    }

    private Optional<JuridicalPerson> findByRucWithBrowser(String ruc) {
        try (Playwright playwright = Playwright.create()) {
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setTimeout(35_000);

            try (Browser browser = playwright.chromium().launch(launchOptions)) {
                Browser.NewContextOptions contextOptions = new Browser.NewContextOptions()
                        .setUserAgent(USER_AGENT_VALUE)
                        .setViewportSize(1280, 900)
                        .setLocale("es-PE")
                        .setIgnoreHTTPSErrors(true);

                try (BrowserContext context = browser.newContext(contextOptions)) {
                    Page page = context.newPage();
                    page.setDefaultTimeout(18_000);
                    page.setDefaultNavigationTimeout(35_000);

                    log.info("SUNAT RUC: Playwright abriendo {}", SEARCH_URL);
                    page.navigate(SEARCH_URL, new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(35_000));
                    log.info("SUNAT RUC: Playwright página inicial url={}, title={}", page.url(), safe(page.title()));

                    fillRucAndSearch(page, ruc);
                    waitAfterSearch(page);

                    String html = page.content();
                    log.info("SUNAT RUC: Playwright después de buscar url={}, htmlLength={}, hasRucResult={}, resumen={}",
                            page.url(), length(html), hasRucResult(html, ruc), debugSummary(html));

                    if (isCaptchaOrCodePage(html) && !hasRucResult(html, ruc)) {
                        log.info("SUNAT RUC: SUNAT solicitó código/captcha para {}. No se automatiza ese paso.", ruc);
                        return Optional.empty();
                    }

                    Optional<JuridicalPerson> person = parseJuridicalPerson(html, ruc);
                    if (person.isEmpty()) {
                        return Optional.empty();
                    }

                    List<JuridicalPersonLocal> annexLocations = safeFetchAnnexLocationsWithBrowser(page, ruc);
                    person.get().setAnnexLocations(annexLocations);

                    log.info("SUNAT RUC: Playwright contribuyente encontrado para {}. razonSocial={}, estado={}, condicion={}, direccionFiscal={}, anexos={}",
                            ruc,
                            safe(person.get().getBusinessName()),
                            safe(person.get().getStatus()),
                            safe(person.get().getCondition()),
                            hasText(person.get().getAddress()),
                            annexLocations.size());

                    return person;
                }
            }
        } catch (TimeoutError ex) {
            log.info("SUNAT RUC: Playwright timeout consultando {}: {}", ruc, shortMessage(ex));
            return Optional.empty();
        } catch (RuntimeException ex) {
            log.info("SUNAT RUC: Playwright no pudo consultar {}: {}", ruc, shortMessage(ex));
            return Optional.empty();
        }
    }

    private void fillRucAndSearch(Page page, String ruc) {
        clickIfExists(page, "button:has-text('Por RUC'), a:has-text('Por RUC'), input[value*='Por RUC']", 2_000);

        Locator input = firstExisting(page,
                "#txtRuc",
                "input[name='search1']",
                "input[placeholder*='RUC']",
                "input[type='text']:visible");

        input.waitFor(new Locator.WaitForOptions().setTimeout(12_000));
        input.click();
        input.fill(ruc);
        log.info("SUNAT RUC: Playwright RUC digitado={}", ruc);

        Locator button = firstExisting(page,
                "#btnAceptar",
                "button:has-text('Buscar')",
                "input[type='button'][value*='Buscar']",
                "input[type='submit'][value*='Buscar']",
                "a:has-text('Buscar')");

        button.click();
        log.info("SUNAT RUC: Playwright clic en Buscar ejecutado");
    }

    private List<JuridicalPersonLocal> safeFetchAnnexLocationsWithBrowser(Page page, String ruc) {
        try {
            String html = page.content();
            String text = SunatUbigeoResolver.normalizeKey(Jsoup.parse(html).text());
            if (text == null || (!text.contains("ESTABLECIMIENTO") && !text.contains("LOCAL ANEX"))) {
                log.info("SUNAT RUC: no se detectó botón/sección de anexos para {}", ruc);
                return List.of();
            }

            Locator annexButton = firstExistingOrNull(page,
                    "button:has-text('Establecimiento')",
                    "input[type='button'][value*='Establecimiento']",
                    "input[type='submit'][value*='Establecimiento']",
                    "a:has-text('Establecimiento')");

            if (annexButton == null) {
                log.info("SUNAT RUC: texto de anexos detectado, pero no se encontró botón clickeable para {}", ruc);
                return List.of();
            }

            annexButton.scrollIntoViewIfNeeded();
            annexButton.click();
            waitAfterSearch(page);

            List<JuridicalPersonLocal> locations = parseAnnexLocations(page.content());
            log.info("SUNAT RUC: anexos por Playwright parseados={} para {}", locations.size(), ruc);
            return locations;
        } catch (RuntimeException ex) {
            log.info("SUNAT RUC: no se pudieron cargar anexos por Playwright para {}: {}", ruc, shortMessage(ex));
            return List.of();
        }
    }

    private void waitAfterSearch(Page page) {
        try {
            page.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(12_000));
        } catch (RuntimeException ignored) {
            // No bloquea.
        }

        try {
            page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(12_000));
        } catch (RuntimeException ignored) {
            log.info("SUNAT RUC: timeout esperando networkidle; se continúa con HTML disponible");
        }

        try {
            page.waitForSelector("text=Resultado de la Búsqueda", new Page.WaitForSelectorOptions().setTimeout(8_000));
        } catch (RuntimeException ignored) {
            // Puede ser pantalla de no encontrado, validación o anexos.
        }

        try {
            page.waitForTimeout(700);
        } catch (RuntimeException ignored) {
            // No bloquea.
        }
    }

    private void clickIfExists(Page page, String selector, int timeoutMillis) {
        try {
            Locator locator = page.locator(selector).first();
            if (locator.count() > 0) {
                locator.click(new Locator.ClickOptions().setTimeout(timeoutMillis));
            }
        } catch (RuntimeException ignored) {
            // Es opcional. No bloquea.
        }
    }

    private Locator firstExisting(Page page, String... selectors) {
        Locator locator = firstExistingOrNull(page, selectors);
        if (locator == null) {
            throw new IllegalStateException("No se encontró control esperado en la página SUNAT: " + String.join(" | ", selectors));
        }
        return locator;
    }

    private Locator firstExistingOrNull(Page page, String... selectors) {
        for (String selector : selectors) {
            try {
                Locator locator = page.locator(selector).first();
                if (locator.count() > 0) {
                    return locator;
                }
            } catch (RuntimeException ignored) {
                // Prueba siguiente selector.
            }
        }
        return null;
    }

    private Optional<JuridicalPerson> findByRucWithRestClient(String ruc) {
        try {
            ResponseEntity<String> initialResponse = loadSearchPage();
            String cookieHeader = buildCookieHeader(initialResponse.getHeaders().get(HttpHeaders.SET_COOKIE));
            log.info("SUNAT RUC: RestClient página inicial status={}, cookies={}",
                    initialResponse.getStatusCode().value(), hasText(cookieHeader));

            RandomCodeResponse randomCodeResponse = getRandomCode(cookieHeader);
            cookieHeader = mergeCookieHeaders(cookieHeader, randomCodeResponse.cookieHeader());
            log.info("SUNAT RUC: RestClient numRnd obtenido={}, cookiesActualizadas={}",
                    hasText(randomCodeResponse.numRnd()), hasText(cookieHeader));

            ResponseEntity<String> searchResponse = postRucSearch(ruc, randomCodeResponse.numRnd(), cookieHeader);
            String html = searchResponse.getBody();
            log.info("SUNAT RUC: RestClient POST status={}, htmlLength={}, hasRucResult={}, resumen={}",
                    searchResponse.getStatusCode().value(), length(html), hasRucResult(html, ruc), debugSummary(html));

            if (!hasRucResult(html, ruc)) {
                searchResponse = getRucSearch(ruc, randomCodeResponse.numRnd(), cookieHeader);
                html = searchResponse.getBody();
                log.info("SUNAT RUC: RestClient GET fallback status={}, htmlLength={}, hasRucResult={}, resumen={}",
                        searchResponse.getStatusCode().value(), length(html), hasRucResult(html, ruc), debugSummary(html));
            }

            Optional<JuridicalPerson> person = parseJuridicalPerson(html, ruc);
            if (person.isEmpty()) {
                return Optional.empty();
            }

            List<JuridicalPersonLocal> annexLocations = safeFetchAnnexLocations(html, ruc, cookieHeader);
            person.get().setAnnexLocations(annexLocations);
            return person;
        } catch (RestClientException ex) {
            log.warn("SUNAT RUC: error HTTP con RestClient para {}: {}", ruc, shortMessage(ex));
            return Optional.empty();
        } catch (RuntimeException ex) {
            log.warn("SUNAT RUC: error interpretando RestClient para {}: {}", ruc, shortMessage(ex));
            return Optional.empty();
        }
    }

    private ResponseEntity<String> loadSearchPage() {
        return sunatRucRestClient.get()
                .uri(SEARCH_PATH)
                .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE)
                .retrieve()
                .toEntity(String.class);
    }

    private RandomCodeResponse getRandomCode(String cookieHeader) {
        try {
            ResponseEntity<String> response = getRandomCodeFromPath(RANDOM_CODE_PATH, cookieHeader);
            String numRnd = normalize(response.getBody());
            String newCookies = buildCookieHeader(response.getHeaders().get(HttpHeaders.SET_COOKIE));
            if (!isBlank(numRnd)) {
                return new RandomCodeResponse(numRnd, newCookies);
            }
            log.info("SUNAT RUC: captcha/random principal devolvió vacío. Intentando fallback. status={}",
                    response.getStatusCode().value());
        } catch (RestClientException ex) {
            log.info("SUNAT RUC: no se pudo obtener numRnd principal: {}", shortMessage(ex));
        }

        ResponseEntity<String> fallbackResponse = getRandomCodeFromPath(RANDOM_CODE_FALLBACK_PATH, cookieHeader);
        String fallbackNumRnd = normalize(fallbackResponse.getBody());
        String fallbackCookies = buildCookieHeader(fallbackResponse.getHeaders().get(HttpHeaders.SET_COOKIE));
        return new RandomCodeResponse(fallbackNumRnd, fallbackCookies);
    }

    private ResponseEntity<String> getRandomCodeFromPath(String path, String cookieHeader) {
        return sunatRucRestClient.get()
                .uri(path)
                .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN_VALUE + ", " + MediaType.TEXT_HTML_VALUE)
                .header(HttpHeaders.REFERER, SEARCH_URL)
                .headers(headers -> addCookie(headers, cookieHeader))
                .retrieve()
                .toEntity(String.class);
    }

    private ResponseEntity<String> getRucSearch(String ruc, String numRnd, String cookieHeader) {
        return sunatRucRestClient.get()
                .uri(uriBuilder -> uriBuilder.path(RESULT_PATH)
                        .queryParam("accion", "consPorRuc")
                        .queryParam("nroRuc", ruc)
                        .queryParam("numRnd", nullToEmpty(numRnd))
                        .queryParam("search1", ruc)
                        .queryParam("tipdoc", "1")
                        .queryParam("search2", "")
                        .queryParam("search3", "")
                        .queryParam("contexto", "ti-it")
                        .queryParam("modo", "1")
                        .queryParam("rbtnTipo", "1")
                        .queryParam("actReturn", "1")
                        .build())
                .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE)
                .header(HttpHeaders.REFERER, SEARCH_URL)
                .headers(headers -> addCookie(headers, cookieHeader))
                .retrieve()
                .toEntity(String.class);
    }

    private ResponseEntity<String> postRucSearch(String ruc, String numRnd, String cookieHeader) {
        MultiValueMap<String, String> form = buildRucForm(ruc, numRnd);

        return sunatRucRestClient.post()
                .uri(RESULT_PATH)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.TEXT_HTML)
                .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                .header(HttpHeaders.REFERER, SEARCH_URL)
                .headers(headers -> addCookie(headers, cookieHeader))
                .body(form)
                .retrieve()
                .toEntity(String.class);
    }

    private MultiValueMap<String, String> buildRucForm(String ruc, String numRnd) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("accion", "consPorRuc");
        form.add("nroRuc", ruc);
        form.add("numRnd", nullToEmpty(numRnd));
        form.add("contexto", "ti-it");
        form.add("modo", "1");
        form.add("rbtnTipo", "1");
        form.add("search1", ruc);
        form.add("tipdoc", "1");
        form.add("search2", "");
        form.add("search3", "");
        form.add("actReturn", "1");
        return form;
    }

    private Optional<JuridicalPerson> parseJuridicalPerson(String html, String requestedRuc) {
        if (isBlank(html)) {
            return Optional.empty();
        }

        Document document = Jsoup.parse(html);
        String fullText = SunatUbigeoResolver.normalizeKey(document.text());

        if (fullText == null || fullText.contains("NO REGISTRA") || fullText.contains("NO EXISTE")) {
            log.info("SUNAT RUC: respuesta indica no registrado/no existe para {}", requestedRuc);
            return Optional.empty();
        }

        Map<String, String> fields = extractFieldMap(document);
        log.info("SUNAT RUC: campos detectados para {} => {}", requestedRuc, fields.keySet());

        String rucWithName = firstNonBlank(fields.get("NUMERO DE RUC"), findRucNameInText(document.text(), requestedRuc));

        if (isBlank(rucWithName) || !rucWithName.contains(requestedRuc)) {
            log.info("SUNAT RUC: no se encontró campo Número de RUC para {}. rucWithName={}", requestedRuc, safe(rucWithName));
            return Optional.empty();
        }

        Matcher matcher = RUC_AND_NAME_PATTERN.matcher(rucWithName);
        String businessName = matcher.matches()
                ? matcher.group(2)
                : rucWithName.replace(requestedRuc, "").replace("-", "");

        if (isBlank(businessName)) {
            log.info("SUNAT RUC: no se pudo determinar razón social para {}", requestedRuc);
            return Optional.empty();
        }

        ParsedSunatAddress fiscalAddress = addressParser.parse(fields.get("DOMICILIO FISCAL"))
                .orElse(null);

        return Optional.of(JuridicalPerson.builder()
                .documentType("RUC")
                .documentNumber(requestedRuc)
                .businessName(normalize(businessName))
                .type(normalize(fields.get("TIPO CONTRIBUYENTE")))
                .status(normalize(fields.get("ESTADO DEL CONTRIBUYENTE")))
                .condition(normalize(fields.get("CONDICION DEL CONTRIBUYENTE")))
                .address(fiscalAddress == null ? null : fiscalAddress.address())
                .ubigeo(fiscalAddress == null ? null : fiscalAddress.ubigeo())
                .department(fiscalAddress == null ? null : fiscalAddress.department())
                .province(fiscalAddress == null ? null : fiscalAddress.province())
                .district(fiscalAddress == null ? null : fiscalAddress.district())
                .billingType(normalize(firstNonBlank(
                        fields.get("SISTEMA EMISION DE COMPROBANTE"),
                        fields.get("SISTEMA EMISION ELECTRONICA")
                )))
                .accountingType(normalize(fields.get("SISTEMA CONTABILIDAD")))
                .foreignTrade(normalize(fields.get("ACTIVIDAD COMERCIO EXTERIOR")))
                .economicActivity(normalize(fields.get("ACTIVIDADES ECONOMICAS")))
                .build());
    }

    private List<JuridicalPersonLocal> safeFetchAnnexLocations(String mainHtml, String ruc, String cookieHeader) {
        try {
            return fetchAnnexLocations(mainHtml, ruc, cookieHeader);
        } catch (RestClientException ex) {
            log.info("SUNAT RUC: no devolvió establecimientos anexos para {}: {}", ruc, shortMessage(ex));
            return List.of();
        } catch (RuntimeException ex) {
            log.info("SUNAT RUC: no se pudieron interpretar establecimientos anexos para {}: {}", ruc, shortMessage(ex));
            return List.of();
        }
    }

    private List<JuridicalPersonLocal> fetchAnnexLocations(String mainHtml, String ruc, String cookieHeader) {
        Document mainDocument = Jsoup.parse(Objects.toString(mainHtml, ""));

        String mainText = SunatUbigeoResolver.normalizeKey(mainDocument.text());
        if (mainText == null || (!mainText.contains("ESTABLECIMIENTO") && !mainText.contains("LOCAL ANEX"))) {
            log.info("SUNAT RUC: no se detectó botón/sección de anexos para {}", ruc);
            return List.of();
        }

        Optional<ResponseEntity<String>> byForm = postAnnexByDetectedForm(mainDocument, ruc, cookieHeader);
        if (byForm.isPresent()) {
            List<JuridicalPersonLocal> locations = parseAnnexLocations(byForm.get().getBody());
            if (!locations.isEmpty()) {
                return locations;
            }
        }

        List<JuridicalPersonLocal> locations = tryGetAnnexByFallbackParam("numRuc", ruc, cookieHeader);
        if (!locations.isEmpty()) {
            return locations;
        }

        return tryGetAnnexByFallbackParam("nroRuc", ruc, cookieHeader);
    }

    private Optional<ResponseEntity<String>> postAnnexByDetectedForm(Document document, String ruc, String cookieHeader) {
        for (Element formElement : document.select("form")) {
            String normalizedForm = normalize(formElement.text() + " " + formElement.html());
            normalizedForm = normalizedForm == null ? null : normalizedForm.toUpperCase();
            if (normalizedForm == null || (!normalizedForm.contains("ESTABLECIMIENTO") && !normalizedForm.contains("GETLOCANEX"))) {
                continue;
            }

            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            for (Element input : formElement.select("input[name]")) {
                String name = input.attr("name");
                if (isBlank(name)) continue;
                form.add(name, input.val());
            }

            setOrAdd(form, "accion", "getLocAnex");
            setOrAdd(form, "contexto", "ti-it");
            setOrAdd(form, "modo", "1");
            setOrAdd(form, "actReturn", "1");
            putRucIfPresent(form, ruc);

            String action = normalizeAction(formElement.attr("action"));

            ResponseEntity<String> response = sunatRucRestClient.post()
                    .uri(URI.create(action))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.TEXT_HTML)
                    .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                    .header(HttpHeaders.REFERER, SEARCH_URL)
                    .headers(headers -> addCookie(headers, cookieHeader))
                    .body(form)
                    .retrieve()
                    .toEntity(String.class);

            return Optional.of(response);
        }

        return Optional.empty();
    }

    private List<JuridicalPersonLocal> tryGetAnnexByFallbackParam(String rucParamName, String ruc, String cookieHeader) {
        try {
            return parseAnnexLocations(getAnnexByFallbackParam(rucParamName, ruc, cookieHeader).getBody());
        } catch (RestClientException ex) {
            log.info("SUNAT RUC: no devolvió anexos usando parámetro {} para RUC {}: {}", rucParamName, ruc, shortMessage(ex));
            return List.of();
        }
    }

    private ResponseEntity<String> getAnnexByFallbackParam(String rucParamName, String ruc, String cookieHeader) {
        return sunatRucRestClient.get()
                .uri(uriBuilder -> uriBuilder.path(RESULT_PATH)
                        .queryParam("accion", "getLocAnex")
                        .queryParam(rucParamName, ruc)
                        .queryParam("contexto", "ti-it")
                        .queryParam("modo", "1")
                        .queryParam("actReturn", "1")
                        .build())
                .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML_VALUE)
                .header(HttpHeaders.REFERER, SEARCH_URL)
                .headers(headers -> addCookie(headers, cookieHeader))
                .retrieve()
                .toEntity(String.class);
    }

    private List<JuridicalPersonLocal> parseAnnexLocations(String html) {
        if (isBlank(html)) {
            return List.of();
        }

        Document document = Jsoup.parse(html);
        List<JuridicalPersonLocal> locations = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (Element row : document.select("tr")) {
            Elements cells = row.select("td");
            if (cells.size() < 3) {
                continue;
            }

            String code = onlyDigits(cells.get(0).text());
            if (code == null || !code.matches("\\d{4}")) {
                continue;
            }

            String addressText = normalize(cells.get(2).text());
            if (isBlank(addressText)) {
                continue;
            }

            Optional<ParsedSunatAddress> parsedAddress = addressParser.parse(addressText);
            if (parsedAddress.isEmpty()) {
                continue;
            }

            ParsedSunatAddress address = parsedAddress.get();
            String key = (Objects.toString(address.address(), "") + "|" + Objects.toString(address.ubigeo(), ""))
                    .toLowerCase();

            if (!seen.add(key)) {
                continue;
            }

            locations.add(JuridicalPersonLocal.builder()
                    .address(address.address())
                    .ubigeo(address.ubigeo())
                    .department(address.department())
                    .province(address.province())
                    .district(address.district())
                    .build());
        }

        return locations;
    }

    private Map<String, String> extractFieldMap(Document document) {
        Map<String, String> fields = new LinkedHashMap<>();

        extractPairsFromTables(document, fields);
        extractPairsFromBootstrapRows(document, fields);
        extractRegexFallbacks(document.text(), fields);
        normalizeAliases(fields);

        return fields;
    }

    private void extractPairsFromTables(Document document, Map<String, String> fields) {
        for (Element row : document.select("tr")) {
            Elements cells = row.select("> td");
            if (cells.isEmpty()) {
                cells = row.select("td");
            }

            List<String> values = cells.stream()
                    .map(Element::text)
                    .map(this::normalize)
                    .filter(value -> !isBlank(value))
                    .toList();

            putPairs(values, fields);
        }
    }

    private void extractPairsFromBootstrapRows(Document document, Map<String, String> fields) {
        for (Element row : document.select(".row")) {
            Elements columns = row.select("> div[class*=col-], > label[class*=col-], > p[class*=col-], > span[class*=col-]");
            if (columns.size() < 2) {
                continue;
            }

            List<String> values = columns.stream()
                    .map(Element::text)
                    .map(this::normalize)
                    .filter(value -> !isBlank(value))
                    .toList();

            putPairs(values, fields);
        }
    }

    private void putPairs(List<String> values, Map<String, String> fields) {
        if (values == null || values.size() < 2) {
            return;
        }

        for (int i = 0; i < values.size() - 1; i += 2) {
            String label = normalizeLabel(values.get(i));
            String value = values.get(i + 1);
            if (!isBlank(label) && !isBlank(value)) {
                fields.putIfAbsent(label, value);
            }
        }
    }

    private void extractRegexFallbacks(String text, Map<String, String> fields) {
        putIfAbsent(fields, "NUMERO DE RUC", regexValue(text,
                "Número\\s+de\\s+RUC\\s*:\\s*(\\d{11}\\s*-\\s*.+?)(?=\\s+Tipo\\s+Contribuyente\\s*:|\\s+Nombre\\s+Comercial\\s*:|$)"));
        putIfAbsent(fields, "TIPO CONTRIBUYENTE", regexValue(text,
                "Tipo\\s+Contribuyente\\s*:\\s*(.+?)(?=\\s+Tipo\\s+de\\s+Documento\\s*:|\\s+Nombre\\s+Comercial\\s*:|\\s+Fecha\\s+de\\s+Inscripción\\s*:|$)"));
        putIfAbsent(fields, "NOMBRE COMERCIAL", regexValue(text,
                "Nombre\\s+Comercial\\s*:\\s*(.+?)(?=\\s+Fecha\\s+de\\s+Inscripción\\s*:|$)"));
        putIfAbsent(fields, "ESTADO DEL CONTRIBUYENTE", regexValue(text,
                "Estado\\s+del\\s+Contribuyente\\s*:\\s*(.+?)(?=\\s+Condición\\s+del\\s+Contribuyente\\s*:|$)"));
        putIfAbsent(fields, "CONDICION DEL CONTRIBUYENTE", regexValue(text,
                "Condición\\s+del\\s+Contribuyente\\s*:\\s*(.+?)(?=\\s+Domicilio\\s+Fiscal\\s*:|$)"));
        putIfAbsent(fields, "DOMICILIO FISCAL", regexValue(text,
                "Domicilio\\s+Fiscal\\s*:\\s*(.+?)(?=\\s+Sistema\\s+Emisión|\\s+Sistema\\s+Emision|$)"));
        putIfAbsent(fields, "SISTEMA CONTABILIDAD", regexValue(text,
                "Sistema\\s+Contabilidad\\s*:\\s*(.+?)(?=\\s+Actividad\\(es\\)\\s+Económica|\\s+Actividad\\(es\\)\\s+Economica|$)"));
        putIfAbsent(fields, "ACTIVIDAD COMERCIO EXTERIOR", regexValue(text,
                "Actividad\\s+Comercio\\s+Exterior\\s*:\\s*(.+?)(?=\\s+Sistema\\s+Contabilidad|$)"));
        putIfAbsent(fields, "ACTIVIDADES ECONOMICAS", regexValue(text,
                "Actividad\\(es\\)\\s+Económica\\(s\\)\\s*:\\s*(.+?)(?=\\s+Comprobantes\\s+de\\s+Pago|$)"));
        putIfAbsent(fields, "SISTEMA EMISION ELECTRONICA", regexValue(text,
                "Sistema\\s+de\\s+Emisión\\s+Electrónica\\s*:\\s*(.+?)(?=\\s+Emisor\\s+electrónico\\s+desde|$)"));
    }

    private String regexValue(String text, String regex) {
        if (isBlank(text)) {
            return null;
        }

        Matcher matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL | Pattern.UNICODE_CASE)
                .matcher(text.replace('\u00A0', ' '));
        if (!matcher.find()) {
            return null;
        }
        return normalize(matcher.group(1));
    }

    private void putIfAbsent(Map<String, String> fields, String key, String value) {
        if (!fields.containsKey(key) && !isBlank(value)) {
            fields.put(key, value);
        }
    }

    private void normalizeAliases(Map<String, String> fields) {
        alias(fields, "NUMERO DE RUC", "NUMERO RUC");
        alias(fields, "NUMERO DE RUC", "NÚMERO DE RUC");
        alias(fields, "TIPO CONTRIBUYENTE", "TIPO CONTRIBUYENTE");
        alias(fields, "ESTADO DEL CONTRIBUYENTE", "ESTADO CONTRIBUYENTE");
        alias(fields, "CONDICION DEL CONTRIBUYENTE", "CONDICIÓN DEL CONTRIBUYENTE");
        alias(fields, "CONDICION DEL CONTRIBUYENTE", "CONDICION CONTRIBUYENTE");
        alias(fields, "ACTIVIDADES ECONOMICAS", "ACTIVIDAD ES ECONOMICA S");
        alias(fields, "SISTEMA EMISION ELECTRONICA", "SISTEMA DE EMISION ELECTRONICA");
    }

    private void alias(Map<String, String> fields, String canonical, String alias) {
        if (fields.containsKey(canonical)) {
            return;
        }
        String value = fields.get(alias);
        if (!isBlank(value)) {
            fields.put(canonical, value);
        }
    }

    private boolean hasRucResult(String html, String ruc) {
        if (isBlank(html)) {
            return false;
        }

        String text = SunatUbigeoResolver.normalizeKey(Jsoup.parse(html).text());
        return text != null
                && text.contains(ruc)
                && text.contains("NUMERO DE RUC")
                && !text.contains("NO REGISTRA")
                && !text.contains("NO EXISTE");
    }

    private boolean isCaptchaOrCodePage(String html) {
        if (isBlank(html)) {
            return false;
        }
        String text = SunatUbigeoResolver.normalizeKey(Jsoup.parse(html).text());
        return text != null && (text.contains("INGRESE EL CODIGO MOSTRADO") || text.contains("CAPTCHA"));
    }

    private String normalizeAction(String action) {
        if (isBlank(action)) {
            return RESULT_PATH;
        }

        String trimmed = action.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return URI.create(trimmed).toString();
        }

        if (trimmed.startsWith("/")) {
            return trimmed;
        }

        return "/cl-ti-itmrconsruc/" + trimmed;
    }

    private void setOrAdd(MultiValueMap<String, String> form, String key, String value) {
        if (form.containsKey(key)) {
            form.set(key, value);
        } else {
            form.add(key, value);
        }
    }

    private void putRucIfPresent(MultiValueMap<String, String> form, String ruc) {
        if (form.containsKey("numRuc")) form.set("numRuc", ruc);
        if (form.containsKey("nroRuc")) form.set("nroRuc", ruc);
        if (form.containsKey("search1")) form.set("search1", ruc);

        if (!form.containsKey("numRuc") && !form.containsKey("nroRuc")) {
            form.add("numRuc", ruc);
        }
    }

    private void addCookie(HttpHeaders headers, String cookieHeader) {
        if (!isBlank(cookieHeader)) {
            headers.add(HttpHeaders.COOKIE, cookieHeader);
        }
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

    private String mergeCookieHeaders(String current, String incoming) {
        if (isBlank(current)) return incoming;
        if (isBlank(incoming)) return current;
        return current + "; " + incoming;
    }

    private String findRucNameInText(String text, String ruc) {
        if (isBlank(text) || isBlank(ruc)) {
            return null;
        }

        String normalizedText = text.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
        Matcher matcher = Pattern.compile(Pattern.quote(ruc) + "\\s*-?\\s*(.+?)(?=\\s+Tipo\\s+Contribuyente\\s*:|\\s+Nombre\\s+Comercial\\s*:|$)",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(normalizedText);
        return matcher.find() ? ruc + " - " + matcher.group(1).trim() : null;
    }

    private String normalizeLabel(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }

        return SunatUbigeoResolver.normalizeKey(normalized.replace(":", " "));
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        return normalized.isBlank() || "-".equals(normalized) ? null : normalized;
    }

    private String onlyDigits(String value) {
        if (value == null) {
            return null;
        }

        String digits = value.replaceAll("\\D", "");
        return digits.isBlank() ? null : digits;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }

        return null;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private String debugSummary(String html) {
        if (isBlank(html)) {
            return "-";
        }
        String text = Jsoup.parse(html).text().replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
        return text.length() <= 320 ? text : text.substring(0, 320);
    }

    private String safe(String value) {
        return value == null ? "-" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String shortMessage(Throwable ex) {
        if (ex == null || ex.getMessage() == null) {
            return "-";
        }
        String message = ex.getMessage().replaceAll("\\s+", " ").trim();
        return message.length() <= 300 ? message : message.substring(0, 300);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record RandomCodeResponse(String numRnd, String cookieHeader) {
    }
}
