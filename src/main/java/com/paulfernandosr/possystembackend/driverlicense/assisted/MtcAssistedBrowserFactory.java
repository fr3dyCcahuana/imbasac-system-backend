package com.paulfernandosr.possystembackend.driverlicense.assisted;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;
import com.paulfernandosr.possystembackend.driverlicense.infrastructure.config.MtcLicenseProperties;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.regex.Pattern;
import static com.paulfernandosr.possystembackend.driverlicense.assisted.MtcAssistedDtos.*;

@Component
public class MtcAssistedBrowserFactory {
    private final MtcAssistedProperties assisted;
    private final MtcLicenseProperties network;
    public MtcAssistedBrowserFactory(MtcAssistedProperties assisted, MtcLicenseProperties network) {
        this.assisted = assisted;
        this.network = network;
    }

    public MtcAssistedBrowser create() { return new BrowserSession(assisted, network); }

    private static final class BrowserSession implements MtcAssistedBrowser {
        private static final Logger log = LoggerFactory.getLogger(BrowserSession.class);
        private final MtcAssistedProperties properties;
        private Playwright playwright;
        private Browser browser;
        private Page page;
        private String question;
        private List<String> optionSources;
        private String step;
        private static final Pattern ROBOT = Pattern.compile("No soy un robot", Pattern.CASE_INSENSITIVE);
        private static final String FIELDS_SCRIPT = """
            root => {
              const result = {};
              for (const f of root.querySelectorAll('mat-form-field, .mat-form-field')) {
                const label = f.querySelector('mat-label, .mat-form-field-label, label');
                const input = f.querySelector('input, textarea, select');
                if (label && input) result[label.textContent.trim()] = input.value.trim();
              }
              for (const input of root.querySelectorAll('input[aria-label]')) {
                if (input.value) result[input.getAttribute('aria-label')] = input.value.trim();
              }
              return result;
            }
            """;

        BrowserSession(MtcAssistedProperties properties, MtcLicenseProperties network) {
            this.properties = properties;
            try {
                playwright = Playwright.create();
                var launch = new BrowserType.LaunchOptions().setHeadless(true).setTimeout(Math.max(10000, network.getTimeoutMs()))
                        .setArgs(List.of("--disable-dev-shm-usage"));
                if (network.isProxyEnabled() && network.getProxyServer() != null && !network.getProxyServer().isBlank()) {
                    var proxy = new Proxy(network.getProxyServer());
                    if (network.getProxyUsername() != null) proxy.setUsername(network.getProxyUsername());
                    if (network.getProxyPassword() != null) proxy.setPassword(network.getProxyPassword());
                    if (network.getProxyBypass() != null) proxy.setBypass(network.getProxyBypass());
                    launch.setProxy(proxy);
                }
                browser = playwright.chromium().launch(launch);
                var context = browser.newContext(new Browser.NewContextOptions().setLocale("es-PE")
                        .setTimezoneId("America/Lima").setViewportSize(1366, 1000));
                page = context.newPage();
                page.setDefaultTimeout(network.getTimeoutMs());
                page.setDefaultNavigationTimeout(network.getTimeoutMs());
            } catch (RuntimeException e) { close(); throw e; }
        }

        @Override public ChallengeView open(String documentType, String documentNumber) {
            try { return openChallenge(documentType, documentNumber); }
            catch (PlaywrightException e) { throw portalFailure(e); }
        }

        private ChallengeView openChallenge(String documentType, String documentNumber) {
            step = "OPEN_PORTAL";
            page.navigate(properties.getSearchUrl(), new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            step = "ENTER_DOCUMENT";
            if (!"DNI".equals(documentType)) {
                page.getByRole(AriaRole.SWITCH, new Page.GetByRoleOptions().setName("Búsqueda avanzada")).check();
                page.getByRole(AriaRole.COMBOBOX, new Page.GetByRoleOptions().setName("Tipo de documento de identidad")).click();
                page.getByRole(AriaRole.OPTION, new Page.GetByRoleOptions().setName(documentLabel(documentType)).setExact(true)).click();
            }
            page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Documento de identidad").setExact(true)).fill(documentNumber);
            step = "OPEN_CHALLENGE";
            page.getByText("No soy un robot", new Page.GetByTextOptions().setExact(true)).click();
            Locator dialog = page.getByRole(AriaRole.DIALOG);
            dialog.waitFor();
            page.waitForFunction("() => { const d = document.querySelector('[role=dialog]'); const imgs = d?.querySelectorAll('img'); return imgs?.length > 0 && [...imgs].every(i => i.complete && i.naturalWidth > 0); }");
            question = dialog.innerText().replaceAll("(?m)^\\s*Cerrar\\s*$", "").trim();
            Locator images = dialog.locator("img");
            if (images.count() < 2 || images.count() > 16 || question.isBlank()) {
                throw new MtcAssistedException(502, "MTC_CHALLENGE_CHANGED", "El formato del desafío MTC cambió. Vuelve a intentar la consulta.");
            }
            List<Option> options = new ArrayList<>();
            optionSources = new ArrayList<>();
            step = "CAPTURE_CHALLENGE";
            for (int i = 0; i < images.count(); i++) {
                optionSources.add(images.nth(i).getAttribute("src"));
                options.add(new Option(i, "data:image/png;base64," + Base64.getEncoder().encodeToString(images.nth(i).screenshot())));
            }
            return new ChallengeView(question, List.copyOf(options));
        }

        @Override public Observation submit(int imageIndex, boolean privacyAccepted) {
            try { return submitChoice(imageIndex, privacyAccepted); }
            catch (PlaywrightException e) { throw portalFailure(e); }
        }

        private Observation submitChoice(int imageIndex, boolean privacyAccepted) {
            step = "CHECK_CHALLENGE";
            if (!privacyAccepted) throw new MtcAssistedException(400, "MTC_PRIVACY_REQUIRED", "Debes aceptar expresamente la política del MTC.");
            Locator dialog = page.getByRole(AriaRole.DIALOG);
            Locator images = dialog.locator("img");
            if (!dialog.isVisible() || imageIndex < 0 || imageIndex >= optionSources.size() || images.count() != optionSources.size()) {
                throw new MtcAssistedException(409, "MTC_CHALLENGE_CHANGED", "El desafío ya no está disponible. Genera una nueva consulta.");
            }
            for (int i = 0; i < images.count(); i++) {
                if (!Objects.equals(optionSources.get(i), images.nth(i).getAttribute("src"))) {
                    throw new MtcAssistedException(409, "MTC_CHALLENGE_CHANGED", "Las imágenes del desafío cambiaron. Genera una nueva consulta.");
                }
            }
            // This index comes only from the person's explicit selection; no image classification or token injection.
            step = "SEND_CHOICE";
            images.nth(imageIndex).click();
            step = "CONFIRM_CHOICE";
            Locator robot = page.getByRole(AriaRole.CHECKBOX, new Page.GetByRoleOptions().setName(ROBOT));
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline && !robot.isChecked()) page.waitForTimeout(150);
            if (!robot.isChecked()) throw new MtcAssistedException(422, "MTC_CAPTCHA_INVALIDO", "El MTC no aceptó la imagen elegida. Genera un nuevo desafío.");
            step = "ACCEPT_PRIVACY";
            Locator privacy = page.getByRole(AriaRole.CHECKBOX, new Page.GetByRoleOptions().setName("Si, acepto").setExact(true));
            // Angular Material clips the native input to 1px. Click its visible label instead.
            if (!privacy.isChecked()) page.getByText("Si, acepto", new Page.GetByTextOptions().setExact(true)).click();
            long privacyDeadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < privacyDeadline && !privacy.isChecked()) page.waitForTimeout(100);
            if (!privacy.isChecked()) throw new MtcAssistedException(502, "MTC_PRIVACY_NOT_CONFIRMED",
                    "El MTC no confirmó la aceptación de su política. Inicia otra consulta.");
            step = "SEARCH";
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("Buscar\\s*$"))).click();
            step = "WAIT_RESULT";
            page.getByText("Datos del conductor", new Page.GetByTextOptions().setExact(true)).waitFor();
            step = "READ_LICENSES";
            selectTab("Licencias");
            Map<String, String> identity = fields(page.locator("body"));
            List<Map<String, String>> licenses = licenseCards();
            String pointsText = null;
            List<String> warnings = new ArrayList<>();
            try {
                step = "READ_POINTS";
                selectTab("Puntos");
                // Wait for a real numeric total; a tab label alone does not mean its data finished loading.
                page.waitForFunction("() => /Puntos\\s+acumulados\\s*(?:(?:info(?:_outline)?|help_outline|bubble_chart)\\s*)*\\d{1,4}\\s+puntos?/iu.test(document.body.innerText)", null,
                        new Page.WaitForFunctionOptions().setTimeout(10000));
                pointsText = page.locator("body").innerText();
            } catch (PlaywrightException e) {
                warnings.add("No se pudo obtener el total de puntos. El resultado queda incompleto.");
            }
            return new Observation(identity, licenses, pointsText, List.copyOf(warnings));
        }

        private MtcAssistedException portalFailure(PlaywrightException error) {
            String detail = Objects.toString(error.getMessage(), "");
            String reason = error instanceof TimeoutError ? "timeout"
                    : detail.contains("strict mode violation") ? "ambiguous_control"
                    : detail.contains("did not change its state") ? "checkbox_state"
                    : detail.contains("net::") ? "network" : "browser_error";
            // Playwright exception messages can contain document numbers and page content.
            // Keep diagnostics limited to fixed step names and failure categories.
            log.warn("MTC assisted browser failed: step={}, reason={}", step, reason);
            if (Set.of("OPEN_PORTAL", "ENTER_DOCUMENT", "OPEN_CHALLENGE", "CAPTURE_CHALLENGE").contains(step)
                    && Set.of("timeout", "network").contains(reason)) {
                return new MtcAssistedException(503, "MTC_CHALLENGE_LOAD_FAILED",
                        "El MTC no terminó de cargar el desafío visual. Intenta otra vez en unos momentos.");
            }
            String message = switch (Objects.toString(step, "")) {
                case "OPEN_PORTAL" -> "No se pudo abrir el portal del MTC. Revisa la conexión y vuelve a intentar.";
                case "ENTER_DOCUMENT" -> "No se pudo introducir el documento en el portal del MTC. Inicia otra consulta.";
                case "OPEN_CHALLENGE", "CAPTURE_CHALLENGE" -> "No se pudo obtener el desafío visual del MTC. Inicia otra consulta.";
                case "CHECK_CHALLENGE", "SEND_CHOICE", "CONFIRM_CHOICE" -> "No se pudo confirmar la imagen elegida en el MTC. Genera un nuevo desafío.";
                case "ACCEPT_PRIVACY" -> "No se pudo registrar tu aceptación de la política en el portal del MTC. Inicia otra consulta.";
                case "SEARCH", "WAIT_RESULT" -> "El MTC no mostró el resultado de la consulta. Inicia otra consulta.";
                default -> "No se pudieron leer las licencias del resultado del MTC. Inicia otra consulta.";
            };
            return new MtcAssistedException(502, "MTC_" + step + "_FAILED", message);
        }

        private void selectTab(String name) {
            // Names are our own fixed "Licencias" / "Puntos" constants. Java \Q quoting is not a JS RegExp feature.
            page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName(Pattern.compile(name + "\\s*$"))).click();
        }
        @SuppressWarnings("unchecked") private List<Map<String, String>> licenseCards() {
            // The current portal uses plain divs for license cards. Find each card by its labeled field,
            // keeping separate records instead of overwriting values when multiple licenses are present.
            String script = "root => { const read = (" + FIELDS_SCRIPT + ");" + """
              const norm = s => (s || '').normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/[^a-z0-9]/gi,'').toUpperCase();
              const isNumber = f => ['NUMERODELICENCIA','NRODELICENCIA'].includes(norm(f.querySelector('mat-label,.mat-form-field-label,label')?.textContent));
              const numbers = [...root.querySelectorAll('mat-form-field,.mat-form-field')].filter(isNumber);
              const cards = [];
              for (const field of numbers) {
                let node = field.parentElement;
                while (node) {
                  const count = [...node.querySelectorAll('mat-form-field,.mat-form-field')].filter(isNumber).length;
                  if (count > 1) break;
                  if (count === 1 && /Licencia\\s*:/iu.test(node.innerText)) {
                    cards.push({...read(node), __text: node.innerText});
                    break;
                  }
                  node = node.parentElement;
                }
              }
              return cards;
            }
            """;
            return (List<Map<String, String>>) page.locator("body").evaluate(script);
        }
        @SuppressWarnings("unchecked") private Map<String, String> fields(Locator root) {
            return new LinkedHashMap<>((Map<String, String>) root.evaluate(FIELDS_SCRIPT));
        }
        private static String documentLabel(String type) {
            return switch (type) {
                case "CE" -> "CARNÉ DE EXTRANJERÍA";
                case "CS" -> "CARNÉ DE SOLICITANTE DE REFUGIO";
                case "PAS" -> "PASAPORTE";
                case "TI" -> "TARJETA DE IDENTIDAD";
                case "CPP" -> "PTP/CPP";
                default -> throw new MtcAssistedException(400, "MTC_DOCUMENT_TYPE", "Tipo de documento no admitido por la consulta del MTC.");
            };
        }
        @Override public void close() {
            try { if (browser != null) browser.close(); } catch (RuntimeException ignored) {}
            try { if (playwright != null) playwright.close(); } catch (RuntimeException ignored) {}
        }
    }
}
