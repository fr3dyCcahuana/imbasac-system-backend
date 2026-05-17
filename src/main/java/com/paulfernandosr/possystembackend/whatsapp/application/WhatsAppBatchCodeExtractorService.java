package com.paulfernandosr.possystembackend.whatsapp.application;

import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppBatchCodeLine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class WhatsAppBatchCodeExtractorService {
    private static final Pattern CODE_PATTERN = Pattern.compile("(?i)\\b(?:[A-Z]{2,}[A-Z0-9-]{2,}|(?=[A-Z0-9-]*[A-Z])(?=[A-Z0-9-]*\\d)[A-Z0-9-]{4,}|\\d{5,20})\\b");
    private static final Pattern QTY_PATTERN = Pattern.compile("(?i)(?:\\bx\\s*|\\bcant(?:idad)?\\s*[:=]?\\s*|\\bqty\\s*[:=]?\\s*|\\bund\\.?\\s*)(\\d+(?:[.,]\\d{1,3})?)|(?:\\b(\\d+(?:[.,]\\d{1,3})?)\\s*(?:und|unds|unidad|unidades|pcs|pza|pzas)\\b)");

    private static final Set<String> STOP_WORDS = Set.of(
            "HOLA", "BUENAS", "GRACIAS", "PROFORMA", "COTIZACION", "COTIZACIÓN", "ASESOR",
            "PRODUCTO", "PRODUCTOS", "CODIGO", "CÓDIGO", "COD", "SKU", "STOCK", "PRECIO",
            "TOTAL", "SOLES", "PEN", "S", "LISTA", "PEDIDO", "NOMBRE", "DNI", "RUC",
            "FOTO", "IMAGEN", "PAPEL", "CANTIDAD", "UND", "UNIDAD", "UNIDADES"
    );

    private final WhatsAppIntegrationProperties properties;

    public boolean looksLikeBatchCodeList(String text) {
        List<WhatsAppBatchCodeLine> codes = extractCodes(text);
        if (codes.size() >= 2) return true;
        if (text == null) return false;
        long nonBlankLines = text.lines().filter(line -> !line.trim().isBlank()).count();
        return nonBlankLines >= 2 && codes.size() >= 1;
    }

    public List<WhatsAppBatchCodeLine> extractCodes(String text) {
        if (text == null || text.isBlank()) return List.of();

        int maxCodes = Math.max(1, properties.getSales().getMaxBatchCodesPerMessage());
        BigDecimal defaultQty = BigDecimal.valueOf(Math.max(0.001D, properties.getSales().getDefaultBatchQuantity()));

        Map<String, WhatsAppBatchCodeLine> byCode = new LinkedHashMap<>();
        String normalizedText = text.replace('\r', '\n');
        List<String> lines = new ArrayList<>();
        normalizedText.lines().forEach(line -> {
            String trimmed = line.trim();
            if (!trimmed.isBlank()) lines.add(trimmed);
        });
        if (lines.isEmpty()) lines.add(normalizedText.trim());

        for (String line : lines) {
            BigDecimal qty = extractQuantity(line).orElse(defaultQty);
            Matcher matcher = CODE_PATTERN.matcher(line);
            while (matcher.find()) {
                String raw = matcher.group();
                String code = cleanCode(raw);
                if (!isValidCode(code)) continue;
                WhatsAppBatchCodeLine existing = byCode.get(code);
                if (existing == null) {
                    byCode.put(code, WhatsAppBatchCodeLine.builder()
                            .code(code)
                            .quantity(qty)
                            .rawLine(line)
                            .build());
                } else {
                    existing.setQuantity(existing.getQuantity().add(qty));
                }
                if (byCode.size() >= maxCodes) return new ArrayList<>(byCode.values());
            }
        }
        return new ArrayList<>(byCode.values());
    }

    private Optional<BigDecimal> extractQuantity(String line) {
        if (line == null || line.isBlank()) return Optional.empty();
        Matcher matcher = QTY_PATTERN.matcher(line);
        if (matcher.find()) {
            String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            try {
                BigDecimal qty = new BigDecimal(value.replace(',', '.'));
                if (qty.compareTo(BigDecimal.ZERO) > 0) return Optional.of(qty);
            } catch (Exception ignored) { }
        }
        return Optional.empty();
    }

    private String cleanCode(String value) {
        if (value == null) return "";
        String n = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .trim();
        return n.replaceAll("^[^A-Z0-9]+|[^A-Z0-9]+$", "");
    }

    private boolean isValidCode(String code) {
        if (code == null || code.length() < 4) return false;
        if (STOP_WORDS.contains(code)) return false;
        // Evita tomar palabras comunes/descripciones como códigos (ej.: FARO, DISCOVER, CASCO).
        // Los códigos válidos normalmente son alfanuméricos o numéricos largos.
        if (code.matches("^[A-Z]+$")) return false;
        return code.matches("[A-Z0-9-]+") || code.matches("\\d{5,20}");
    }
}
