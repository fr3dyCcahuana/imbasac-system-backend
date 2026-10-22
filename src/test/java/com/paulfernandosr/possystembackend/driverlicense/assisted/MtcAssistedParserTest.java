package com.paulfernandosr.possystembackend.driverlicense.assisted;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MtcAssistedParserTest {
    private final MtcAssistedParser parser = new MtcAssistedParser();
    @Test void doesNotInferZeroFromUnknownOrUnrelatedNumbers() {
        assertNull(parser.points("Puntos acumulados\nCargando datos\nPágina 1 de 2"));
        assertNull(parser.points(null));
        assertNull(parser.points("Puntos acumulados\nDe acumular 95 puntos adicionales, le corresponderá una sanción"));
        assertEquals(0, parser.points("Puntos acumulados\ninfo\n0 puntos"));
        assertEquals(5, parser.points("Puntos acumulados info\nbubble_chart\n5 puntos\nDe acumular 95 puntos adicionales, le corresponderá una sanción"));
        assertEquals(100, parser.points("Puntos acumulados\n100 puntos"));
    }
    @Test void doesNotInterpretNoVigenteAsVigente() {
        assertEquals("DESCONOCIDO", parser.status("NO VIGENTE"));
        assertEquals("VENCIDA", parser.status("Vencido"));
    }
    @Test void extractsAccentedLabelsButRejectsInvalidExpiry() {
        var license = parser.license(Map.of("Número de licencia", "TEST", "Clase y categoría", "A I", "Vigente hasta", "31/02/2030", "__text", "Licencia: A I - Vigente"));
        assertEquals("TEST", license.number()); assertEquals("VIGENTE", license.status());
        assertFalse(parser.completeLicense(license));
    }
}
