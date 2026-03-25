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
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class PdfBoxGuideRemissionPdfGenerator implements GuideRemissionPdfGenerator {
    private static final float PAGE_MARGIN = 36f;
    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    private static final float CONTENT_WIDTH = PAGE_WIDTH - (PAGE_MARGIN * 2);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public byte[] generate(GuideRemissionCompany company, GuideRemissionDocument document) {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PageContext page = newPage(pdf, PAGE_HEIGHT - PAGE_MARGIN - 90);

            page.y = writeHeader(page, company, document);
            page.y = writeRecipientSection(page, document, page.y - 10);
            page.y = writeTransportSection(page, document, page.y - 10);
            page.y = writeRelatedDocumentsSection(page, document.getRelatedDocuments(), page.y - 10);
            page = writeItemsSection(pdf, page, document, page.y - 12);
            page = writeSummary(pdf, page, document);
            page.stream.close();
            appendPageNumbers(pdf);

            pdf.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new GuideRemissionIntegrationException("No se pudo generar el PDF de la guía de remisión.", ex);
        }
    }

    private float writeHeader(PageContext page, GuideRemissionCompany company, GuideRemissionDocument document) throws IOException {
        writeText(page.stream, safe(company.getRazonSocial()).toUpperCase(Locale.ROOT), PAGE_MARGIN, PAGE_HEIGHT - 40, PDType1Font.HELVETICA_BOLD, 14);
        writeText(page.stream, "Direccion fiscal: " + safe(company.getDomicilioFiscal()), PAGE_MARGIN, PAGE_HEIGHT - 58, PDType1Font.HELVETICA, 9);
        writeText(page.stream, "Ubigeo: " + safe(company.getUbigeo()) + "  Distrito: " + safe(company.getDistrito()), PAGE_MARGIN, PAGE_HEIGHT - 72, PDType1Font.HELVETICA, 9);

        float boxWidth = 175f;
        float boxHeight = 72f;
        float boxX = PAGE_WIDTH - PAGE_MARGIN - boxWidth;
        float boxY = PAGE_HEIGHT - PAGE_MARGIN - boxHeight + 6;
        drawRect(page.stream, boxX, boxY, boxWidth, boxHeight);
        writeCenteredText(page.stream, "RUC: " + safe(company.getRuc()), boxX, boxY + 52, boxWidth, PDType1Font.HELVETICA_BOLD, 11);
        writeCenteredText(page.stream, "GUIA DE REMISION", boxX, boxY + 35, boxWidth, PDType1Font.HELVETICA_BOLD, 10);
        writeCenteredText(page.stream, "ELECTRONICA - REMITENTE", boxX, boxY + 21, boxWidth, PDType1Font.HELVETICA_BOLD, 10);
        writeCenteredText(page.stream, safe(document.getSerie()) + "-" + safe(document.getNumero()), boxX, boxY + 7, boxWidth, PDType1Font.HELVETICA_BOLD, 11);

        float y = PAGE_HEIGHT - 104;
        writeLabelValue(page.stream, PAGE_MARGIN, y, "Cliente", safe(document.getRecipientName()));
        writeLabelValue(page.stream, PAGE_MARGIN + 250, y, "Doc.", safe(document.getRecipientDocumentNumber()));
        y -= 14;
        writeLabelValue(page.stream, PAGE_MARGIN, y, "F. Emision", formatDate(document.getIssueDate()));
        writeLabelValue(page.stream, PAGE_MARGIN + 150, y, "Hora", formatTime(document.getIssueTime()));
        writeLabelValue(page.stream, PAGE_MARGIN + 250, y, "F. Traslado", formatDate(document.getTransferDate()));
        y -= 14;
        writeLabelValue(page.stream, PAGE_MARGIN, y, "Pto. Partida", safe(document.getDepartureAddress()));
        y -= 14;
        writeLabelValue(page.stream, PAGE_MARGIN, y, "Pto. Llegada", safe(document.getArrivalAddress()));
        return y - 4;
    }

    private float writeRecipientSection(PageContext page, GuideRemissionDocument document, float startY) throws IOException {
        drawSectionTitle(page.stream, "DATOS DEL DESTINATARIO", startY);
        float y = startY - 16;
        writeLabelValue(page.stream, PAGE_MARGIN, y, "Documento", safe(document.getRecipientDocumentNumber()));
        writeLabelValue(page.stream, PAGE_MARGIN + 250, y, "Tipo", recipientDocType(document.getRecipientDocumentType()));
        y -= 14;
        writeLabelValue(page.stream, PAGE_MARGIN, y, "Nombre / Razon social", safe(document.getRecipientName()));
        y -= 14;
        writeLabelValue(page.stream, PAGE_MARGIN, y, "Ubigeo llegada", safe(document.getArrivalUbigeo()));
        return y;
    }

    private float writeTransportSection(PageContext page, GuideRemissionDocument document, float startY) throws IOException {
        drawSectionTitle(page.stream, "DATOS DEL TRASLADO", startY);
        float y = startY - 16;
        writeLabelValue(page.stream, PAGE_MARGIN, y, "Motivo", transferReason(document.getTransferReasonCode()));
        writeLabelValue(page.stream, PAGE_MARGIN + 250, y, "Modalidad", transferMode(document.getTransferModeCode()));
        y -= 14;

        if ("02".equals(document.getTransferModeCode())) {
            writeLabelValue(page.stream, PAGE_MARGIN, y, "Conductor", safe(document.getDriverFullName()));
            writeLabelValue(page.stream, PAGE_MARGIN + 250, y, "DNI", safe(document.getDriverDni()));
            y -= 14;
            writeLabelValue(page.stream, PAGE_MARGIN, y, "Licencia", safe(document.getDriverLicense()));
            writeLabelValue(page.stream, PAGE_MARGIN + 250, y, "Placa", safe(document.getVehiclePlate()));
            y -= 14;
        } else {
            writeLabelValue(page.stream, PAGE_MARGIN, y, "Transportista", safe(document.getTransporterName()));
            writeLabelValue(page.stream, PAGE_MARGIN + 250, y, "RUC / Doc.", safe(document.getTransporterDocumentNumber()));
            y -= 14;
            if (notBlank(document.getLegacyTransportEntityId()) || notBlank(document.getLegacyTransportMtcNumber())) {
                writeLabelValue(page.stream, PAGE_MARGIN, y, "Campo legado", safe(document.getLegacyTransportEntityId()));
                writeLabelValue(page.stream, PAGE_MARGIN + 250, y, "MTC", safe(document.getLegacyTransportMtcNumber()));
                y -= 14;
            }
        }

        return y;
    }

    private float writeRelatedDocumentsSection(PageContext page, List<GuideRemissionRelatedDocument> relatedDocuments, float startY) throws IOException {
        drawSectionTitle(page.stream, "COMPROBANTES RELACIONADOS", startY);
        float y = startY - 16;

        if (relatedDocuments == null || relatedDocuments.isEmpty()) {
            writeText(page.stream, "No registrados.", PAGE_MARGIN, y, PDType1Font.HELVETICA, 9);
            return y - 6;
        }

        int index = 1;
        for (GuideRemissionRelatedDocument document : relatedDocuments) {
            String text = index++ + ". " + documentTypeLabel(document.getDocumentTypeCode()) + " "
                    + safe(document.getSerie()) + "-" + safe(document.getNumero());
            writeText(page.stream, text, PAGE_MARGIN, y, PDType1Font.HELVETICA, 9);
            y -= 12;
        }
        return y + 2;
    }

    private PageContext writeItemsSection(PDDocument pdf, PageContext page, GuideRemissionDocument document, float startY) throws IOException {
        page = ensureSpace(pdf, page, 70);
        if (!page.justCreated) {
            page.y = startY;
        }
        drawSectionTitle(page.stream, "DETALLE DE BIENES TRASLADADOS", page.y);
        page.y -= 18;
        drawTableHeader(page.stream, page.y);
        page.y -= 16;
        page.justCreated = false;

        for (GuideRemissionDocumentItem item : document.getItems()) {
            List<String> allocationLines = buildAllocationLines(item);
            float neededHeight = 16 + (allocationLines.size() * 10f) + 6;
            page = ensureSpace(pdf, page, neededHeight + 30);

            if (page.justCreated) {
                drawSectionTitle(page.stream, "DETALLE DE BIENES TRASLADADOS (CONT.)", page.y);
                page.y -= 18;
                drawTableHeader(page.stream, page.y);
                page.y -= 16;
                page.justCreated = false;
            }

            writeText(page.stream, safeNumber(item.getQuantity()), PAGE_MARGIN, page.y, PDType1Font.HELVETICA, 9);
            writeText(page.stream, safe(item.getUnitCode()), PAGE_MARGIN + 50, page.y, PDType1Font.HELVETICA, 9);
            writeText(page.stream, safe(item.getItemCode()), PAGE_MARGIN + 95, page.y, PDType1Font.HELVETICA, 9);
            writeText(page.stream, safe(item.getDescription()), PAGE_MARGIN + 170, page.y, PDType1Font.HELVETICA, 9);
            page.y -= 12;

            for (String allocationLine : allocationLines) {
                writeText(page.stream, allocationLine, PAGE_MARGIN + 170, page.y, PDType1Font.HELVETICA_OBLIQUE, 8);
                page.y -= 10;
            }
            page.y -= 4;
        }

        return page;
    }

    private PageContext writeSummary(PDDocument pdf, PageContext page, GuideRemissionDocument document) throws IOException {
        page = ensureSpace(pdf, page, 80);
        drawSectionTitle(page.stream, "RESUMEN", page.y - 4);
        page.y -= 20;
        writeLabelValue(page.stream, PAGE_MARGIN, page.y, "Peso total", safeNumber(document.getTotalWeight()) + " Kg");
        writeLabelValue(page.stream, PAGE_MARGIN + 250, page.y, "Bultos", safe(document.getNumberOfPackages()));
        page.y -= 14;
        writeLabelValue(page.stream, PAGE_MARGIN, page.y, "Estado", safe(document.getStatus()));
        writeLabelValue(page.stream, PAGE_MARGIN + 250, page.y, "Ticket", safe(document.getTicket()));
        page.y -= 14;
        writeLabelValue(page.stream, PAGE_MARGIN, page.y, "Respuesta CDR", safe(document.getTicketResponseCode()));
        page.y -= 14;
        writeLabelValue(page.stream, PAGE_MARGIN, page.y, "Observaciones", safe(document.getNotes()));
        return page;
    }

    private List<String> buildAllocationLines(GuideRemissionDocumentItem item) {
        List<String> lines = new ArrayList<>();
        if (item.getSourceAllocations() == null || item.getSourceAllocations().isEmpty()) {
            return lines;
        }

        String summary = item.getSourceAllocations().stream()
                .map(this::formatAllocation)
                .collect(Collectors.joining(" | "));

        lines.addAll(wrap("Origen: " + summary, 70));
        return lines;
    }

    private String formatAllocation(GuideRemissionDocumentItemAllocation allocation) {
        return documentTypeLabel(allocation.getRelatedDocumentTypeCode())
                + " " + safe(allocation.getRelatedDocumentSerie()) + "-" + safe(allocation.getRelatedDocumentNumero())
                + (allocation.getRelatedDocumentLineNo() != null ? " L" + allocation.getRelatedDocumentLineNo() : "")
                + " x" + safeNumber(allocation.getQuantity());
    }

    private PageContext newPage(PDDocument pdf, float startY) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        pdf.addPage(page);
        PDPageContentStream stream = new PDPageContentStream(pdf, page);
        return new PageContext(stream, startY, true);
    }

    private PageContext ensureSpace(PDDocument pdf, PageContext currentPage, float requiredHeight) throws IOException {
        if (currentPage.y - requiredHeight > PAGE_MARGIN) {
            return currentPage;
        }

        currentPage.stream.close();
        return newPage(pdf, PAGE_HEIGHT - PAGE_MARGIN - 40);
    }

    private void appendPageNumbers(PDDocument pdf) throws IOException {
        int totalPages = pdf.getNumberOfPages();
        for (int i = 0; i < totalPages; i++) {
            PDPage page = pdf.getPage(i);
            try (PDPageContentStream stream = new PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true)) {
                writeText(stream, "Pag " + (i + 1) + " de " + totalPages, PAGE_WIDTH - PAGE_MARGIN - 60, 22, PDType1Font.HELVETICA, 8);
            }
        }
    }

    private void drawTableHeader(PDPageContentStream stream, float y) throws IOException {
        stream.setLineWidth(0.7f);
        stream.moveTo(PAGE_MARGIN, y + 4);
        stream.lineTo(PAGE_MARGIN + CONTENT_WIDTH, y + 4);
        stream.stroke();

        writeText(stream, "CANT.", PAGE_MARGIN, y - 8, PDType1Font.HELVETICA_BOLD, 9);
        writeText(stream, "U.M.", PAGE_MARGIN + 50, y - 8, PDType1Font.HELVETICA_BOLD, 9);
        writeText(stream, "CODIGO", PAGE_MARGIN + 95, y - 8, PDType1Font.HELVETICA_BOLD, 9);
        writeText(stream, "DESCRIPCION", PAGE_MARGIN + 170, y - 8, PDType1Font.HELVETICA_BOLD, 9);
    }

    private void drawSectionTitle(PDPageContentStream stream, String title, float y) throws IOException {
        stream.setLineWidth(0.7f);
        stream.moveTo(PAGE_MARGIN, y + 3);
        stream.lineTo(PAGE_MARGIN + CONTENT_WIDTH, y + 3);
        stream.stroke();
        writeText(stream, title, PAGE_MARGIN, y - 8, PDType1Font.HELVETICA_BOLD, 10);
    }

    private void drawRect(PDPageContentStream stream, float x, float y, float width, float height) throws IOException {
        stream.addRect(x, y, width, height);
        stream.stroke();
    }

    private void writeLabelValue(PDPageContentStream stream, float x, float y, String label, String value) throws IOException {
        writeText(stream, label + ":", x, y, PDType1Font.HELVETICA_BOLD, 9);
        writeText(stream, safe(value), x + 55, y, PDType1Font.HELVETICA, 9);
    }

    private void writeCenteredText(PDPageContentStream stream, String text, float x, float y, float width, PDType1Font font, float fontSize) throws IOException {
        float textWidth = font.getStringWidth(safe(text)) / 1000 * fontSize;
        float centeredX = x + ((width - textWidth) / 2);
        writeText(stream, text, centeredX, y, font, fontSize);
    }

    private void writeText(PDPageContentStream stream, String text, float x, float y, PDType1Font font, float fontSize) throws IOException {
        stream.beginText();
        stream.setFont(font, fontSize);
        stream.newLineAtOffset(x, y);
        stream.showText(safe(text));
        stream.endText();
    }

    private List<String> wrap(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        if (!notBlank(text)) {
            return lines;
        }

        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (line.length() == 0) {
                line.append(word);
                continue;
            }
            if (line.length() + word.length() + 1 <= maxChars) {
                line.append(' ').append(word);
            } else {
                lines.add(line.toString());
                line = new StringBuilder(word);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    private String formatDate(java.time.LocalDate date) {
        return date == null ? "" : date.format(DATE_FORMATTER);
    }

    private String formatTime(java.time.LocalTime time) {
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

    private String safe(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
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
