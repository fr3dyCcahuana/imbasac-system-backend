package com.paulfernandosr.possystembackend.driverlicense.infrastructure.mtc;

import com.microsoft.playwright.Locator;
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
            ControlledModalError controlledError = detectControlledError(page, bodyUpper);

            debugSupport.logFormState(page, query.sessionId(), "after-submit");
            debugSupport.logBodySummary(page, query.sessionId(), "after-submit");

            log.info("[mtc][check] body leído. sessionId={}, bodyLength={}",
                    query.sessionId(), body.length());

            boolean hasMainResult = hasMainResult(bodyUpper);
            boolean hasNotFound = hasNotFoundMessage(bodyUpper);

            log.info("[mtc][check] flags. sessionId={}, hasMainResult={}, hasControlledError={}, controlledErrorCode={}, hasNotFound={}",
                    query.sessionId(),
                    hasMainResult,
                    controlledError != null,
                    controlledError == null ? null : controlledError.errorCode(),
                    hasNotFound);

            if (hasMainResult) {
                DriverLicenseResult result = buildSuccessResult(query, page, body);
                debugSupport.dumpPageState(page, query.sessionId(), "success-result");
                captchaService.destroySession(query.sessionId());
                return result;
            }

            if (controlledError != null) {
                debugSupport.dumpPageState(page, query.sessionId(), "controlled-error-" + controlledError.errorCode());
                dismissModalIfPresent(page, query.sessionId());
                captchaService.destroySession(query.sessionId());
                return toControlledResult(query, controlledError);
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

    private DriverLicenseResult toControlledResult(DriverLicenseQuery query, ControlledModalError controlledError) {
        if (controlledError.status() == DriverLicenseStatus.CAPTCHA_INVALIDO) {
            return DriverLicenseResult.captchaInvalid(
                    controlledError.message(),
                    query.documentType(),
                    query.documentNumber()
            );
        }

        return DriverLicenseResult.controlledError(
                controlledError.message(),
                query.documentType(),
                query.documentNumber(),
                controlledError.errorCode(),
                controlledError.captchaRequired()
        );
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
            ControlledModalError controlledError = detectControlledError(page, bodyUpper);

            boolean changed = body.length() != bodyBeforeLength;
            boolean hasMain = hasMainResult(bodyUpper);
            boolean hasControlled = controlledError != null;
            boolean hasNotFound = hasNotFoundMessage(bodyUpper);

            log.info("[mtc][check] wait loop. sessionId={}, attempt={}, bodyLength={}, changed={}, hasMainResult={}, hasControlledError={}, controlledErrorCode={}, hasNotFound={}",
                    sessionId, i, body.length(), changed, hasMain, hasControlled,
                    controlledError == null ? null : controlledError.errorCode(), hasNotFound);

            if (hasMain || hasControlled || hasNotFound || changed) {
                break;
            }
        }

        log.info("[mtc][check] waitForResult end. sessionId={}, url={}", sessionId, page.url());
    }

    private DriverLicenseResult buildSuccessResult(DriverLicenseQuery query, Page page, String body) {
        String fullName = sanitizeFieldValue(extractLineValue(body, "CONSULTA DEL ADMINISTRADO"));
        String documentNumber = firstNonBlank(
                sanitizeFieldValue(extractLineValue(body, "NRO. DE DOCUMENTO DE IDENTIDAD")),
                query.documentNumber()
        );
        String licenseNumber = sanitizeFieldValue(extractLineValue(body, "NRO. DE LICENCIA"));
        String category = sanitizeFieldValue(extractLineValue(body, "CLASE Y CATEGORIA"));
        String validUntil = sanitizeFieldValue(extractLineValue(body, "VIGENTE HASTA"));
        String licenseStatusText = sanitizeFieldValue(extractLineValue(body, "ESTADO DE LA LICENCIA"));
        String verySeriousFaults = sanitizeFieldValue(extractLineValue(body, "MUY GRAVE(S)"));
        String seriousFaults = sanitizeFieldValue(extractLineValue(body, "GRAVE(S)"));
        String accumulatedPoints = sanitizeFieldValue(extractLineValue(body, "SUS PUNTOS FIRMES ACUMULADOS SON"));
        String remainingPointsToMax = sanitizeFieldValue(extractRemainingPoints(body));
        String accumulatedInfractions = sanitizeFieldValue(extractLineValue(body, "INFRACCIONES ACUMULADAS"));

        List<String> procedures = extractTableRows(page, "#gbtramite");
        List<String> infractions = extractInfractionsTable(page);

        log.info("[mtc][check] parse success. sessionId={}, fullName={}, licenseNumber={}, category={}, validUntil={}, status={}",
                query.sessionId(), fullName, licenseNumber, category, validUntil, licenseStatusText);

        DriverLicenseStatus resolvedStatus = mapStatus(licenseStatusText);
        String resolvedMessage = resolvedStatus == DriverLicenseStatus.SIN_LICENCIA
                ? "La persona consultada no registra licencia."
                : "Consulta realizada correctamente.";

        return new DriverLicenseResult(
                true,
                resolvedStatus,
                resolvedMessage,
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
                false,
                null,
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

    private ControlledModalError detectControlledError(Page page, String bodyUpper) {
        String modalMessage = extractModalMessage(page);
        String modalUpper = modalMessage == null ? null : modalMessage.toUpperCase(Locale.ROOT);

        if (containsCaptchaError(bodyUpper) || containsCaptchaError(modalUpper)) {
            String rawMessage = StringUtils.hasText(modalMessage)
                    ? modalMessage
                    : "El código captcha no coincide con la imagen. Genera un nuevo captcha e inténtalo nuevamente.";
            return new ControlledModalError(
                    DriverLicenseStatus.CAPTCHA_INVALIDO,
                    buildFriendlyControlledMessage(rawMessage),
                    "MTC_CAPTCHA_INVALIDO",
                    true
            );
        }

        if (containsMissingDocumentError(bodyUpper) || containsMissingDocumentError(modalUpper)) {
            String rawMessage = StringUtils.hasText(modalMessage)
                    ? modalMessage
                    : "Ingrese número de documento.";
            return new ControlledModalError(
                    DriverLicenseStatus.ERROR_CONTROLADO,
                    buildFriendlyControlledMessage(rawMessage),
                    resolveControlledErrorCode(rawMessage),
                    false
            );
        }

        if (StringUtils.hasText(modalMessage)) {
            String friendlyMessage = buildFriendlyControlledMessage(modalMessage);
            if (!StringUtils.hasText(friendlyMessage)
                    || "Aceptar".equalsIgnoreCase(friendlyMessage)
                    || "OK".equalsIgnoreCase(friendlyMessage)) {
                return null;
            }
            return new ControlledModalError(
                    DriverLicenseStatus.ERROR_CONTROLADO,
                    friendlyMessage,
                    resolveControlledErrorCode(modalMessage),
                    false
            );
        }

        return null;
    }

    private boolean containsCaptchaError(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return value.contains("CAPTCHA INCORRECTO")
                || value.contains("INGRESE CORRECTAMENTE EL CAPTCHA")
                || value.contains("CODIGO CAPTCHA INCORRECTO")
                || value.contains("CÓDIGO CAPTCHA INCORRECTO")
                || value.contains("EL CODIGO CAPTCHA NO COINCIDE CON LA IMAGEN")
                || value.contains("EL CÓDIGO CAPTCHA NO COINCIDE CON LA IMAGEN")
                || (value.contains("CAPTCHA") && value.contains("INCORRECT"))
                || (value.contains("CAPTCHA") && value.contains("NO COINCIDE"));
    }

    private boolean containsMissingDocumentError(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return value.contains("INGRESE NÚMERO DE DOCUMENTO")
                || value.contains("INGRESE NUMERO DE DOCUMENTO")
                || value.contains("INGRESE EL NÚMERO DE DOCUMENTO")
                || value.contains("INGRESE EL NUMERO DE DOCUMENTO");
    }

    private String extractModalMessage(Page page) {
        try {
            Locator modalBodies = page.locator(".modal-body, .bootbox-body, .swal2-html-container");
            int count = modalBodies.count();
            for (int i = 0; i < count; i++) {
                Locator modal = modalBodies.nth(i);
                try {
                    if (!modal.isVisible()) {
                        continue;
                    }
                } catch (Exception ignored) {
                    continue;
                }

                String text = modal.innerText();
                if (text != null) {
                    text = text
                            .replace(' ', ' ')
                            .replaceAll("(?i)\baceptar\b", "")
                            .replaceAll("(?i)\bok\b", "")
                            .replaceAll("\\s+", " ")
                            .trim();
                }
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        } catch (Exception e) {
            log.warn("[mtc][check] no se pudo leer mensaje modal. error={}", e.getMessage());
        }
        return null;
    }

    private void dismissModalIfPresent(Page page, String sessionId) {
        try {
            Locator buttons = page.locator(".modal-footer button, .bootbox .btn, .swal2-confirm, button");
            int count = buttons.count();
            for (int i = 0; i < count; i++) {
                Locator button = buttons.nth(i);
                try {
                    if (!button.isVisible()) {
                        continue;
                    }
                } catch (Exception ignored) {
                    continue;
                }
                String text = button.innerText();
                if (text == null) {
                    continue;
                }
                String upper = text.trim().toUpperCase(Locale.ROOT);
                if ("ACEPTAR".equals(upper) || "OK".equals(upper)) {
                    button.click(new Locator.ClickOptions().setTimeout(1500));
                    log.info("[mtc][check] modal controlado cerrado. sessionId={}", sessionId);
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("[mtc][check] no se pudo cerrar el modal controlado. sessionId={}, error={}",
                    sessionId, e.getMessage());
        }
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

        if (value.contains("SIN LICENCIA")) return DriverLicenseStatus.SIN_LICENCIA;
        if (value.contains("VIGENTE")) return DriverLicenseStatus.VIGENTE;
        if (value.contains("VENC")) return DriverLicenseStatus.VENCIDA;
        if (value.contains("SUSP")) return DriverLicenseStatus.SUSPENDIDA;
        if (value.contains("CANCEL")) return DriverLicenseStatus.CANCELADA;

        return DriverLicenseStatus.ERROR;
    }

    private String extractLineValue(String text, String label) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(label)) {
            return null;
        }

        String[] lines = text.split("\n");
        String normalizedLabel = normalizeLabel(label);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].replace(' ', ' ').trim();
            if (!StringUtils.hasText(line)) {
                continue;
            }

            String normalizedLine = normalizeLabel(line);
            if (normalizedLine.startsWith(normalizedLabel)) {
                int colonIndex = line.indexOf(':');
                if (colonIndex >= 0 && colonIndex + 1 < line.length()) {
                    return line.substring(colonIndex + 1).trim();
                }

                if (i + 1 < lines.length) {
                    String nextLine = lines[i + 1] == null ? "" : lines[i + 1].replace(' ', ' ').trim();
                    if (StringUtils.hasText(nextLine)) {
                        return nextLine;
                    }
                }
            }
        }

        Pattern pattern = Pattern.compile("(?im)^\\s*" + flexibleLabelPattern(label) + "\\s*:?\\s*(.+?)\\s*$");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String normalizeLabel(String value) {
        if (value == null) {
            return "";
        }
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9]", "")
                .toUpperCase(Locale.ROOT);
    }

    private String flexibleLabelPattern(String label) {
        String[] tokens = label.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0) {
                sb.append("\\s*");
            }
            sb.append(Pattern.quote(tokens[i]));
        }
        return sb.toString();
    }

    private String sanitizeFieldValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String cleaned = value.replace(' ', ' ').replaceAll("\\s+", " ").trim();
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }
        if ("-".equals(cleaned) || "--".equals(cleaned) || "SIN DATO".equalsIgnoreCase(cleaned)) {
            return null;
        }
        return cleaned;
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

    private record ControlledModalError(
            DriverLicenseStatus status,
            String message,
            String errorCode,
            boolean captchaRequired
    ) {
    }

    private String buildFriendlyControlledMessage(String rawText) {
        String normalizedKey = normalizeModalKey(rawText);

        if (normalizedKey.contains("elcodigocaptchanocoincideconlaimagen")) {
            return "El código captcha no coincide con la imagen.";
        }

        if (normalizedKey.contains("ingresenumerodedocumento")) {
            return "Ingrese número de documento.";
        }

        if (normalizedKey.contains("elnumerodedocumentodeidentidaddebeserde8caracteresnumericos")) {
            return "El número de documento de identidad debe ser de 8 caracteres numéricos.";
        }

        if (normalizedKey.contains("elnumerodedocumentodebeserde8caracteresnumericos")) {
            return "El número de documento debe ser de 8 caracteres numéricos.";
        }

        if (normalizedKey.contains("seleccioneeltipodedocumento")) {
            return "Seleccione el tipo de documento.";
        }

        return prettifyRawModalText(rawText);
    }

    private String resolveControlledErrorCode(String rawText) {
        String normalizedKey = normalizeModalKey(rawText);

        if (normalizedKey.contains("elcodigocaptchanocoincideconlaimagen")) {
            return "MTC_CAPTCHA_INVALIDO";
        }

        if (normalizedKey.contains("ingresenumerodedocumento")) {
            return "MTC_DOCUMENTO_REQUERIDO";
        }

        if (normalizedKey.contains("elnumerodedocumentodeidentidaddebeserde8caracteresnumericos")
                || normalizedKey.contains("elnumerodedocumentodebeserde8caracteresnumericos")) {
            return "MTC_DOCUMENTO_LONGITUD_INVALIDA";
        }

        if (normalizedKey.contains("seleccioneeltipodedocumento")) {
            return "MTC_TIPO_DOCUMENTO_REQUERIDO";
        }

        return "MTC_MODAL_ERROR";
    }

    private String normalizeModalKey(String text) {
        if (text == null) {
            return "";
        }

        String cleaned = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                            .replaceAll("(?i)\baceptar\b", "")
                            .replaceAll("(?i)\bok\b", "")
                .replaceAll("[^a-zA-Z0-9]", "")
                .toLowerCase(Locale.ROOT)
                .trim();

        return cleaned;
    }

    private String prettifyRawModalText(String text) {
        if (text == null || text.isBlank()) {
            return "Se presentó un error controlado en la consulta.";
        }

        String cleaned = text
                .replace('\u00A0', ' ')
                            .replaceAll("(?i)\baceptar\b", "")
                            .replaceAll("(?i)\bok\b", "")
                .replaceAll("\\s+", " ")
                .trim();

        if (cleaned.isBlank()) {
            return "Se presentó un error controlado en la consulta.";
        }

        return cleaned;
    }
}
