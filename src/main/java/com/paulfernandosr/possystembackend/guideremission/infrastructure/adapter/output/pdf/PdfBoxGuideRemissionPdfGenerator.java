package com.paulfernandosr.possystembackend.guideremission.infrastructure.adapter.output.pdf;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionCompany;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionDocument;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionDocumentItem;
import com.paulfernandosr.possystembackend.guideremission.domain.port.output.GuideRemissionPdfGenerator;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import java.awt.Color;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Slf4j
public class PdfBoxGuideRemissionPdfGenerator implements GuideRemissionPdfGenerator {
    private static final PDFont FONT_REGULAR = PDType1Font.HELVETICA;
    private static final PDFont FONT_BOLD = PDType1Font.HELVETICA_BOLD;
    private static final PDFont FONT_ITALIC = PDType1Font.HELVETICA_OBLIQUE;

    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float MARGIN_LEFT = 36f;
    private static final float MARGIN_RIGHT = 36f;
    private static final float MARGIN_TOP = 34f;
    private static final float MARGIN_BOTTOM = 70f;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT;
    private static final float DEFAULT_LEADING = 11f;

    private static final Map<String, String> TRANSFER_REASON_LABELS = Map.of(
            "01", "VENTA",
            "02", "COMPRA",
            "04", "TRASLADO ENTRE ESTABLECIMIENTOS",
            "08", "IMPORTACIÓN",
            "09", "EXPORTACIÓN",
            "13", "OTROS"
    );

    private static final Map<String, String> TRANSFER_MODE_LABELS = Map.of(
            "01", "TRANSPORTE PÚBLICO",
            "02", "TRANSPORTE PRIVADO"
    );

    @Override
    public byte[] generate(GuideRemissionCompany company, GuideRemissionDocument document) {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            RenderState state = startPage(pdf, company, document);
            drawTableHeader(state);

            for (GuideRemissionDocumentItem item : document.getItems()) {
                float rowHeight = estimateRowHeight(item);
                if (state.y - rowHeight < 150f) {
                    state = startPage(pdf, company, document, state);
                    drawTableHeader(state);
                }
                drawItemRow(state, item, rowHeight);
                state.y -= rowHeight;
            }

            if (state.y < 170f) {
                state = startPage(pdf, company, document, state);
            }

            drawSummary(state, document);
            state.stream.close();
            appendFooters(pdf, company, document);
            pdf.save(outputStream);
            return outputStream.toByteArray();
        } catch (IOException ex) {
            log.error("[guide-remission][pdf] No se pudo generar el PDF. serie={}, numero={}",
                    document.getSerie(), document.getNumero(), ex);
            throw new IllegalStateException("No se pudo generar el PDF de la guía de remisión.", ex);
        }
    }

    private RenderState startPage(PDDocument pdf, GuideRemissionCompany company, GuideRemissionDocument document) throws IOException {
        return startPage(pdf, company, document, null);
    }

    private RenderState startPage(PDDocument pdf, GuideRemissionCompany company, GuideRemissionDocument document, RenderState currentState) throws IOException {
        if (currentState != null && currentState.stream != null) {
            currentState.stream.close();
        }

        PDPage page = new PDPage(PDRectangle.A4);
        pdf.addPage(page);
        PDPageContentStream stream = new PDPageContentStream(pdf, page);
        RenderState state = new RenderState(page, stream, PAGE_HEIGHT - MARGIN_TOP);
        state.y = drawHeader(state, company, document);
        state.y = drawPartiesSection(state, document);
        state.y = drawRouteAndTransportSection(state, document);
        return state;
    }

    private float drawHeader(RenderState state, GuideRemissionCompany company, GuideRemissionDocument document) throws IOException {
        float companyBlockWidth = CONTENT_WIDTH - 168f;
        float rightBoxX = MARGIN_LEFT + companyBlockWidth + 12f;
        float topY = PAGE_HEIGHT - MARGIN_TOP;

        writeText(state.stream, FONT_BOLD, 15f, MARGIN_LEFT, topY, safe(company.getRazonSocial()));
        writeText(state.stream, FONT_REGULAR, 9f, MARGIN_LEFT, topY - 15f,
                buildCompanyAddress(company));
        writeText(state.stream, FONT_REGULAR, 9f, MARGIN_LEFT, topY - 28f,
                "RUC: " + safe(company.getRuc()));

        if (hasText(company.getNombreComercial())) {
            writeText(state.stream, FONT_REGULAR, 9f, MARGIN_LEFT, topY - 41f,
                    "Nombre comercial: " + company.getNombreComercial().trim());
        }

        float boxHeight = 66f;
        drawRect(state.stream, rightBoxX, topY - boxHeight + 5f, 156f, boxHeight);
        writeCenteredText(state.stream, FONT_BOLD, 10f, rightBoxX, topY - 10f, 156f,
                "RUC: " + safe(company.getRuc()));
        writeCenteredText(state.stream, FONT_BOLD, 10f, rightBoxX, topY - 26f, 156f,
                "GUÍA DE REMISIÓN");
        writeCenteredText(state.stream, FONT_BOLD, 10f, rightBoxX, topY - 39f, 156f,
                "ELECTRÓNICA - REMITENTE");
        writeCenteredText(state.stream, FONT_BOLD, 11f, rightBoxX, topY - 56f, 156f,
                safe(document.getSerie()) + "-" + safe(document.getNumero()));

        drawLine(state.stream, MARGIN_LEFT, topY - 58f, PAGE_WIDTH - MARGIN_RIGHT, topY - 58f);
        return topY - 72f;
    }

    private float drawPartiesSection(RenderState state, GuideRemissionDocument document) throws IOException {
        float y = state.y;
        float leftX = MARGIN_LEFT;
        float midX = MARGIN_LEFT + 265f;
        float rightX = MARGIN_LEFT + 410f;

        writeLabelValue(state.stream, leftX, y, "Cliente", safe(document.getRecipientName()));
        writeLabelValue(state.stream, leftX, y - 13f, "Doc. Ident.", safe(document.getRecipientDocumentNumber()));
        writeLabelValue(state.stream, leftX, y - 26f, "Dirección", safe(document.getArrivalAddress()));

        writeLabelValue(state.stream, midX, y, "Emisión", formatDate(document.getIssueDate()));
        writeLabelValue(state.stream, midX, y - 13f, "Hora", formatTime(document.getIssueTime()));
        writeLabelValue(state.stream, midX, y - 26f, "Fecha Traslado", formatDate(document.getTransferDate()));

        return y - 44f;
    }

    private float drawRouteAndTransportSection(RenderState state, GuideRemissionDocument document) throws IOException {
        float y = state.y;
        drawLine(state.stream, MARGIN_LEFT, y + 6f, PAGE_WIDTH - MARGIN_RIGHT, y + 6f);

        writeLabelValue(state.stream, MARGIN_LEFT, y - 8f, "Pto. Partida", buildAddressWithUbigeo(document.getDepartureAddress(), document.getDepartureUbigeo()));
        writeLabelValue(state.stream, MARGIN_LEFT, y - 21f, "Pto. Llegada", buildAddressWithUbigeo(document.getArrivalAddress(), document.getArrivalUbigeo()));
        writeLabelValue(state.stream, MARGIN_LEFT, y - 34f, "Mot. Traslado", resolveTransferReason(document.getTransferReasonCode()));
        writeLabelValue(state.stream, 335f, y - 34f, "Modalidad", resolveTransferMode(document.getTransferModeCode()));

        if ("01".equals(document.getTransferModeCode())) {
            writeLabelValue(state.stream, MARGIN_LEFT, y - 47f, "Transportista",
                    firstNonBlank(document.getTransporterName(), document.getLegacyTransportEntityId()));
            writeLabelValue(state.stream, 335f, y - 47f, "Doc. Transporte",
                    firstNonBlank(document.getTransporterDocumentNumber(), document.getLegacyTransportEntityId()));
            writeLabelValue(state.stream, MARGIN_LEFT, y - 60f, "Registro/MTC", safe(document.getLegacyTransportMtcNumber()));
        } else {
            writeLabelValue(state.stream, MARGIN_LEFT, y - 47f, "Conductor", safe(document.getDriverFullName()));
            writeLabelValue(state.stream, 335f, y - 47f, "DNI", safe(document.getDriverDni()));
            writeLabelValue(state.stream, MARGIN_LEFT, y - 60f, "Licencia", safe(document.getDriverLicense()));
            writeLabelValue(state.stream, 335f, y - 60f, "Marca y Placa", safe(document.getVehiclePlate()));
        }

        drawLine(state.stream, MARGIN_LEFT, y - 70f, PAGE_WIDTH - MARGIN_RIGHT, y - 70f);
        return y - 84f;
    }

    private void drawTableHeader(RenderState state) throws IOException {
        float tableX = MARGIN_LEFT;
        float y = state.y;
        float[] widths = {55f, 50f, 90f, CONTENT_WIDTH - 195f};
        String[] headers = {"CANT.", "U.M.", "CÓDIGO", "DESCRIPCIÓN"};

        drawFilledRowBackground(state.stream, tableX, y - 14f, CONTENT_WIDTH, 16f);
        float x = tableX;
        for (int i = 0; i < headers.length; i++) {
            drawRect(state.stream, x, y - 14f, widths[i], 16f);
            writeCenteredText(state.stream, FONT_BOLD, 8.5f, x, y - 4f, widths[i], headers[i]);
            x += widths[i];
        }
        state.y = y - 18f;
    }

    private void drawItemRow(RenderState state, GuideRemissionDocumentItem item, float rowHeight) throws IOException {
        float tableX = MARGIN_LEFT;
        float yTop = state.y;
        float[] widths = {55f, 50f, 90f, CONTENT_WIDTH - 195f};
        float x = tableX;

        List<String> descriptionLines = wrapText(safe(item.getDescription()), FONT_REGULAR, 8.7f, widths[3] - 6f);
        if (descriptionLines.isEmpty()) {
            descriptionLines = List.of("");
        }

        drawRect(state.stream, x, yTop - rowHeight, widths[0], rowHeight);
        writeCenteredText(state.stream, FONT_REGULAR, 8.5f, x, yTop - 11f, widths[0], formatQuantity(item.getQuantity()));
        x += widths[0];

        drawRect(state.stream, x, yTop - rowHeight, widths[1], rowHeight);
        writeCenteredText(state.stream, FONT_REGULAR, 8.5f, x, yTop - 11f, widths[1], safe(item.getUnitCode()));
        x += widths[1];

        drawRect(state.stream, x, yTop - rowHeight, widths[2], rowHeight);
        writeCenteredText(state.stream, FONT_REGULAR, 8.5f, x, yTop - 11f, widths[2], safe(item.getItemCode()));
        x += widths[2];

        drawRect(state.stream, x, yTop - rowHeight, widths[3], rowHeight);
        float textY = yTop - 10f;
        for (String line : descriptionLines) {
            writeText(state.stream, FONT_REGULAR, 8.5f, x + 3f, textY, line);
            textY -= 10f;
        }
    }

    private void drawSummary(RenderState state, GuideRemissionDocument document) throws IOException {
        float y = state.y - 10f;
        float leftBoxWidth = 180f;
        float rightBoxX = MARGIN_LEFT + leftBoxWidth + 18f;
        float rightBoxWidth = CONTENT_WIDTH - leftBoxWidth - 18f;

        drawRect(state.stream, MARGIN_LEFT, y - 40f, leftBoxWidth, 40f);
        writeText(state.stream, FONT_BOLD, 9.5f, MARGIN_LEFT + 8f, y - 12f, "BULTOS");
        writeText(state.stream, FONT_REGULAR, 9f, MARGIN_LEFT + 8f, y - 26f,
                "Cantidad: " + safe(document.getNumberOfPackages(), "-"));
        writeText(state.stream, FONT_REGULAR, 9f, MARGIN_LEFT + 8f, y - 38f,
                "Peso total: " + formatWeight(document.getTotalWeight()));

        drawRect(state.stream, rightBoxX, y - 40f, rightBoxWidth, 40f);
        writeText(state.stream, FONT_BOLD, 9.5f, rightBoxX + 8f, y - 12f, "REFERENCIA");
        writeText(state.stream, FONT_REGULAR, 9f, rightBoxX + 8f, y - 26f,
                "Documento: " + buildRelatedDocument(document));
        writeText(state.stream, FONT_REGULAR, 9f, rightBoxX + 8f, y - 38f,
                "Descripción SUNAT: " + safe(document.getDocumentDescription(), "-"));

        y -= 56f;

        if (hasText(document.getNotes())) {
            drawRect(state.stream, MARGIN_LEFT, y - 34f, CONTENT_WIDTH, 34f);
            writeText(state.stream, FONT_BOLD, 9.5f, MARGIN_LEFT + 8f, y - 12f, "OBSERVACIONES");
            List<String> notes = wrapText(document.getNotes(), FONT_REGULAR, 8.7f, CONTENT_WIDTH - 20f);
            float noteY = y - 25f;
            for (String line : notes.subList(0, Math.min(notes.size(), 2))) {
                writeText(state.stream, FONT_REGULAR, 8.7f, MARGIN_LEFT + 8f, noteY, line);
                noteY -= 10f;
            }
        }
    }

    private void appendFooters(PDDocument pdf, GuideRemissionCompany company, GuideRemissionDocument document) throws IOException {
        int totalPages = pdf.getNumberOfPages();
        for (int i = 0; i < totalPages; i++) {
            PDPage page = pdf.getPage(i);
            try (PDPageContentStream stream = new PDPageContentStream(pdf, page,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                float footerY = 42f;
                drawLine(stream, MARGIN_LEFT, footerY + 18f, PAGE_WIDTH - MARGIN_RIGHT, footerY + 18f);
                writeText(stream, FONT_REGULAR, 8.2f, MARGIN_LEFT, footerY + 6f,
                        "RUC emisor: " + safe(company.getRuc()) + "   |   Documento: " + safe(document.getSerie()) + "-" + safe(document.getNumero()));
                writeText(stream, FONT_REGULAR, 8.2f, MARGIN_LEFT, footerY - 6f,
                        "Mot. traslado: " + resolveTransferReason(document.getTransferReasonCode()) +
                                "   |   Modalidad: " + resolveTransferMode(document.getTransferModeCode()));
                writeCenteredText(stream, FONT_BOLD, 8.7f, 0f, 26f, PAGE_WIDTH,
                        "Pag " + (i + 1) + " de " + totalPages);

                if (i == totalPages - 1) {
                    float boxWidth = 120f;
                    float boxHeight = 34f;
                    float boxX = PAGE_WIDTH - MARGIN_RIGHT - boxWidth;
                    drawRect(stream, boxX, 18f, boxWidth, boxHeight);
                    writeCenteredText(stream, FONT_REGULAR, 8f, boxX, 31f, boxWidth, "Conformidad del cliente");
                }
            }
        }
    }

    private float estimateRowHeight(GuideRemissionDocumentItem item) throws IOException {
        List<String> descriptionLines = wrapText(safe(item.getDescription()), FONT_REGULAR, 8.7f, CONTENT_WIDTH - 201f);
        int lines = Math.max(descriptionLines.size(), 1);
        return Math.max(18f, 6f + lines * 10f);
    }

    private List<String> wrapText(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        if (!hasText(text)) {
            return lines;
        }

        String[] paragraphs = text.replace('\r', ' ').split("\\n");
        for (String paragraph : paragraphs) {
            String[] words = paragraph.trim().split("\\s+");
            StringBuilder currentLine = new StringBuilder();
            for (String word : words) {
                String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
                if (textWidth(font, fontSize, candidate) <= maxWidth) {
                    currentLine = new StringBuilder(candidate);
                    continue;
                }
                if (!currentLine.isEmpty()) {
                    lines.add(currentLine.toString());
                }
                if (textWidth(font, fontSize, word) <= maxWidth) {
                    currentLine = new StringBuilder(word);
                    continue;
                }
                lines.addAll(splitLongWord(word, font, fontSize, maxWidth));
                currentLine = new StringBuilder();
            }
            if (!currentLine.isEmpty()) {
                lines.add(currentLine.toString());
            }
        }
        return lines;
    }

    private List<String> splitLongWord(String word, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (char c : word.toCharArray()) {
            String candidate = current + String.valueOf(c);
            if (textWidth(font, fontSize, candidate) <= maxWidth) {
                current.append(c);
                continue;
            }
            if (!current.isEmpty()) {
                result.add(current.toString());
            }
            current = new StringBuilder(String.valueOf(c));
        }
        if (!current.isEmpty()) {
            result.add(current.toString());
        }
        return result;
    }

    private void writeLabelValue(PDPageContentStream stream, float x, float y, String label, String value) throws IOException {
        writeText(stream, FONT_BOLD, 8.7f, x, y, label + ":");
        writeText(stream, FONT_REGULAR, 8.7f, x + Math.min(textWidth(FONT_BOLD, 8.7f, label + ":") + 4f, 92f), y, safe(value, "-"));
    }

    private void writeText(PDPageContentStream stream, PDFont font, float fontSize, float x, float y, String text) throws IOException {
        stream.beginText();
        stream.setFont(font, fontSize);
        stream.newLineAtOffset(x, y);
        stream.showText(sanitize(text));
        stream.endText();
    }

    private void writeCenteredText(PDPageContentStream stream, PDFont font, float fontSize, float boxX, float y, float boxWidth, String text) throws IOException {
        float textWidth = textWidth(font, fontSize, sanitize(text));
        float textX = boxX + Math.max((boxWidth - textWidth) / 2f, 2f);
        writeText(stream, font, fontSize, textX, y, text);
    }

    private void drawRect(PDPageContentStream stream, float x, float y, float width, float height) throws IOException {
        stream.addRect(x, y, width, height);
        stream.stroke();
    }

    private void drawLine(PDPageContentStream stream, float startX, float startY, float endX, float endY) throws IOException {
        stream.moveTo(startX, startY);
        stream.lineTo(endX, endY);
        stream.stroke();
    }

    private void drawFilledRowBackground(PDPageContentStream stream, float x, float y, float width, float height) throws IOException {
        stream.setNonStrokingColor(new Color(240, 240, 240));
        stream.addRect(x, y, width, height);
        stream.fill();
        stream.setNonStrokingColor(Color.BLACK);
    }

    private float textWidth(PDFont font, float fontSize, String text) throws IOException {
        return font.getStringWidth(sanitize(text)) / 1000f * fontSize;
    }

    private String buildCompanyAddress(GuideRemissionCompany company) {
        return (safe(company.getDomicilioFiscal()) + " " + safe(company.getDistrito()) + " " +
                safe(company.getProvincia()) + " " + safe(company.getDepartamento())).trim();
    }

    private String buildAddressWithUbigeo(String address, String ubigeo) {
        if (!hasText(ubigeo)) {
            return safe(address);
        }
        return safe(address) + " (Ubigeo: " + ubigeo.trim() + ")";
    }

    private String resolveTransferReason(String code) {
        if (!hasText(code)) {
            return "-";
        }
        return code + " - " + TRANSFER_REASON_LABELS.getOrDefault(code.trim(), "NO DEFINIDO");
    }

    private String resolveTransferMode(String code) {
        if (!hasText(code)) {
            return "-";
        }
        return code + " - " + TRANSFER_MODE_LABELS.getOrDefault(code.trim(), "NO DEFINIDO");
    }

    private String buildRelatedDocument(GuideRemissionDocument document) {
        if (!hasText(document.getRelatedDocumentTypeCode()) && !hasText(document.getRelatedDocumentSerie()) && !hasText(document.getRelatedDocumentNumero())) {
            return "-";
        }
        String typeLabel = switch (safe(document.getRelatedDocumentTypeCode()).trim()) {
            case "01" -> "FACTURA";
            case "03" -> "BOLETA";
            default -> safe(document.getRelatedDocumentTypeCode());
        };
        return (typeLabel + " " + safe(document.getRelatedDocumentSerie()) + "-" + safe(document.getRelatedDocumentNumero())).trim();
    }

    private String formatDate(java.time.LocalDate value) {
        return value == null ? "-" : value.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private String formatTime(java.time.LocalTime value) {
        return value == null ? "-" : value.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    private String formatQuantity(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private String formatWeight(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return String.format(Locale.US, "%s Kg.", value.stripTrailingZeros().toPlainString());
    }

    private String firstNonBlank(String first, String second) {
        if (hasText(first)) {
            return first.trim();
        }
        if (hasText(second)) {
            return second.trim();
        }
        return "-";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safe(String value) {
        return safe(value, "");
    }

    private String safe(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\t", " ")
                .replace("\r", " ")
                .replace("\n", " ")
                .replace("•", "-")
                .replace("–", "-")
                .replace("—", "-")
                .replace("“", "\"")
                .replace("”", "\"")
                .replace("’", "'")
                .replace("á", "a")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ó", "o")
                .replace("ú", "u")
                .replace("Á", "A")
                .replace("É", "E")
                .replace("Í", "I")
                .replace("Ó", "O")
                .replace("Ú", "U")
                .replace("ñ", "n")
                .replace("Ñ", "N");
    }

    private static final class RenderState {
        private final PDPage page;
        private final PDPageContentStream stream;
        private float y;

        private RenderState(PDPage page, PDPageContentStream stream, float y) {
            this.page = page;
            this.stream = stream;
            this.y = y;
        }
    }
}
