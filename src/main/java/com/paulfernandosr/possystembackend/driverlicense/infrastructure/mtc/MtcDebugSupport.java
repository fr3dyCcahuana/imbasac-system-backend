package com.paulfernandosr.possystembackend.driverlicense.infrastructure.mtc;

import com.microsoft.playwright.Page;
import com.paulfernandosr.possystembackend.driverlicense.infrastructure.config.MtcLicenseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class MtcDebugSupport {

    private static final Logger log = LoggerFactory.getLogger(MtcDebugSupport.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final MtcLicenseProperties properties;

    public MtcDebugSupport(MtcLicenseProperties properties) {
        this.properties = properties;
    }

    public void dumpPageState(Page page, String sessionId, String stage) {
        if (!properties.isDebugEnabled() || page == null) {
            return;
        }

        try {
            Path dir = Path.of(properties.getDebugDir());
            Files.createDirectories(dir);

            String suffix = sessionId + "-" + stage + "-" + LocalDateTime.now().format(TS);

            Path htmlPath = dir.resolve(suffix + ".html");
            Path bodyPath = dir.resolve(suffix + ".txt");
            Path shotPath = dir.resolve(suffix + ".png");

            String html = page.content();
            String body = safeBody(page);

            Files.writeString(htmlPath, html == null ? "" : html, StandardCharsets.UTF_8);
            Files.writeString(bodyPath, body == null ? "" : body, StandardCharsets.UTF_8);
            page.screenshot(new Page.ScreenshotOptions().setPath(shotPath));

            log.info("[mtc][debug] evidencia guardada. sessionId={}, stage={}, html={}, body={}, screenshot={}",
                    sessionId, stage, htmlPath, bodyPath, shotPath);
        } catch (Exception e) {
            log.warn("[mtc][debug] no se pudo guardar evidencia. sessionId={}, stage={}, error={}",
                    sessionId, stage, e.getMessage());
        }
    }

    public void logSelectors(Page page, String sessionId, String stage) {
        if (!properties.isDebugEnabled()) {
            return;
        }

        try {
            log.info(
                    "[mtc][debug] sessionId={}, stage={}, url={}, form1={}, pnlBusqNroDoc={}, pnlCaptcha={}, imgCaptcha={}, txtCaptcha={}, txtNroDocumento={}, ibtnBusqNroDoc={}",
                    sessionId,
                    stage,
                    page.url(),
                    page.locator("#form1").count(),
                    page.locator("#pnlBusqNroDoc").count(),
                    page.locator("#pnlCaptcha").count(),
                    page.locator("#imgCaptcha").count(),
                    page.locator("#txtCaptcha").count(),
                    page.locator("#txtNroDocumento").count(),
                    page.locator("#ibtnBusqNroDoc").count()
            );
        } catch (Exception e) {
            log.warn("[mtc][debug] no se pudo inspeccionar selectores. sessionId={}, stage={}, error={}",
                    sessionId, stage, e.getMessage());
        }
    }

    public void logFormState(Page page, String sessionId, String stage) {
        if (!properties.isDebugEnabled()) {
            return;
        }

        try {
            String txtNroDocumento = inputValue(page, "#txtNroDocumento");
            String txtCaptcha = inputValue(page, "#txtCaptcha");
            String eventTarget = inputValue(page, "#__EVENTTARGET");
            String eventArgument = inputValue(page, "#__EVENTARGUMENT");
            String hdCodAdministrado = inputValue(page, "#hdCodAdministrado");
            String hdNumTipoDoc = inputValue(page, "#hdNumTipoDoc");
            String hdNumDocumento = inputValue(page, "#hdNumDocumento");

            log.info(
                    "[mtc][debug] form-state. sessionId={}, stage={}, txtNroDocumento={}, txtCaptcha={}, __EVENTTARGET={}, __EVENTARGUMENT={}, hdCodAdministrado={}, hdNumTipoDoc={}, hdNumDocumento={}",
                    sessionId,
                    stage,
                    txtNroDocumento,
                    txtCaptcha,
                    eventTarget,
                    eventArgument,
                    hdCodAdministrado,
                    hdNumTipoDoc,
                    hdNumDocumento
            );
        } catch (Exception e) {
            log.warn("[mtc][debug] no se pudo leer estado del formulario. sessionId={}, stage={}, error={}",
                    sessionId, stage, e.getMessage());
        }
    }

    public void logBodySummary(Page page, String sessionId, String stage) {
        if (!properties.isDebugEnabled()) {
            return;
        }

        try {
            String body = safeBody(page);
            String snippet = body.length() > 500 ? body.substring(0, 500) : body;

            log.info("[mtc][debug] body-summary. sessionId={}, stage={}, length={}, snippet={}",
                    sessionId, stage, body.length(), snippet.replace("\n", " | "));
        } catch (Exception e) {
            log.warn("[mtc][debug] no se pudo leer body summary. sessionId={}, stage={}, error={}",
                    sessionId, stage, e.getMessage());
        }
    }

    private String inputValue(Page page, String selector) {
        try {
            if (page.locator(selector).count() == 0) {
                return null;
            }
            return page.locator(selector).first().inputValue();
        } catch (Exception e) {
            return null;
        }
    }

    private String safeBody(Page page) {
        try {
            String body = page.locator("body").innerText();
            if (body == null) {
                body = "";
            }
            return body.replace('\u00A0', ' ').replace("\r", "");
        } catch (Exception e) {
            return "";
        }
    }
}