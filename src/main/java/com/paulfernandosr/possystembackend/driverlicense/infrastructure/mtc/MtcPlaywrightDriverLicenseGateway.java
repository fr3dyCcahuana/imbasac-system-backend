package com.paulfernandosr.possystembackend.driverlicense.infrastructure.mtc;

import com.microsoft.playwright.Page;
import com.paulfernandosr.possystembackend.driverlicense.domain.DriverLicenseGateway;
import com.paulfernandosr.possystembackend.driverlicense.domain.DriverLicenseQuery;
import com.paulfernandosr.possystembackend.driverlicense.domain.DriverLicenseResult;
import com.paulfernandosr.possystembackend.driverlicense.domain.DriverLicenseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MtcPlaywrightDriverLicenseGateway implements DriverLicenseGateway {

    private static final Logger log = LoggerFactory.getLogger(MtcPlaywrightDriverLicenseGateway.class);

    private final MtcCaptchaService captchaService;
    private final MtcDebugSupport debugSupport;

    public MtcPlaywrightDriverLicenseGateway(MtcCaptchaService captchaService,
                                             MtcDebugSupport debugSupport) {
        this.captchaService = captchaService;
        this.debugSupport = debugSupport;
    }

    @Override
    public DriverLicenseResult check(DriverLicenseQuery query) {
        log.info("[mtc][check] inicio. sessionId={}, documentType={}, documentNumber={}",
                query.sessionId(), query.documentType(), query.documentNumber());

        if (!captchaService.isSessionActive(query.sessionId())) {
            return DriverLicenseResult.error("La sesión del captcha expiró. Genera uno nuevo.");
        }

        MtcSession session = captchaService.getSession(query.sessionId());
        if (session == null) {
            return DriverLicenseResult.error("La sesión Playwright ya no está disponible. Genera un nuevo captcha.");
        }

        Page page = session.getPage();
        if (page.isClosed()) {
            captchaService.destroySession(query.sessionId());
            return DriverLicenseResult.error("La página del MTC ya fue cerrada. Genera un nuevo captcha.");
        }

        attachNetworkLogging(page, query.sessionId());

        try {
            debugSupport.logSelectors(page, query.sessionId(), "before-fill");
            debugSupport.logFormState(page, query.sessionId(), "before-fill");

            page.locator("#rbtnlBuqueda_0").check();
            log.info("[mtc][check] radio búsqueda documento marcado. sessionId={}", query.sessionId());

            String mappedDocType = mapDocumentType(query.documentType());
            page.evaluate("""
                (value) => {
                    const ddl = document.getElementById('ddlTipoDocumento');
                    ddl.value = value;
                }
            """, mappedDocType);
            log.info("[mtc][check] tipo documento seleccionado. sessionId={}, value={}",
                    query.sessionId(), mappedDocType);

            page.locator("#txtNroDocumento").fill(query.documentNumber());
            log.info("[mtc][check] nro documento ingresado. sessionId={}, documentNumber={}",
                    query.sessionId(), query.documentNumber());

            page.locator("#txtCaptcha").fill(query.captchaText());
            log.info("[mtc][check] captcha ingresado. sessionId={}, captchaLength={}",
                    query.sessionId(), query.captchaText() == null ? 0 : query.captchaText().length());

            debugSupport.logFormState(page, query.sessionId(), "after-fill");
            debugSupport.dumpPageState(page, query.sessionId(), "before-submit");

            String bodyBefore = safeBody(page);
            int bodyBeforeLength = bodyBefore.length();

            log.info("[mtc][check] click real en ibtnBusqNroDoc. sessionId={}", query.sessionId());
            page.evaluate("__doPostBack('ibtnBusqNroDoc','')");

            waitForResult(page, query.sessionId(), bodyBeforeLength);

            String body = safeBody(page);
            String bodyUpper = body.toUpperCase(Locale.ROOT);

            debugSupport.logFormState(page, query.sessionId(), "after-submit");
            debugSupport.logBodySummary(page, query.sessionId(), "after-submit");

            log.info("[mtc][check] body leído. sessionId={}, bodyLength={}",
                    query.sessionId(), body.length());

            log.info("[mtc][check] flags. sessionId={}, hasMainResult={}, hasCaptchaError={}, hasNotFound={}",
                    query.sessionId(),
                    hasMainResult(bodyUpper),
                    isCaptchaError(bodyUpper),
                    hasNotFoundMessage(bodyUpper));

            if (isCaptchaError(bodyUpper)) {
                debugSupport.dumpPageState(page, query.sessionId(), "captcha-error");
                captchaService.destroySession(query.sessionId());
                return DriverLicenseResult.error("Captcha incorrecto.");
            }

            if (bodyUpper.contains("INGRESE NÚMERO DE DOCUMENTO") || bodyUpper.contains("INGRESE NUMERO DE DOCUMENTO")) {
                debugSupport.dumpPageState(page, query.sessionId(), "missing-document");
                captchaService.destroySession(query.sessionId());
                return DriverLicenseResult.error("El MTC respondió que falta ingresar el número de documento.");
            }

            if (hasMainResult(bodyUpper)) {
                DriverLicenseResult result = buildSuccessResult(query, page, body);
                debugSupport.dumpPageState(page, query.sessionId(), "success-result");
                captchaService.destroySession(query.sessionId());
                return result;
            }

            if (hasNotFoundMessage(bodyUpper)) {
                debugSupport.dumpPageState(page, query.sessionId(), "not-found");
                captchaService.destroySession(query.sessionId());
                return DriverLicenseResult.notFound(
                        "No se encontró información para el documento consultado.",
                        query.documentType(),
                        query.documentNumber()
                );
            }

            debugSupport.dumpPageState(page, query.sessionId(), "unknown-result");
            captchaService.destroySession(query.sessionId());
            return DriverLicenseResult.error("No fue posible determinar el resultado de la consulta en el MTC.");
        } catch (Exception e) {
            log.error("[mtc][check] error. sessionId={}, message={}", query.sessionId(), e.getMessage(), e);
            debugSupport.dumpPageState(page, query.sessionId(), "check-error");
            captchaService.destroySession(query.sessionId());
            return DriverLicenseResult.error("Error Playwright/MTC: " + e.getMessage());
        }
    }

    private void attachNetworkLogging(Page page, String sessionId) {
        page.onRequest(request -> log.info("[mtc][net][request] sessionId={}, method={}, url={}",
                sessionId, request.method(), request.url()));

        page.onResponse(response -> log.info("[mtc][net][response] sessionId={}, status={}, url={}",
                sessionId, response.status(), response.url()));

        page.onRequestFailed(request -> log.warn("[mtc][net][failed] sessionId={}, method={}, url={}, error={}",
                sessionId, request.method(), request.url(), request.failure()));
    }

    private void waitForResult(Page page, String sessionId, int bodyBeforeLength) {
        log.info("[mtc][check] waitForResult start. sessionId={}, url={}, bodyBeforeLength={}",
                sessionId, page.url(), bodyBeforeLength);

        page.waitForTimeout(3000);

        for (int i = 1; i <= 12; i++) {
            page.waitForTimeout(500);

            String body = safeBody(page);
            String bodyUpper = body.toUpperCase(Locale.ROOT);

            boolean changed = body.length() != bodyBeforeLength;

            boolean hasMain = hasMainResult(bodyUpper);
            boolean hasCaptcha = isCaptchaError(bodyUpper);
            boolean hasNotFound = hasNotFoundMessage(bodyUpper);
            boolean hasMissingDocModal = bodyUpper.contains("INGRESE NÚMERO DE DOCUMENTO")
                    || bodyUpper.contains("INGRESE NUMERO DE DOCUMENTO");

            log.info("[mtc][check] wait loop. sessionId={}, attempt={}, bodyLength={}, changed={}, hasMainResult={}, hasCaptchaError={}, hasNotFound={}, hasMissingDocModal={}",
                    sessionId, i, body.length(), changed, hasMain, hasCaptcha, hasNotFound, hasMissingDocModal);

            if (hasMain || hasCaptcha || hasNotFound || hasMissingDocModal || changed) {
                break;
            }
        }

        log.info("[mtc][check] waitForResult end. sessionId={}, url={}", sessionId, page.url());
    }

    private DriverLicenseResult buildSuccessResult(DriverLicenseQuery query, Page page, String body) {
        String fullName = extractLineValue(body, "CONSULTA DEL ADMINISTRADO");
        String documentNumber = firstNonBlank(
                extractLineValue(body, "NRO. DE DOCUMENTO DE IDENTIDAD"),
                query.documentNumber()
        );
        String licenseNumber = extractLineValue(body, "NRO. DE LICENCIA");
        String category = extractLineValue(body, "CLASE Y CATEGORIA");
        String validUntil = extractLineValue(body, "VIGENTE HASTA");
        String licenseStatusText = extractLineValue(body, "ESTADO DE LA LICENCIA");
        String verySeriousFaults = extractLineValue(body, "MUY GRAVE(S)");
        String seriousFaults = extractLineValue(body, "GRAVE(S)");
        String accumulatedPoints = extractLineValue(body, "SUS PUNTOS FIRMES ACUMULADOS SON");
        String remainingPointsToMax = extractRemainingPoints(body);
        String accumulatedInfractions = extractLineValue(body, "INFRACCIONES ACUMULADAS");

        List<String> procedures = extractTableRows(page, "#gbtramite");
        List<String> infractions = extractInfractionsTable(page);

        log.info("[mtc][check] parse success. sessionId={}, fullName={}, licenseNumber={}, category={}, validUntil={}, status={}",
                query.sessionId(), fullName, licenseNumber, category, validUntil, licenseStatusText);

        return new DriverLicenseResult(
                true,
                mapStatus(licenseStatusText),
                "Consulta realizada correctamente.",
                fullName,
                query.documentType(),
                documentNumber,
                licenseNumber,
                category,
                validUntil,
                verySeriousFaults,
                seriousFaults,
                accumulatedPoints,
                remainingPointsToMax,
                accumulatedInfractions,
                infractions,
                procedures,
                "MTC_WEB",
                false
        );
    }

    private String safeBody(Page page) {
        String body = page.locator("body").innerText();
        if (body == null) {
            body = "";
        }
        return body.replace('\u00A0', ' ').replace("\r", "");
    }

    private boolean hasMainResult(String bodyUpper) {
        return bodyUpper.contains("CONSULTA DEL ADMINISTRADO")
                || bodyUpper.contains("ESTADO DE LA LICENCIA")
                || bodyUpper.contains("NRO. DE LICENCIA")
                || bodyUpper.contains("CLASE Y CATEGORIA")
                || bodyUpper.contains("VIGENTE HASTA");
    }

    private boolean hasNotFoundMessage(String bodyUpper) {
        return bodyUpper.contains("NO REGISTRA INFORMACION")
                || bodyUpper.contains("NO EXISTE INFORMACION")
                || bodyUpper.contains("NO SE ENCONTRARON DATOS")
                || bodyUpper.contains("NO SE ENCONTRO INFORMACION");
    }

    private boolean isCaptchaError(String bodyUpper) {
        return bodyUpper.contains("CAPTCHA INCORRECTO")
                || bodyUpper.contains("INGRESE CORRECTAMENTE EL CAPTCHA")
                || bodyUpper.contains("CODIGO CAPTCHA INCORRECTO")
                || (bodyUpper.contains("CAPTCHA") && bodyUpper.contains("INCORRECT"));
    }

    private String mapDocumentType(String documentType) {
        return switch (documentType.toUpperCase(Locale.ROOT)) {
            case "DNI" -> "2";
            case "CE" -> "4";
            case "CS", "CARNET DE SOLICITANTE" -> "5";
            case "TI" -> "13";
            case "PTP", "CPP" -> "14";
            default -> throw new IllegalArgumentException("Tipo de documento no soportado: " + documentType);
        };
    }

    private DriverLicenseStatus mapStatus(String raw) {
        if (!StringUtils.hasText(raw)) {
            return DriverLicenseStatus.ERROR;
        }

        String value = raw.toUpperCase(Locale.ROOT);

        if (value.contains("VIGENTE")) return DriverLicenseStatus.VIGENTE;
        if (value.contains("VENC")) return DriverLicenseStatus.VENCIDA;
        if (value.contains("SUSP")) return DriverLicenseStatus.SUSPENDIDA;
        if (value.contains("CANCEL")) return DriverLicenseStatus.CANCELADA;

        return DriverLicenseStatus.ERROR;
    }

    private String extractLineValue(String text, String label) {
        String normalized = text.replace("\r", "");
        Pattern pattern = Pattern.compile(Pattern.quote(label) + "\\s*:?\\s*(.+)");
        Matcher matcher = pattern.matcher(normalized);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String extractRemainingPoints(String body) {
        Pattern pattern = Pattern.compile("LE FALTA\\s+(\\d+)\\s+PUNTOS", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private List<String> extractTableRows(Page page, String tableSelector) {
        List<String> rows = new ArrayList<>();

        try {
            var rowsLocator = page.locator(tableSelector + " tr");
            int count = rowsLocator.count();

            for (int i = 0; i < count; i++) {
                String rowText = rowsLocator.nth(i).innerText();
                if (rowText != null) {
                    rowText = rowText.trim();
                }
                if (StringUtils.hasText(rowText)) {
                    rows.add(rowText);
                }
            }
        } catch (Exception e) {
            log.warn("[mtc][check] extractTableRows warning. selector={}, error={}", tableSelector, e.getMessage());
        }

        return rows;
    }

    private List<String> extractInfractionsTable(Page page) {
        List<String> result = new ArrayList<>();

        try {
            var tables = page.locator("table");
            int tableCount = tables.count();

            for (int i = 0; i < tableCount; i++) {
                var table = tables.nth(i);
                String tableText = table.innerText();
                if (!StringUtils.hasText(tableText)) {
                    continue;
                }

                String upper = tableText.toUpperCase(Locale.ROOT);
                if (upper.contains("PAPELETA") && upper.contains("PUNTOS FIRMES")) {
                    var rows = table.locator("tr");
                    int rowCount = rows.count();
                    for (int j = 0; j < rowCount; j++) {
                        String rowText = rows.nth(j).innerText();
                        if (StringUtils.hasText(rowText)) {
                            result.add(rowText.trim());
                        }
                    }
                    return result;
                }
            }
        } catch (Exception e) {
            log.warn("[mtc][check] extractInfractionsTable warning. error={}", e.getMessage());
        }

        return result;
    }
}