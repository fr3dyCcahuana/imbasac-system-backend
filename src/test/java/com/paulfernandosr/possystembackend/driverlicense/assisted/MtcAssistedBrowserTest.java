package com.paulfernandosr.possystembackend.driverlicense.assisted;

import com.sun.net.httpserver.HttpServer;
import com.paulfernandosr.possystembackend.driverlicense.infrastructure.config.MtcLicenseProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real adapter on our own fixture; never solves a challenge on the MTC. */
@EnabledIfEnvironmentVariable(named = "MTC_BROWSER_TESTS", matches = "true")
class MtcAssistedBrowserTest {
    @Test void reportsInitialLoadTimeoutWithoutExposingTheDocument() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = "<html><body>Loading</body></html>".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            var properties = new MtcAssistedProperties(); properties.setSearchUrl("http://127.0.0.1:" + server.getAddress().getPort());
            var network = new MtcLicenseProperties(); network.setTimeoutMs(500);
            try (var browser = new MtcAssistedBrowserFactory(properties, network).create()) {
                var error = assertThrows(MtcAssistedException.class, () -> browser.open("DNI", "00000000"));
                assertEquals("MTC_CHALLENGE_LOAD_FAILED", error.code());
                assertEquals(503, error.status());
                assertFalse(error.getMessage().contains("00000000"));
            }
        } finally { server.stop(0); }
    }

    @Test void completesHumanSelectionAndReadsBothViewsUsingRealChromium() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = fixture().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            var properties = new MtcAssistedProperties(); properties.setSearchUrl("http://127.0.0.1:" + server.getAddress().getPort());
            var network = new MtcLicenseProperties(); network.setTimeoutMs(10000);
            try (var browser = new MtcAssistedBrowserFactory(properties, network).create()) {
                var challenge = browser.open("DNI", "00000000");
                assertEquals(2, challenge.options().size());
                assertTrue(challenge.options().getFirst().image().startsWith("data:image/png;base64,"));
                var result = browser.submit(1, true);
                var parser = new MtcAssistedParser();
                assertEquals("00000000", parser.field(result.identity(), "Número de documento"));
                assertEquals("TEST00000000", parser.license(result.licenses().getFirst()).number());
                assertEquals(5, parser.points(result.pointsText()));
            }
        } finally { server.stop(0); }
    }

    private String fixture() {
        String image = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII=";
        return """
            <!doctype html><html lang="es"><head><meta charset="utf-8"><style>img{width:90px;height:90px}mat-form-field{display:block;margin:12px}.card{padding:24px}.consent-input{position:absolute;width:1px;height:1px;clip:rect(0,0,0,0);overflow:hidden}</style></head><body>
            <section id="form"><label>Documento de identidad<input></label>
            <label><input type="checkbox" id="robot" onclick="document.getElementById('challenge').hidden=false">No soy un robot</label>
            <label for="privacy"><span><input type="checkbox" id="privacy" class="consent-input"></span><span>Si, acepto</span></label>
            <button onclick="if(document.getElementById('privacy').checked){document.getElementById('form').hidden=true;document.getElementById('result').hidden=false}"><span>search</span>Buscar</button></section>
            <div role="dialog" id="challenge" hidden><p>Consigna de prueba</p>
            <img src="%s" onclick="choose()"><img src="%s" onclick="choose()"><button>Cerrar</button></div>
            <section id="result" hidden><h2>Datos del conductor</h2>
            <mat-form-field><label>Número de documento</label><input value="00000000" readonly></mat-form-field>
            <mat-form-field><label>Nombres</label><input value="PERSONA" readonly></mat-form-field>
            <mat-form-field><label>Apellidos</label><input value="DE PRUEBA" readonly></mat-form-field>
            <button role="tab" onclick="document.getElementById('licenses').hidden=false;document.getElementById('points').hidden=true"><span>badge</span>Licencias</button>
            <button role="tab" onclick="document.getElementById('licenses').hidden=true;document.getElementById('points').hidden=false"><span>bubble_chart</span>Puntos</button>
            <div id="licenses" class="card"><h3>Licencia: A I - Vigente</h3>
            <mat-form-field><label>Número de licencia</label><input value="TEST00000000" readonly></mat-form-field>
            <mat-form-field><label>Clase y categoría</label><input value="A I" readonly></mat-form-field>
            <mat-form-field><label>Vigente hasta</label><input value="17/11/2033" readonly></mat-form-field></div>
            <div id="points" hidden><div>Puntos acumulados <span>info</span></div><span>bubble_chart</span><p>5 puntos</p><p>De acumular 95 puntos adicionales, le corresponderá una sanción</p></div></section>
            <script>function choose(){document.getElementById('robot').checked=true;document.getElementById('challenge').hidden=true}</script>
            </body></html>
            """.formatted(image, image);
    }
}
