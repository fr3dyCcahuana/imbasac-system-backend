package com.paulfernandosr.possystembackend.guideremission.infrastructure.adapter.output.pdf;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionCompany;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionDocument;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionDocumentItem;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionDocumentItemAllocation;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionRelatedDocument;
import com.paulfernandosr.possystembackend.guideremission.domain.exception.GuideRemissionIntegrationException;
import com.paulfernandosr.possystembackend.guideremission.domain.port.output.GuideRemissionPdfGenerator;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class PdfBoxGuideRemissionPdfGenerator implements GuideRemissionPdfGenerator {
    private static final float PAGE_MARGIN = 32f;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float CONTENT_WIDTH = PAGE_WIDTH - (PAGE_MARGIN * 2);
    private static final float BOTTOM_LIMIT = 42f;

    private static final float TITLE_FONT = 15f;
    private static final float LABEL_FONT = 8.5f;
    private static final float VALUE_FONT = 9f;
    private static final float SMALL_FONT = 8f;
    private static final float LINE_HEIGHT = 11f;
    private static final float SECTION_HEIGHT = 14f;

    private static final float HEADER_BOX_WIDTH = 192f;
    private static final float HEADER_BOX_HEIGHT = 78f;
    private static final float LOGO_WIDTH = 72f;
    private static final float LOGO_HEIGHT = 48f;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public byte[] generate(GuideRemissionCompany company, GuideRemissionDocument document) {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PageContext page = newPage(pdf, PAGE_HEIGHT - PAGE_MARGIN);

            page.y = writeHeader(pdf, page, company, document);
            page.y = writeGeneralDataSection(page, document, page.y - 10);
            page.y = writeRecipientSection(page, document, page.y - 12);
            page.y = writeTransportSection(page, document, page.y - 12);
            page.y = writeRelatedDocumentsSection(page, document.getRelatedDocuments(), page.y - 12);
            page = writeItemsSection(pdf, page, document, page.y - 12);
            page = writeSummary(pdf, page, document, page.y - 12);

            page.stream.close();
            appendPageNumbers(pdf);
            pdf.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new GuideRemissionIntegrationException("No se pudo generar el PDF de la guía de remisión.", ex);
        }
    }

    private float writeHeader(PDDocument pdf, PageContext page, GuideRemissionCompany company, GuideRemissionDocument document) throws IOException {
        float topY = PAGE_HEIGHT - PAGE_MARGIN;
        float boxX = PAGE_WIDTH - PAGE_MARGIN - HEADER_BOX_WIDTH;
        float boxY = topY - HEADER_BOX_HEIGHT;

        PDImageXObject logo = loadLogo(pdf, company);
        float textX = PAGE_MARGIN;
        if (logo != null) {
            float imageY = topY - LOGO_HEIGHT + 2f;
            page.stream.drawImage(logo, PAGE_MARGIN, imageY, LOGO_WIDTH, LOGO_HEIGHT);
            textX += LOGO_WIDTH + 12f;
        }

        float textWidth = boxX - textX - 14f;
        float y = topY - 4f;
        y = writeParagraph(page.stream, safe(company.getRazonSocial()).toUpperCase(Locale.ROOT), textX, y,
                textWidth, PDType1Font.HELVETICA_BOLD, TITLE_FONT, 15f) - 2f;

        if (notBlank(company.getNombreComercial())) {
            y = writeParagraph(page.stream, safe(company.getNombreComercial()), textX, y,
                    textWidth, PDType1Font.HELVETICA_OBLIQUE, SMALL_FONT, 10f) - 2f;
        }

        y = writeParagraph(page.stream, "Direccion fiscal: " + safe(company.getDomicilioFiscal()), textX, y,
                textWidth, PDType1Font.HELVETICA, SMALL_FONT, 10f) - 1f;

        String ubigeoLine = "Ubigeo: " + safe(company.getUbigeo())
                + "  Distrito: " + safe(company.getDistrito())
                + "  Provincia: " + safe(company.getProvincia());
        writeParagraph(page.stream, ubigeoLine, textX, y, textWidth, PDType1Font.HELVETICA, SMALL_FONT, 10f);

        drawRect(page.stream, boxX, boxY, HEADER_BOX_WIDTH, HEADER_BOX_HEIGHT);
        writeCenteredText(page.stream, "RUC: " + safe(company.getRuc()), boxX, boxY + 58f, HEADER_BOX_WIDTH, PDType1Font.HELVETICA_BOLD, 11f);
        writeCenteredText(page.stream, "GUIA DE REMISION", boxX, boxY + 40f, HEADER_BOX_WIDTH, PDType1Font.HELVETICA_BOLD, 11f);
        writeCenteredText(page.stream, "ELECTRONICA - REMITENTE", boxX, boxY + 25f, HEADER_BOX_WIDTH, PDType1Font.HELVETICA_BOLD, 10f);
        writeCenteredText(page.stream, safe(document.getSerie()) + "-" + safe(document.getNumero()), boxX, boxY + 9f, HEADER_BOX_WIDTH, PDType1Font.HELVETICA_BOLD, 12f);

        drawLine(page.stream, PAGE_MARGIN, boxY - 10f, PAGE_MARGIN + CONTENT_WIDTH, boxY - 10f, 0.8f);
        return boxY - 20f;
    }

    private float writeGeneralDataSection(PageContext page, GuideRemissionDocument document, float startY) throws IOException {
        drawSectionHeader(page.stream, "DATOS GENERALES", startY);
        float y = startY - 18f;
        float x1 = PAGE_MARGIN + 4f;
        float x2 = PAGE_MARGIN + 312f;

        y = writeTwoColumnRow(page.stream, y,
                field(x1, "Cliente", safe(document.getRecipientName()), 54f, 290f),
                field(x2, "Doc.", safe(document.getRecipientDocumentNumber()), 30f, 205f));

        y = writeThreeColumnRow(page.stream, y,
                field(x1, "F. Emision", formatDate(document.getIssueDate()), 56f, 154f),
                field(PAGE_MARGIN + 190f, "Hora", formatTime(document.getIssueTime()), 32f, 112f),
                field(PAGE_MARGIN + 308f, "F. Traslado", formatDate(document.getTransferDate()), 58f, 210f));

        y = writeFullWidthField(page.stream, y, field(x1, "Pto. Partida", buildLocation(document.getDepartureUbigeo(), document.getDepartureAddress()), 68f, CONTENT_WIDTH - 8f));
        y = writeFullWidthField(page.stream, y, field(x1, "Pto. Llegada", buildLocation(document.getArrivalUbigeo(), document.getArrivalAddress()), 68f, CONTENT_WIDTH - 8f));
        return y;
    }

    private float writeRecipientSection(PageContext page, GuideRemissionDocument document, float startY) throws IOException {
        drawSectionHeader(page.stream, "DATOS DEL DESTINATARIO", startY);
        float y = startY - 18f;
        float x1 = PAGE_MARGIN + 4f;
        float x2 = PAGE_MARGIN + 312f;

        y = writeTwoColumnRow(page.stream, y,
                field(x1, "Documento", safe(document.getRecipientDocumentNumber()), 58f, 290f),
                field(x2, "Tipo", recipientDocType(document.getRecipientDocumentType()), 30f, 150f));
        y = writeFullWidthField(page.stream, y,
                field(x1, "Nombre / Razon social", safe(document.getRecipientName()), 118f, CONTENT_WIDTH - 8f));
        y = writeTwoColumnRow(page.stream, y,
                field(x1, "Ubigeo llegada", safe(document.getArrivalUbigeo()), 74f, 250f),
                field(x2, "Establecimiento", safe(document.getArrivalEstablishmentCode()), 74f, 205f));
        return y;
    }

    private float writeTransportSection(PageContext page, GuideRemissionDocument document, float startY) throws IOException {
        drawSectionHeader(page.stream, "DATOS DEL TRASLADO", startY);
        float y = startY - 18f;
        float x1 = PAGE_MARGIN + 4f;
        float x2 = PAGE_MARGIN + 312f;

        y = writeTwoColumnRow(page.stream, y,
                field(x1, "Motivo", transferReason(document.getTransferReasonCode()), 42f, 250f),
                field(x2, "Modalidad", transferMode(document.getTransferModeCode()), 58f, 205f));

        if ("02".equals(document.getTransferModeCode())) {
            y = writeTwoColumnRow(page.stream, y,
                    field(x1, "Conductor", safe(document.getDriverFullName()), 56f, 290f),
                    field(x2, "DNI", safe(document.getDriverDni()), 28f, 150f));
            y = writeTwoColumnRow(page.stream, y,
                    field(x1, "Licencia", safe(document.getDriverLicense()), 46f, 250f),
                    field(x2, "Placa", safe(document.getVehiclePlate()), 34f, 150f));
        } else {
            y = writeTwoColumnRow(page.stream, y,
                    field(x1, "Transportista", safe(document.getTransporterName()), 66f, 290f),
                    field(x2, "RUC / Doc.", safe(document.getTransporterDocumentNumber()), 56f, 205f));
            if (notBlank(document.getLegacyTransportEntityId()) || notBlank(document.getLegacyTransportMtcNumber())) {
                y = writeTwoColumnRow(page.stream, y,
                        field(x1, "Campo legado", safe(document.getLegacyTransportEntityId()), 72f, 290f),
                        field(x2, "MTC", safe(document.getLegacyTransportMtcNumber()), 30f, 150f));
            }
        }

        return y;
    }

    private float writeRelatedDocumentsSection(PageContext page, List<GuideRemissionRelatedDocument> relatedDocuments, float startY) throws IOException {
        drawSectionHeader(page.stream, "COMPROBANTES RELACIONADOS", startY);
        float y = startY - 18f;
        float x = PAGE_MARGIN + 4f;

        if (relatedDocuments == null || relatedDocuments.isEmpty()) {
            writeText(page.stream, "No registrados.", x, y, PDType1Font.HELVETICA, VALUE_FONT);
            return y - 10f;
        }

        int index = 1;
        for (GuideRemissionRelatedDocument relatedDocument : relatedDocuments) {
            String text = index++ + ". " + documentTypeLabel(relatedDocument.getDocumentTypeCode()) + " "
                    + safe(relatedDocument.getSerie()) + "-" + safe(relatedDocument.getNumero());
            y = writeParagraph(page.stream, text, x, y, CONTENT_WIDTH - 8f, PDType1Font.HELVETICA, VALUE_FONT, LINE_HEIGHT) - 2f;
        }
        return y - 4f;
    }

    private PageContext writeItemsSection(PDDocument pdf, PageContext page, GuideRemissionDocument document, float startY) throws IOException {
        page = ensureSpace(pdf, page, 100f);
        if (!page.justCreated) {
            page.y = startY;
        }

        drawSectionHeader(page.stream, "DETALLE DE BIENES TRASLADADOS", page.y);
        page.y -= 18f;
        drawItemsTableHeader(page.stream, page.y);
        page.y -= 22f;
        page.justCreated = false;

        for (GuideRemissionDocumentItem item : document.getItems()) {
            List<String> descriptionLines = wrapByWidth(safe(item.getDescription()), PDType1Font.HELVETICA, VALUE_FONT, descriptionColumnWidth() - 8f);
            if (descriptionLines.isEmpty()) {
                descriptionLines = List.of("");
            }
            List<String> allocationLines = buildAllocationLines(item, descriptionColumnWidth() - 16f);

            float neededHeight = 8f + (descriptionLines.size() * 10f) + (allocationLines.size() * 9f) + 8f;
            page = ensureSpace(pdf, page, neededHeight + 20f);
            if (page.justCreated) {
                drawSectionHeader(page.stream, "DETALLE DE BIENES TRASLADADOS (CONT.)", page.y);
                page.y -= 18f;
                drawItemsTableHeader(page.stream, page.y);
                page.y -= 22f;
                page.justCreated = false;
            }

            float rowTop = page.y;
            float rowY = rowTop - 2f;
            float qtyX = PAGE_MARGIN + 4f;
            float umX = PAGE_MARGIN + 48f;
            float codeX = PAGE_MARGIN + 92f;
            float descX = PAGE_MARGIN + 192f;

            writeText(page.stream, safeNumber(item.getQuantity()), qtyX, rowY, PDType1Font.HELVETICA, VALUE_FONT);
            writeText(page.stream, safe(item.getUnitCode()), umX, rowY, PDType1Font.HELVETICA, VALUE_FONT);
            writeText(page.stream, safe(item.getItemCode()), codeX, rowY, PDType1Font.HELVETICA, VALUE_FONT);

            float currentDescY = rowY;
            for (String line : descriptionLines) {
                writeText(page.stream, line, descX, currentDescY, PDType1Font.HELVETICA, VALUE_FONT);
                currentDescY -= 10f;
            }
            for (String line : allocationLines) {
                writeText(page.stream, line, descX + 8f, currentDescY, PDType1Font.HELVETICA_OBLIQUE, 7.6f);
                currentDescY -= 9f;
            }

            float rowBottom = Math.min(currentDescY, rowTop - 16f) - 4f;
            drawLine(page.stream, PAGE_MARGIN, rowBottom, PAGE_MARGIN + CONTENT_WIDTH, rowBottom, 0.35f);
            page.y = rowBottom - 8f;
        }

        return page;
    }

    private PageContext writeSummary(PDDocument pdf, PageContext page, GuideRemissionDocument document, float startY) throws IOException {
        page = ensureSpace(pdf, page, 120f);
        if (!page.justCreated) {
            page.y = startY;
        }

        drawSectionHeader(page.stream, "RESUMEN", page.y);
        page.y -= 18f;
        float x1 = PAGE_MARGIN + 4f;
        float x2 = PAGE_MARGIN + 312f;

        page.y = writeTwoColumnRow(page.stream, page.y,
                field(x1, "Peso total", safeNumber(document.getTotalWeight()) + " Kg", 58f, 250f),
                field(x2, "Bultos", safe(document.getNumberOfPackages()), 42f, 150f));
        page.y = writeTwoColumnRow(page.stream, page.y,
                field(x1, "Estado", safe(document.getStatus()), 44f, 250f),
                field(x2, "Ticket", safe(document.getTicket()), 38f, 205f));
        page.y = writeFullWidthField(page.stream, page.y,
                field(x1, "Respuesta CDR", firstNotBlank(document.getCdrMessage(), document.getTicketResponseCode(), document.getCdrGenerated()), 78f, CONTENT_WIDTH - 8f));
        page.y = writeFullWidthField(page.stream, page.y,
                field(x1, "Observaciones", safe(document.getNotes()), 78f, CONTENT_WIDTH - 8f));
        return page;
    }

    private void drawItemsTableHeader(PDPageContentStream stream, float y) throws IOException {
        float headerY = y - 1f;
        fillRect(stream, PAGE_MARGIN, headerY - 12f, CONTENT_WIDTH, 16f, 238);
        drawRect(stream, PAGE_MARGIN, headerY - 12f, CONTENT_WIDTH, 16f);
        drawVerticalLine(stream, PAGE_MARGIN + 40f, headerY - 12f, headerY + 4f);
        drawVerticalLine(stream, PAGE_MARGIN + 84f, headerY - 12f, headerY + 4f);
        drawVerticalLine(stream, PAGE_MARGIN + 184f, headerY - 12f, headerY + 4f);

        writeText(stream, "CANT.", PAGE_MARGIN + 4f, y - 8f, PDType1Font.HELVETICA_BOLD, LABEL_FONT);
        writeText(stream, "U.M.", PAGE_MARGIN + 48f, y - 8f, PDType1Font.HELVETICA_BOLD, LABEL_FONT);
        writeText(stream, "CODIGO", PAGE_MARGIN + 92f, y - 8f, PDType1Font.HELVETICA_BOLD, LABEL_FONT);
        writeText(stream, "DESCRIPCION", PAGE_MARGIN + 192f, y - 8f, PDType1Font.HELVETICA_BOLD, LABEL_FONT);
    }

    private void drawSectionHeader(PDPageContentStream stream, String title, float y) throws IOException {
        fillRect(stream, PAGE_MARGIN, y - SECTION_HEIGHT + 2f, CONTENT_WIDTH, SECTION_HEIGHT, 230);
        drawRect(stream, PAGE_MARGIN, y - SECTION_HEIGHT + 2f, CONTENT_WIDTH, SECTION_HEIGHT);
        writeText(stream, title, PAGE_MARGIN + 4f, y - 8f, PDType1Font.HELVETICA_BOLD, 10f);
    }

    private float writeTwoColumnRow(PDPageContentStream stream, float y, Field left, Field right) throws IOException {
        FieldLayout leftLayout = measureField(left);
        FieldLayout rightLayout = measureField(right);
        int lines = Math.max(leftLayout.valueLines().size(), rightLayout.valueLines().size());
        float rowHeight = Math.max(LINE_HEIGHT, lines * LINE_HEIGHT);

        drawField(stream, left, leftLayout, y);
        drawField(stream, right, rightLayout, y);
        return y - rowHeight - 4f;
    }

    private float writeThreeColumnRow(PDPageContentStream stream, float y, Field a, Field b, Field c) throws IOException {
        FieldLayout layoutA = measureField(a);
        FieldLayout layoutB = measureField(b);
        FieldLayout layoutC = measureField(c);
        int lines = Math.max(layoutA.valueLines().size(), Math.max(layoutB.valueLines().size(), layoutC.valueLines().size()));
        float rowHeight = Math.max(LINE_HEIGHT, lines * LINE_HEIGHT);

        drawField(stream, a, layoutA, y);
        drawField(stream, b, layoutB, y);
        drawField(stream, c, layoutC, y);
        return y - rowHeight - 4f;
    }

    private float writeFullWidthField(PDPageContentStream stream, float y, Field field) throws IOException {
        FieldLayout layout = measureField(field);
        drawField(stream, field, layout, y);
        float rowHeight = Math.max(LINE_HEIGHT, layout.valueLines().size() * LINE_HEIGHT);
        return y - rowHeight - 4f;
    }

    private FieldLayout measureField(Field field) throws IOException {
        float valueWidth = Math.max(24f, field.width() - field.labelWidth() - 6f);
        List<String> valueLines = wrapByWidth(safe(field.value()), PDType1Font.HELVETICA, VALUE_FONT, valueWidth);
        if (valueLines.isEmpty()) {
            valueLines = List.of("");
        }
        return new FieldLayout(valueLines);
    }

    private void drawField(PDPageContentStream stream, Field field, FieldLayout layout, float y) throws IOException {
        writeText(stream, field.label() + ":", field.x(), y, PDType1Font.HELVETICA_BOLD, LABEL_FONT);
        float valueY = y;
        float valueX = field.x() + field.labelWidth();
        for (String line : layout.valueLines()) {
            writeText(stream, line, valueX, valueY, PDType1Font.HELVETICA, VALUE_FONT);
            valueY -= LINE_HEIGHT;
        }
    }

    private List<String> buildAllocationLines(GuideRemissionDocumentItem item, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        if (item.getSourceAllocations() == null || item.getSourceAllocations().isEmpty()) {
            return lines;
        }

        String summary = item.getSourceAllocations().stream()
                .map(this::formatAllocation)
                .collect(Collectors.joining(" | "));
        lines.addAll(wrapByWidth("Origen: " + summary, PDType1Font.HELVETICA_OBLIQUE, 7.6f, maxWidth));
        return lines;
    }

    private String formatAllocation(GuideRemissionDocumentItemAllocation allocation) {
        return documentTypeLabel(allocation.getRelatedDocumentTypeCode())
                + " " + safe(allocation.getRelatedDocumentSerie()) + "-" + safe(allocation.getRelatedDocumentNumero())
                + (allocation.getRelatedDocumentLineNo() != null ? " L" + allocation.getRelatedDocumentLineNo() : "")
                + " x" + safeNumber(allocation.getQuantity());
    }

    private PDImageXObject loadLogo(PDDocument pdf, GuideRemissionCompany company) {
        String ruc = safe(company.getRuc());
        String[] candidates = new String[] {
                "images/guide-remission/logo-" + ruc + ".png",
                "images/guide-remission/logo-" + ruc + ".jpg",
                "images/guide-remission/logo-" + ruc + ".jpeg",
                "images/guide-remission/" + ruc + ".png",
                "images/guide-remission/" + ruc + ".jpg",
                "images/guide-remission/" + ruc + ".jpeg",
                "images/guide-remission/logo.png",
                "images/guide-remission/logo.jpg",
                "images/guide-remission/logo.jpeg",
                "guide-remission/logo-" + ruc + ".png",
                "guide-remission/logo.png"
        };

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        for (String candidate : candidates) {
            try (InputStream inputStream = classLoader.getResourceAsStream(candidate)) {
                if (inputStream == null) {
                    continue;
                }
                return PDImageXObject.createFromByteArray(pdf, inputStream.readAllBytes(), candidate);
            } catch (Exception ignored) {
                // Continuar con el siguiente recurso.
            }
        }
        return null;
    }

    private PageContext newPage(PDDocument pdf, float startY) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        pdf.addPage(page);
        PDPageContentStream stream = new PDPageContentStream(pdf, page);
        return new PageContext(stream, startY, true);
    }

    private PageContext ensureSpace(PDDocument pdf, PageContext currentPage, float requiredHeight) throws IOException {
        if (currentPage.y - requiredHeight > BOTTOM_LIMIT) {
            return currentPage;
        }
        currentPage.stream.close();
        return newPage(pdf, PAGE_HEIGHT - PAGE_MARGIN);
    }

    private void appendPageNumbers(PDDocument pdf) throws IOException {
        int totalPages = pdf.getNumberOfPages();
        for (int i = 0; i < totalPages; i++) {
            PDPage page = pdf.getPage(i);
            try (PDPageContentStream stream = new PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true)) {
                drawLine(stream, PAGE_MARGIN, 28f, PAGE_MARGIN + CONTENT_WIDTH, 28f, 0.5f);
                writeText(stream, "Pag. " + (i + 1) + " de " + totalPages, PAGE_WIDTH - PAGE_MARGIN - 56f, 16f, PDType1Font.HELVETICA, 8f);
            }
        }
    }

    private List<String> wrapByWidth(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        if (!notBlank(text)) {
            return lines;
        }

        String[] words = safe(text).split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (textWidth(font, fontSize, candidate) <= maxWidth) {
                current = new StringBuilder(candidate);
                continue;
            }

            if (current.length() > 0) {
                lines.add(current.toString());
            }

            if (textWidth(font, fontSize, word) <= maxWidth) {
                current = new StringBuilder(word);
            } else {
                List<String> parts = splitLongWord(word, font, fontSize, maxWidth);
                if (!parts.isEmpty()) {
                    for (int i = 0; i < parts.size() - 1; i++) {
                        lines.add(parts.get(i));
                    }
                    current = new StringBuilder(parts.get(parts.size() - 1));
                } else {
                    current = new StringBuilder(word);
                }
            }
        }

        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private List<String> splitLongWord(String word, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (char ch : word.toCharArray()) {
            String candidate = current + String.valueOf(ch);
            if (current.length() == 0 || textWidth(font, fontSize, candidate) <= maxWidth) {
                current = new StringBuilder(candidate);
            } else {
                parts.add(current.toString());
                current = new StringBuilder(String.valueOf(ch));
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }

    private float writeParagraph(PDPageContentStream stream, String text, float x, float y, float width,
                                 PDFont font, float fontSize, float lineHeight) throws IOException {
        List<String> lines = wrapByWidth(text, font, fontSize, width);
        if (lines.isEmpty()) {
            lines = List.of("");
        }
        float currentY = y;
        for (String line : lines) {
            writeText(stream, line, x, currentY, font, fontSize);
            currentY -= lineHeight;
        }
        return currentY;
    }

    private float textWidth(PDFont font, float fontSize, String text) throws IOException {
        return font.getStringWidth(safe(text)) / 1000f * fontSize;
    }

    private void fillRect(PDPageContentStream stream, float x, float y, float width, float height, int gray) throws IOException {
        stream.saveGraphicsState();
        stream.setNonStrokingColor(gray);
        stream.addRect(x, y, width, height);
        stream.fill();
        stream.restoreGraphicsState();
    }

    private void drawRect(PDPageContentStream stream, float x, float y, float width, float height) throws IOException {
        stream.saveGraphicsState();
        stream.setLineWidth(0.7f);
        stream.addRect(x, y, width, height);
        stream.stroke();
        stream.restoreGraphicsState();
    }

    private void drawLine(PDPageContentStream stream, float x1, float y1, float x2, float y2, float lineWidth) throws IOException {
        stream.saveGraphicsState();
        stream.setLineWidth(lineWidth);
        stream.moveTo(x1, y1);
        stream.lineTo(x2, y2);
        stream.stroke();
        stream.restoreGraphicsState();
    }

    private void drawVerticalLine(PDPageContentStream stream, float x, float y1, float y2) throws IOException {
        drawLine(stream, x, y1, x, y2, 0.4f);
    }

    private void writeCenteredText(PDPageContentStream stream, String text, float x, float y, float width,
                                   PDFont font, float fontSize) throws IOException {
        float centeredX = x + ((width - textWidth(font, fontSize, text)) / 2f);
        writeText(stream, text, centeredX, y, font, fontSize);
    }

    private void writeText(PDPageContentStream stream, String text, float x, float y, PDFont font, float fontSize) throws IOException {
        stream.beginText();
        stream.setFont(font, fontSize);
        stream.newLineAtOffset(x, y);
        stream.showText(safe(text));
        stream.endText();
    }

    private Field field(float x, String label, String value, float labelWidth, float width) {
        return new Field(x, label, value, labelWidth, width);
    }

    private String buildLocation(String ubigeo, String address) {
        if (notBlank(ubigeo) && notBlank(address)) {
            return ubigeo + " - " + address;
        }
        return firstNotBlank(address, ubigeo);
    }

    private float descriptionColumnWidth() {
        return CONTENT_WIDTH - 184f;
    }

    private String formatDate(LocalDate date) {
        return date == null ? "" : date.format(DATE_FORMATTER);
    }

    private String formatTime(LocalTime time) {
        return time == null ? "" : time.format(TIME_FORMATTER);
    }

    private String documentTypeLabel(String typeCode) {
        return switch (safe(typeCode)) {
            case "01" -> "Factura";
            case "03" -> "Boleta";
            default -> safe(typeCode);
        };
    }

    private String transferMode(String code) {
        return switch (safe(code)) {
            case "01" -> "Publico";
            case "02" -> "Privado";
            default -> safe(code);
        };
    }

    private String transferReason(String code) {
        return switch (safe(code)) {
            case "01" -> "Venta";
            case "04" -> "Traslado entre establecimientos";
            case "08" -> "Importacion";
            case "09" -> "Exportacion";
            default -> safe(code);
        };
    }

    private String recipientDocType(Integer docType) {
        if (docType == null) {
            return "";
        }
        return switch (docType) {
            case 1 -> "DNI";
            case 6 -> "RUC";
            default -> String.valueOf(docType);
        };
    }

    private String safeNumber(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private String firstNotBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (notBlank(value)) {
                return safe(value);
            }
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private record Field(float x, String label, String value, float labelWidth, float width) {
    }

    private record FieldLayout(List<String> valueLines) {
    }

    private static class PageContext {
        private final PDPageContentStream stream;
        private float y;
        private boolean justCreated;

        private PageContext(PDPageContentStream stream, float y, boolean justCreated) {
            this.stream = stream;
            this.y = y;
            this.justCreated = justCreated;
        }
    }
}
