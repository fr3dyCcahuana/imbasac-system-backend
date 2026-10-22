package com.paulfernandosr.possystembackend.driverlicense.assisted;

import org.springframework.stereotype.Component;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import static com.paulfernandosr.possystembackend.driverlicense.assisted.MtcAssistedDtos.*;

@Component
public class MtcAssistedParser {
    public String field(Map<String, String> fields, String... labels) {
        for (String label : labels) {
            for (var entry : fields.entrySet()) {
                if (normalize(entry.getKey()).equals(normalize(label)) && meaningful(entry.getValue())) {
                    return entry.getValue().trim();
                }
            }
        }
        return null;
    }

    public License license(Map<String, String> fields) {
        String text = fields.getOrDefault("__text", "");
        var status = Pattern.compile("(?iu)Licencia\\s*:[^\\r\\n]*?[-–]\\s*(Vigente|Vencid[ao]|Suspendid[ao]|Cancelad[ao]|Retenid[ao])\\b")
                .matcher(text);
        String rawStatus = field(fields, "Estado de la licencia", "Estado");
        if (rawStatus == null && status.find()) rawStatus = status.group(1);
        return new License(field(fields, "Número de licencia", "Nro. de licencia"),
                field(fields, "Clase y categoría", "Clase y categoria"), status(rawStatus),
                field(fields, "Vigente hasta", "Fecha de vencimiento"), field(fields, "Restricciones"),
                field(fields, "Tipo de Licencia"), field(fields, "Fecha de expedición"));
    }

    public String status(String value) {
        return switch (normalize(value)) {
            case "VIGENTE" -> "VIGENTE";
            case "VENCIDA", "VENCIDO" -> "VENCIDA";
            case "SUSPENDIDA", "SUSPENDIDO" -> "SUSPENDIDA";
            case "CANCELADA", "CANCELADO" -> "CANCELADA";
            case "RETENIDA", "RETENIDO" -> "RETENIDA";
            default -> "DESCONOCIDO";
        };
    }

    public Integer points(String text) {
        var match = Pattern.compile("(?iu)Puntos\\s+acumulados\\s*(?:(?:info(?:_outline)?|help_outline|bubble_chart)\\s*)*(\\d{1,4})\\s+puntos?\\b")
                .matcher(text == null ? "" : text);
        return match.find() ? Integer.valueOf(match.group(1)) : null;
    }

    public boolean completeLicense(License license) {
        if (!meaningful(license.number()) || !meaningful(license.category()) || "DESCONOCIDO".equals(license.status())) return false;
        try {
            java.time.LocalDate.parse(license.validUntil(), java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu")
                    .withResolverStyle(java.time.format.ResolverStyle.STRICT));
            return true;
        } catch (Exception ignored) { return false; }
    }

    public boolean meaningful(String value) {
        return value != null && !value.isBlank() && !Set.of("-", "--", "SIN DATO").contains(value.trim().toUpperCase(Locale.ROOT));
    }
    public String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    }
}
