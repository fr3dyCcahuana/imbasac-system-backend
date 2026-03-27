package com.paulfernandosr.possystembackend.guideremission.infrastructure.adapter.output.pdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionCompany;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionDocument;
import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionDocumentItem;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class PdfBoxGuideRemissionPdfGenerator implements GuideRemissionPdfGenerator {

    private static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();

    private static final float MARGIN_LEFT = 16f;
    private static final float MARGIN_RIGHT = 16f;
    private static final float MARGIN_TOP = 16f;
    private static final float MARGIN_BOTTOM = 18f;
    private static final float CONTENT_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT;

    private static final float HEADER_HEIGHT = 86f;
    private static final float DOC_BOX_WIDTH = 236f;
    private static final float DOC_BOX_HEIGHT = 82f;
    private static final float LOGO_MAX_WIDTH = 104f;
    private static final float LOGO_MAX_HEIGHT = 30f;

    private static final float FOOTER_AREA_HEIGHT = 154f;
    private static final float FOOTER_TOP_Y = MARGIN_BOTTOM + FOOTER_AREA_HEIGHT;

    private static final float SECTION_HEADER_HEIGHT = 14f;
    private static final float TABLE_HEADER_HEIGHT = 18f;

    private static final float FONT_COMPANY = 12.6f;
    private static final float FONT_DOC_BOX = 11.2f;
    private static final float FONT_DOC_NUMBER = 13.0f;
    private static final float FONT_SECTION = 7.8f;
    private static final float FONT_VALUE = 8.0f;
    private static final float FONT_SMALL = 6.9f;
    private static final float FONT_CODE = 7.5f;

    private static final int COLOR_TEXT = 20;
    private static final int COLOR_LINE = 75;
    private static final int COLOR_FILL = 242;
    private static final int COLOR_SECONDARY = 95;

    private static final float COL_NO = 23f;
    private static final float COL_CODE = 88f;
    private static final float COL_DESC = 350f;
    private static final float COL_QTY = 50f;
    private static final float COL_UNIT = 56f;

    private static final float QR_SIZE = 84f;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @Override
    public byte[] generate(GuideRemissionCompany company, GuideRemissionDocument document) {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            List<PreparedItemRow> preparedRows = prepareItemRows(document.getItems());

            int startIndex = 0;
            boolean firstPage = true;
            boolean keepGenerating = true;

            while (keepGenerating) {
                PDPage page = new PDPage(PDRectangle.A4);
                pdf.addPage(page);

                try (PDPageContentStream stream = new PDPageContentStream(pdf, page)) {
                    float cursorY = PAGE_HEIGHT - MARGIN_TOP;

                    if (firstPage) {
                        cursorY = drawMainHeader(pdf, stream, company, document, cursorY);
                        cursorY = drawTopSections(stream, document, cursorY - 8f);
                    } else {
                        cursorY = drawContinuationHeader(stream, company, document, cursorY);
                    }

                    float tableTopY = cursorY - 8f;
                    boolean lastPage = canFitRemainingRows(tableTopY, preparedRows, startIndex);
                    TableResult tableResult = drawItemsTable(stream, tableTopY, preparedRows, startIndex, lastPage);
                    startIndex = tableResult.nextIndex();

                    if (lastPage) {
                        drawFooter(pdf, stream, company, document);
                        keepGenerating = false;
                    } else {
                        drawContinuationNotice(stream);
                        firstPage = false;
                    }
                }
            }

            appendPageNumbers(pdf);
            pdf.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new GuideRemissionIntegrationException("No se pudo generar el PDF de la guía de remisión.", ex);
        }
    }

    private float drawMainHeader(PDDocument pdf,
                                 PDPageContentStream stream,
                                 GuideRemissionCompany company,
                                 GuideRemissionDocument document,
                                 float topY) throws IOException {
        float bottomY = topY - HEADER_HEIGHT;
        float docBoxX = PAGE_WIDTH - MARGIN_RIGHT - DOC_BOX_WIDTH;

        float leftX = MARGIN_LEFT;

        // Más aire a izquierda y derecha para que el bloque se vea más centrado
        float contentInset = 50f;
        float textX = leftX + contentInset;
        float textWidth = docBoxX - leftX - (contentInset * 2f);

        PDImageXObject logo = loadLogo(pdf, company);

        float textY = topY - 12f;
        textY = writeWrapped(stream,
                safe(company.getRazonSocial()).toUpperCase(Locale.ROOT),
                textX,
                textY,
                textWidth,
                PDType1Font.HELVETICA_BOLD,
                FONT_COMPANY,
                12.0f,
                COLOR_TEXT) - 2f;

        if (logo != null) {
            ImageFit fit = fitImage(logo, LOGO_MAX_WIDTH, LOGO_MAX_HEIGHT);

            // Un poco más alineado dentro del bloque
            float logoX = textX + 2f;

            // Sube un poco el logo
            float logoY = textY - fit.height() + 4f;

            stream.drawImage(logo, logoX, logoY, fit.width(), fit.height());
            textY = logoY - 4f;
        }

        textY = writeWrapped(stream,
                "Dirección fiscal: " + firstNotBlank(company.getDomicilioFiscal(), "-"),
                textX,
                textY - 10f,
                textWidth,
                PDType1Font.HELVETICA,
                FONT_SMALL,
                7.9f,
                COLOR_TEXT) - 1f;

        writeWrapped(stream,
                buildCompanyLocationLine(company),
                textX,
                textY,
                textWidth,
                PDType1Font.HELVETICA,
                FONT_SMALL,
                7.9f,
                COLOR_TEXT);

        drawRect(stream, docBoxX, bottomY + 8f, DOC_BOX_WIDTH, DOC_BOX_HEIGHT, 0.45f, COLOR_LINE);
        writeCentered(stream, "R.U.C. " + safe(company.getRuc()), docBoxX, bottomY + 67f, DOC_BOX_WIDTH, PDType1Font.HELVETICA_BOLD, FONT_DOC_BOX + 0.4f, COLOR_TEXT);
        writeCentered(stream, "GUÍA DE REMISIÓN", docBoxX, bottomY + 46f, DOC_BOX_WIDTH, PDType1Font.HELVETICA_BOLD, FONT_DOC_BOX, COLOR_TEXT);
        writeCentered(stream, "ELECTRÓNICA - REMITENTE", docBoxX, bottomY + 30f, DOC_BOX_WIDTH, PDType1Font.HELVETICA_BOLD, FONT_DOC_BOX - 0.4f, COLOR_TEXT);
        writeCentered(stream, "N° " + safe(document.getSerie()) + "-" + safe(document.getNumero()), docBoxX, bottomY + 12f, DOC_BOX_WIDTH, PDType1Font.HELVETICA_BOLD, FONT_DOC_NUMBER, COLOR_TEXT);

        drawLine(stream, MARGIN_LEFT, bottomY, PAGE_WIDTH - MARGIN_RIGHT, bottomY, 0.35f, COLOR_LINE);
        return bottomY - 8f;
    }

    private float resolveFieldValueOffset(int cols, float cellWidth, String label) throws IOException {
        float baseWidth = resolveFieldLabelWidth(cols, cellWidth);
        float labelTextWidth = textWidth(PDType1Font.HELVETICA_BOLD, FONT_VALUE, safe(label) + ":");
        return Math.max(baseWidth, labelTextWidth + 8f);
    }

    private float drawContinuationHeader(PDPageContentStream stream,
                                         GuideRemissionCompany company,
                                         GuideRemissionDocument document,
                                         float topY) throws IOException {
        writeText(stream,
                firstNotBlank(company.getRazonSocial(), "EMPRESA").toUpperCase(Locale.ROOT),
                MARGIN_LEFT,
                topY - 4f,
                PDType1Font.HELVETICA_BOLD,
                FONT_VALUE,
                COLOR_TEXT);
        writeText(stream,
                "GUÍA DE REMISIÓN ELECTRÓNICA - REMITENTE",
                MARGIN_LEFT + 145f,
                topY - 4f,
                PDType1Font.HELVETICA_BOLD,
                FONT_VALUE,
                COLOR_TEXT);
        writeText(stream,
                safe(document.getSerie()) + "-" + safe(document.getNumero()),
                PAGE_WIDTH - MARGIN_RIGHT - 88f,
                topY - 4f,
                PDType1Font.HELVETICA_BOLD,
                FONT_VALUE,
                COLOR_TEXT);
        drawLine(stream, MARGIN_LEFT, topY - 10f, PAGE_WIDTH - MARGIN_RIGHT, topY - 10f, 0.30f, COLOR_LINE);
        return topY - 18f;
    }

    private float drawTopSections(PDPageContentStream stream,
                                  GuideRemissionDocument document,
                                  float topY) throws IOException {
        float gap = 8f;
        float leftWidth = 320f;
        float rightWidth = CONTENT_WIDTH - leftWidth - gap;
        float leftX = MARGIN_LEFT;
        float rightX = leftX + leftWidth + gap;

        List<FieldRow> generalRows = buildGeneralRows(document);
        List<FieldRow> transferRows = buildTransferRows(document);

        float leftHeight = measureFieldRows(generalRows, leftWidth - 12f);
        float rightHeight = measureFieldRows(transferRows, rightWidth - 12f);
        float bodyHeight = Math.max(leftHeight, rightHeight);

        drawSectionHeader(stream, leftX, topY, leftWidth, "DATOS GENERALES");
        drawSectionHeader(stream, rightX, topY, rightWidth, "TRASLADO");
        drawRect(stream, leftX, topY - bodyHeight, leftWidth, bodyHeight - SECTION_HEADER_HEIGHT, 0.30f, COLOR_LINE);
        drawRect(stream, rightX, topY - bodyHeight, rightWidth, bodyHeight - SECTION_HEADER_HEIGHT, 0.30f, COLOR_LINE);

        drawFieldRows(stream, leftX + 6f, topY - 27f, leftWidth - 12f, generalRows);
        drawFieldRows(stream, rightX + 6f, topY - 27f, rightWidth - 12f, transferRows);

        return topY - bodyHeight - 6f;
    }

    private List<FieldRow> buildGeneralRows(GuideRemissionDocument document) {
        List<FieldRow> rows = new ArrayList<>();
        rows.add(new FieldRow(List.of(
                new FieldCell("Fecha de emisión", formatDate(document.getIssueDate())),
                new FieldCell("Hora", formatTime(document.getIssueTime()))
        )));
        rows.add(new FieldRow(List.of(new FieldCell("Fecha inicio traslado", formatDate(document.getTransferDate())))));
        rows.add(new FieldRow(List.of(new FieldCell("Destinatario", firstNotBlank(document.getRecipientName(), "-")))));
        rows.add(new FieldRow(List.of(new FieldCell(recipientDocLabel(document.getRecipientDocumentType()), firstNotBlank(document.getRecipientDocumentNumber(), "-")))));
        rows.add(new FieldRow(List.of(new FieldCell("Punto de partida", buildLocation(document.getDepartureUbigeo(), document.getDepartureAddress())))));
        rows.add(new FieldRow(List.of(new FieldCell("Punto de llegada", buildLocation(document.getArrivalUbigeo(), document.getArrivalAddress())))));
        return rows;
    }

    private List<FieldRow> buildTransferRows(GuideRemissionDocument document) {
        List<FieldRow> rows = new ArrayList<>();
        rows.add(new FieldRow(List.of(new FieldCell("Motivo de traslado", transferReason(document.getTransferReasonCode())))));
        rows.add(new FieldRow(List.of(new FieldCell("Modalidad", transferMode(document.getTransferModeCode())))));
        rows.add(new FieldRow(List.of(new FieldCell("Transportista", resolveTransportDisplay(document)))));
        rows.add(new FieldRow(List.of(new FieldCell("Doc. transportista", resolveTransportDocument(document)))));

        if (notBlank(document.getVehiclePlate()) || notBlank(document.getDriverDni()) || notBlank(document.getDriverLicense())) {
            rows.add(new FieldRow(List.of(
                    new FieldCell("Placa", firstNotBlank(document.getVehiclePlate(), "-")),
                    new FieldCell("DNI conductor", firstNotBlank(document.getDriverDni(), document.getDriverLicense(), "-"))
            )));
        }

        if (notBlank(document.getDriverFullName())) {
            rows.add(new FieldRow(List.of(new FieldCell("Conductor", safe(document.getDriverFullName())))));
        }
        return rows;
    }

    private boolean canFitRemainingRows(float tableTopY, List<PreparedItemRow> rows, int startIndex) {
        float availableHeight = (tableTopY - SECTION_HEADER_HEIGHT - TABLE_HEADER_HEIGHT) - (FOOTER_TOP_Y + 2f);
        if (availableHeight < 0) {
            return false;
        }

        float totalHeight = 0f;
        for (int i = startIndex; i < rows.size(); i++) {
            totalHeight += rows.get(i).height();
        }
        return totalHeight <= availableHeight;
    }

    private TableResult drawItemsTable(PDPageContentStream stream,
                                       float topY,
                                       List<PreparedItemRow> rows,
                                       int startIndex,
                                       boolean lastPage) throws IOException {
        drawSectionHeader(stream, MARGIN_LEFT, topY, CONTENT_WIDTH, "DATOS DEL BIEN TRANSPORTADO");
        drawTableHeader(stream, topY - SECTION_HEADER_HEIGHT);

        float cursorY = topY - SECTION_HEADER_HEIGHT - TABLE_HEADER_HEIGHT;
        float bottomLimit = lastPage ? FOOTER_TOP_Y + 2f : MARGIN_BOTTOM + 16f;
        int currentIndex = startIndex;

        while (currentIndex < rows.size()) {
            PreparedItemRow row = rows.get(currentIndex);
            if (cursorY - row.height() < bottomLimit) {
                break;
            }
            drawTableRow(stream, cursorY, row, currentIndex + 1);
            cursorY -= row.height();
            currentIndex++;
        }

        float fillerHeight = cursorY - bottomLimit;
        if (fillerHeight > 0f) {
            drawTableFiller(stream, cursorY, fillerHeight);
        }

        return new TableResult(currentIndex);
    }

    private void drawTableHeader(PDPageContentStream stream, float topY) throws IOException {
        float bottomY = topY - TABLE_HEADER_HEIGHT;
        fillRect(stream, MARGIN_LEFT, bottomY, CONTENT_WIDTH, TABLE_HEADER_HEIGHT, 247);
        drawRect(stream, MARGIN_LEFT, bottomY, CONTENT_WIDTH, TABLE_HEADER_HEIGHT, 0.30f, COLOR_LINE);

        float x1 = MARGIN_LEFT + COL_NO;
        float x2 = x1 + COL_CODE;
        float x3 = x2 + COL_DESC;
        float x4 = x3 + COL_QTY;

        drawVerticalLine(stream, x1, bottomY, topY, COLOR_LINE);
        drawVerticalLine(stream, x2, bottomY, topY, COLOR_LINE);
        drawVerticalLine(stream, x3, bottomY, topY, COLOR_LINE);
        drawVerticalLine(stream, x4, bottomY, topY, COLOR_LINE);

        writeCentered(stream, "N°", MARGIN_LEFT, topY - 11f, COL_NO, PDType1Font.HELVETICA_BOLD, FONT_SECTION, COLOR_TEXT);
        writeCentered(stream, "CÓDIGO", x1, topY - 11f, COL_CODE, PDType1Font.HELVETICA_BOLD, FONT_SECTION, COLOR_TEXT);
        writeCentered(stream, "DESCRIPCIÓN", x2, topY - 11f, COL_DESC, PDType1Font.HELVETICA_BOLD, FONT_SECTION, COLOR_TEXT);
        writeCentered(stream, "CANTIDAD", x3, topY - 11f, COL_QTY, PDType1Font.HELVETICA_BOLD, FONT_SECTION, COLOR_TEXT);

        writeCentered(stream, "UNIDAD", x4, topY - 7.0f, COL_UNIT, PDType1Font.HELVETICA_BOLD, FONT_SECTION - 0.4f, COLOR_TEXT);
        writeCentered(stream, "DESPACHO", x4, topY - 14.0f, COL_UNIT, PDType1Font.HELVETICA_BOLD, FONT_SECTION - 0.6f, COLOR_TEXT);
    }

    private void drawTableRow(PDPageContentStream stream,
                              float topY,
                              PreparedItemRow row,
                              int rowNumber) throws IOException {
        float bottomY = topY - row.height();
        drawRect(stream, MARGIN_LEFT, bottomY, CONTENT_WIDTH, row.height(), 0.28f, COLOR_LINE);

        float x1 = MARGIN_LEFT + COL_NO;
        float x2 = x1 + COL_CODE;
        float x3 = x2 + COL_DESC;
        float x4 = x3 + COL_QTY;

        drawVerticalLine(stream, x1, bottomY, topY, COLOR_LINE);
        drawVerticalLine(stream, x2, bottomY, topY, COLOR_LINE);
        drawVerticalLine(stream, x3, bottomY, topY, COLOR_LINE);
        drawVerticalLine(stream, x4, bottomY, topY, COLOR_LINE);

        writeCentered(stream, String.valueOf(rowNumber), MARGIN_LEFT, topY - 11f, COL_NO, PDType1Font.HELVETICA, FONT_VALUE, COLOR_TEXT);
        writeText(stream, row.code(), x1 + 5f, topY - 11f, PDType1Font.HELVETICA, FONT_CODE, COLOR_TEXT);
        writeCentered(stream, row.quantity(), x3, topY - 11f, COL_QTY, PDType1Font.HELVETICA, FONT_VALUE, COLOR_TEXT);
        writeCentered(stream, row.unit(), x4, topY - 11f, COL_UNIT, PDType1Font.HELVETICA, FONT_VALUE, COLOR_TEXT);

        float descY = topY - 11f;
        for (String line : row.descriptionLines()) {
            writeText(stream, line, x2 + 6f, descY, PDType1Font.HELVETICA, FONT_VALUE, COLOR_TEXT);
            descY -= 9.0f;
        }
    }

    private void drawTableFiller(PDPageContentStream stream, float topY, float fillerHeight) throws IOException {
        float bottomY = topY - fillerHeight;
        drawRect(stream, MARGIN_LEFT, bottomY, CONTENT_WIDTH, fillerHeight, 0.28f, COLOR_LINE);

        float x1 = MARGIN_LEFT + COL_NO;
        float x2 = x1 + COL_CODE;
        float x3 = x2 + COL_DESC;
        float x4 = x3 + COL_QTY;

        drawVerticalLine(stream, x1, bottomY, topY, COLOR_LINE);
        drawVerticalLine(stream, x2, bottomY, topY, COLOR_LINE);
        drawVerticalLine(stream, x3, bottomY, topY, COLOR_LINE);
        drawVerticalLine(stream, x4, bottomY, topY, COLOR_LINE);
    }

    private void drawFooter(PDDocument pdf,
                            PDPageContentStream stream,
                            GuideRemissionCompany company,
                            GuideRemissionDocument document) throws IOException {
        drawSummaryBlock(stream, document, FOOTER_TOP_Y);
        drawQrBlock(pdf, stream, company, document, FOOTER_TOP_Y - 40f);
        drawLegalText(stream);
    }

    private void drawSummaryBlock(PDPageContentStream stream,
                                  GuideRemissionDocument document,
                                  float topY) throws IOException {
        drawSectionHeader(stream, MARGIN_LEFT, topY, CONTENT_WIDTH, "RESUMEN");

        float bodyTop = topY - SECTION_HEADER_HEIGHT;
        float bodyHeight = 28f;
        float bodyBottom = bodyTop - bodyHeight;

        drawRect(stream, MARGIN_LEFT, bodyBottom, CONTENT_WIDTH, bodyHeight, 0.30f, COLOR_LINE);

        float col1 = 190f;
        float col2 = 136f;
        float col3 = CONTENT_WIDTH - col1 - col2;
        drawVerticalLine(stream, MARGIN_LEFT + col1, bodyBottom, bodyTop, COLOR_LINE);
        drawVerticalLine(stream, MARGIN_LEFT + col1 + col2, bodyBottom, bodyTop, COLOR_LINE);

        writeText(stream, "Peso Total Aprox. (KGM):", MARGIN_LEFT + 6f, bodyTop - 17f, PDType1Font.HELVETICA_BOLD, FONT_SMALL, COLOR_TEXT);
        writeText(stream, safeNumber(document.getTotalWeight()), MARGIN_LEFT + 110f, bodyTop - 17f, PDType1Font.HELVETICA, FONT_VALUE, COLOR_TEXT);

        writeText(stream, "Bultos:", MARGIN_LEFT + col1 + 6f, bodyTop - 17f, PDType1Font.HELVETICA_BOLD, FONT_SMALL, COLOR_TEXT);
        writeText(stream, firstNotBlank(document.getNumberOfPackages(), "-"), MARGIN_LEFT + col1 + 44f, bodyTop - 17f, PDType1Font.HELVETICA, FONT_VALUE, COLOR_TEXT);

        writeText(stream, "Modalidad de transporte:", MARGIN_LEFT + col1 + col2 + 6f, bodyTop - 17f, PDType1Font.HELVETICA_BOLD, FONT_SMALL, COLOR_TEXT);
        writeText(stream, transferMode(document.getTransferModeCode()), MARGIN_LEFT + col1 + col2 + 106f, bodyTop - 17f, PDType1Font.HELVETICA, FONT_VALUE, COLOR_TEXT);
    }

    private void drawQrBlock(PDDocument pdf,
                             PDPageContentStream stream,
                             GuideRemissionCompany company,
                             GuideRemissionDocument document,
                             float topY) throws IOException {
        float qrX = MARGIN_LEFT + 6f;
        float qrY = topY - QR_SIZE;

        PDImageXObject qr = createQrImage(pdf, buildQrPayload(company, document));
        if (qr != null) {
            drawImageCentered(stream, qr, qrX, qrY, QR_SIZE, QR_SIZE);
        }
        drawRect(stream, qrX, qrY, QR_SIZE, QR_SIZE, 0.30f, COLOR_LINE);

        float qrTextWidth = QR_SIZE + 20f;
        writeCentered(stream, "Escanea para validar la GRE", qrX - 10f, qrY - 12f, qrTextWidth, PDType1Font.HELVETICA_BOLD, FONT_SMALL, COLOR_TEXT);
        writeCentered(stream, "Representación impresa de la GRE", qrX - 10f, qrY - 21f, qrTextWidth, PDType1Font.HELVETICA, FONT_SMALL, COLOR_SECONDARY);

        float signX = qrX + QR_SIZE + 18f;
        float signWidth = PAGE_WIDTH - MARGIN_RIGHT - signX;
        float signHeight = 56f;
        float signY = topY - 54f;

        drawRect(stream, signX, signY, signWidth, signHeight, 0.32f, COLOR_LINE);
        drawLine(stream, signX + 18f, signY + 34f, signX + signWidth - 18f, signY + 34f, 0.30f, COLOR_SECONDARY);

        writeCentered(stream, "Conformidad del cliente", signX, signY + 24f, signWidth, PDType1Font.HELVETICA_BOLD, FONT_VALUE, COLOR_TEXT);
        writeText(stream, "Nombre:", signX + 18f, signY + 12f, PDType1Font.HELVETICA_BOLD, FONT_SMALL, COLOR_TEXT);
        writeText(stream, "DNI:", signX + 18f, signY + 3f, PDType1Font.HELVETICA_BOLD, FONT_SMALL, COLOR_TEXT);
    }

    private void drawLegalText(PDPageContentStream stream) throws IOException {
        writeCentered(stream,
                "LA MERCADERÍA VIAJA POR CUENTA Y RIESGO DEL COMPRADOR. NO ADMITIMOS RECLAMO POR ROBO O AVERÍA.",
                MARGIN_LEFT,
                MARGIN_BOTTOM + 22f,
                CONTENT_WIDTH,
                PDType1Font.HELVETICA,
                FONT_SMALL,
                COLOR_TEXT);
    }

    private void drawContinuationNotice(PDPageContentStream stream) throws IOException {
        writeText(stream,
                "Continúa en la siguiente página...",
                PAGE_WIDTH - MARGIN_RIGHT - 112f,
                MARGIN_BOTTOM + 14f,
                PDType1Font.HELVETICA_OBLIQUE,
                FONT_SMALL,
                COLOR_SECONDARY);
    }

    private List<PreparedItemRow> prepareItemRows(List<GuideRemissionDocumentItem> items) throws IOException {
        List<PreparedItemRow> rows = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return rows;
        }

        for (GuideRemissionDocumentItem item : items) {
            List<String> descriptionLines = wrapByWidth(
                    firstNotBlank(item.getDescription(), "-"),
                    PDType1Font.HELVETICA,
                    FONT_VALUE,
                    COL_DESC - 12f
            );
            if (descriptionLines.isEmpty()) {
                descriptionLines = List.of("-");
            }

            float rowHeight = Math.max(24f, (descriptionLines.size() * 9.0f) + 8f);
            rows.add(new PreparedItemRow(
                    firstNotBlank(item.getItemCode(), "-"),
                    descriptionLines,
                    firstNotBlank(safeNumber(item.getQuantity()), "-"),
                    firstNotBlank(item.getUnitCode(), "-"),
                    rowHeight
            ));
        }
        return rows;
    }

    private List<FieldRow> sanitizeRows(List<FieldRow> rows) {
        return rows;
    }

    private float measureFieldRows(List<FieldRow> rows, float totalWidth) throws IOException {
        float total = 22f;
        for (FieldRow row : sanitizeRows(rows)) {
            total += measureFieldRow(row, totalWidth);
        }
        return total;
    }

    private float measureFieldRow(FieldRow row, float totalWidth) throws IOException {
        int cols = row.cells().size();
        float gap = 10f;
        float cellWidth = cols == 1 ? totalWidth : (totalWidth - gap) / 2f;
        float max = 0f;

        for (FieldCell cell : row.cells()) {
            float labelWidth = resolveFieldValueOffset(cols, cellWidth, cell.label());
            float valueWidth = Math.max(20f, cellWidth - labelWidth);
            List<String> lines = wrapByWidth(
                    firstNotBlank(cell.value(), "-"),
                    PDType1Font.HELVETICA,
                    FONT_VALUE,
                    valueWidth
            );
            max = Math.max(max, Math.max(1, lines.size()) * 8.8f);
        }

        return max + 5f;
    }

    private void drawFieldRows(PDPageContentStream stream,
                               float x,
                               float y,
                               float totalWidth,
                               List<FieldRow> rows) throws IOException {
        float currentY = y;
        for (FieldRow row : sanitizeRows(rows)) {
            float rowHeight = measureFieldRow(row, totalWidth);
            drawFieldRow(stream, x, currentY, totalWidth, row);
            currentY -= rowHeight;
        }
    }

    private void drawFieldRow(PDPageContentStream stream,
                              float x,
                              float y,
                              float totalWidth,
                              FieldRow row) throws IOException {
        int cols = row.cells().size();
        float gap = 10f;
        float cellWidth = cols == 1 ? totalWidth : (totalWidth - gap) / 2f;
        float currentX = x;

        for (FieldCell cell : row.cells()) {
            float labelWidth = resolveFieldValueOffset(cols, cellWidth, cell.label());

            writeText(stream,
                    cell.label() + ":",
                    currentX,
                    y,
                    PDType1Font.HELVETICA_BOLD,
                    FONT_VALUE,
                    COLOR_TEXT);

            writeWrapped(stream,
                    firstNotBlank(cell.value(), "-"),
                    currentX + labelWidth,
                    y,
                    Math.max(20f, cellWidth - labelWidth),
                    PDType1Font.HELVETICA,
                    FONT_VALUE,
                    8.8f,
                    COLOR_TEXT);

            currentX += cellWidth + gap;
        }
    }

    private void drawSectionHeader(PDPageContentStream stream,
                                   float x,
                                   float topY,
                                   float width,
                                   String title) throws IOException {
        fillRect(stream, x, topY - SECTION_HEADER_HEIGHT, width, SECTION_HEADER_HEIGHT, COLOR_FILL);
        drawRect(stream, x, topY - SECTION_HEADER_HEIGHT, width, SECTION_HEADER_HEIGHT, 0.30f, COLOR_LINE);
        writeText(stream, title, x + 5f, topY - 9.4f, PDType1Font.HELVETICA_BOLD, FONT_SECTION, COLOR_TEXT);
    }

    private void appendPageNumbers(PDDocument pdf) throws IOException {
        int total = pdf.getNumberOfPages();
        for (int i = 0; i < total; i++) {
            try (PDPageContentStream stream = new PDPageContentStream(pdf, pdf.getPage(i), PDPageContentStream.AppendMode.APPEND, true)) {
                writeText(stream,
                        "Pág. " + (i + 1) + " de " + total,
                        PAGE_WIDTH - MARGIN_RIGHT - 40f,
                        MARGIN_BOTTOM - 4f,
                        PDType1Font.HELVETICA,
                        FONT_SMALL,
                        COLOR_SECONDARY);
            }
        }
    }

    private PDImageXObject createQrImage(PDDocument pdf, String content) {
        if (!notBlank(content)) {
            return null;
        }
        try {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            hints.put(EncodeHintType.MARGIN, 1);

            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, 300, 300, hints);
            BufferedImage image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < matrix.getWidth(); x++) {
                for (int y = 0; y < matrix.getHeight(); y++) {
                    image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return PDImageXObject.createFromByteArray(pdf, baos.toByteArray(), "guide-remission-qr");
        } catch (WriterException | IOException ex) {
            return null;
        }
    }

    private String buildQrPayload(GuideRemissionCompany company, GuideRemissionDocument document) {
        List<String> payload = new ArrayList<>();
        payload.add("RUC=" + safe(company.getRuc()));
        payload.add("DOC=09");
        payload.add("SERIE=" + safe(document.getSerie()));
        payload.add("NUMERO=" + safe(document.getNumero()));
        payload.add("FECHA_EMISION=" + formatDate(document.getIssueDate()));
        payload.add("FECHA_TRASLADO=" + formatDate(document.getTransferDate()));
        payload.add("DEST_DOC_TIPO=" + recipientDocTypeCode(document.getRecipientDocumentType()));
        payload.add("DEST_DOC_NUM=" + firstNotBlank(document.getRecipientDocumentNumber(), "-"));
        payload.add("MOTIVO=" + transferReason(document.getTransferReasonCode()));
        payload.add("MODALIDAD=" + transferMode(document.getTransferModeCode()));
        if (notBlank(document.getCdrHash())) {
            payload.add("HASH=" + safe(document.getCdrHash()));
        }
        return String.join("|", payload);
    }

    private PDImageXObject loadLogo(PDDocument pdf, GuideRemissionCompany company) {
        String ruc = safe(company.getRuc());
        String[] candidates = new String[] {
                "images/guide-remission/logo-" + ruc + ".png",
                "images/guide-remission/logo-" + ruc + ".jpg",
                "images/guide-remission/logo.png",
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
                // continuar con el siguiente candidato
            }
        }
        return null;
    }

    private ImageFit fitImage(PDImageXObject image, float maxWidth, float maxHeight) {
        float width = image.getWidth();
        float height = image.getHeight();
        if (width <= 0 || height <= 0) {
            return new ImageFit(maxWidth, maxHeight);
        }
        float scale = Math.min(maxWidth / width, maxHeight / height);
        return new ImageFit(width * scale, height * scale);
    }

    private void drawImageCentered(PDPageContentStream stream,
                                   PDImageXObject image,
                                   float x,
                                   float y,
                                   float width,
                                   float height) throws IOException {
        ImageFit fit = fitImage(image, width - 8f, height - 8f);
        float imgX = x + ((width - fit.width()) / 2f);
        float imgY = y + ((height - fit.height()) / 2f);
        stream.drawImage(image, imgX, imgY, fit.width(), fit.height());
    }

    private void fillRect(PDPageContentStream stream,
                          float x,
                          float y,
                          float width,
                          float height,
                          int gray) throws IOException {
        stream.saveGraphicsState();
        stream.setNonStrokingColor(gray);
        stream.addRect(x, y, width, height);
        stream.fill();
        stream.restoreGraphicsState();
    }

    private void drawRect(PDPageContentStream stream,
                          float x,
                          float y,
                          float width,
                          float height,
                          float lineWidth,
                          int gray) throws IOException {
        stream.saveGraphicsState();
        stream.setStrokingColor(gray);
        stream.setLineWidth(lineWidth);
        stream.addRect(x, y, width, height);
        stream.stroke();
        stream.restoreGraphicsState();
    }

    private void drawLine(PDPageContentStream stream,
                          float x1,
                          float y1,
                          float x2,
                          float y2,
                          float lineWidth,
                          int gray) throws IOException {
        stream.saveGraphicsState();
        stream.setStrokingColor(gray);
        stream.setLineWidth(lineWidth);
        stream.moveTo(x1, y1);
        stream.lineTo(x2, y2);
        stream.stroke();
        stream.restoreGraphicsState();
    }

    private void drawVerticalLine(PDPageContentStream stream,
                                  float x,
                                  float y1,
                                  float y2,
                                  int gray) throws IOException {
        drawLine(stream, x, y1, x, y2, 0.28f, gray);
    }

    private void writeText(PDPageContentStream stream,
                           String text,
                           float x,
                           float y,
                           PDFont font,
                           float fontSize,
                           int gray) throws IOException {
        stream.beginText();
        stream.setNonStrokingColor(gray);
        stream.setFont(font, fontSize);
        stream.newLineAtOffset(x, y);
        stream.showText(safe(text));
        stream.endText();
        stream.setNonStrokingColor(0);
    }

    private void writeCentered(PDPageContentStream stream,
                               String text,
                               float x,
                               float y,
                               float width,
                               PDFont font,
                               float fontSize,
                               int gray) throws IOException {
        float textWidth = textWidth(font, fontSize, text);
        float startX = x + Math.max(0f, (width - textWidth) / 2f);
        writeText(stream, text, startX, y, font, fontSize, gray);
    }

    private float writeWrapped(PDPageContentStream stream,
                               String text,
                               float x,
                               float y,
                               float width,
                               PDFont font,
                               float fontSize,
                               float lineHeight,
                               int gray) throws IOException {
        List<String> lines = wrapByWidth(text, font, fontSize, width);
        if (lines.isEmpty()) {
            lines = List.of("");
        }

        float currentY = y;
        for (String line : lines) {
            writeText(stream, line, x, currentY, font, fontSize, gray);
            currentY -= lineHeight;
        }
        return currentY;
    }

    private List<String> wrapByWidth(String text,
                                     PDFont font,
                                     float fontSize,
                                     float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        if (!notBlank(text)) {
            return lines;
        }

        String[] words = safe(text).split("\s+");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
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
                List<String> pieces = splitLongWord(word, font, fontSize, maxWidth);
                for (int i = 0; i < pieces.size() - 1; i++) {
                    lines.add(pieces.get(i));
                }
                current = new StringBuilder(pieces.isEmpty() ? word : pieces.get(pieces.size() - 1));
            }
        }

        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private List<String> splitLongWord(String word,
                                       PDFont font,
                                       float fontSize,
                                       float maxWidth) throws IOException {
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

    private float textWidth(PDFont font, float fontSize, String text) throws IOException {
        return font.getStringWidth(safe(text)) / 1000f * fontSize;
    }

    private float resolveFieldLabelWidth(int cols, float cellWidth) {
        if (cols == 1) {
            return Math.min(104f, Math.max(76f, cellWidth * 0.32f));
        }
        return Math.min(84f, Math.max(66f, cellWidth * 0.52f));
    }

    private String buildCompanyLocationLine(GuideRemissionCompany company) {
        List<String> parts = new ArrayList<>();
        if (notBlank(company.getUbigeo())) parts.add("Ubigeo: " + safe(company.getUbigeo()));
        if (notBlank(company.getDistrito())) parts.add("Distrito: " + safe(company.getDistrito()));
        if (notBlank(company.getProvincia())) parts.add("Provincia: " + safe(company.getProvincia()));
        if (notBlank(company.getDepartamento())) parts.add("Departamento: " + safe(company.getDepartamento()));
        return String.join(" ", parts);
    }

    private String buildLocation(String ubigeo, String address) {
        if (notBlank(ubigeo) && notBlank(address)) {
            return ubigeo + " - " + address;
        }
        return firstNotBlank(address, ubigeo, "-");
    }

    private String recipientDocLabel(Integer type) {
        return switch (recipientDocTypeCode(type)) {
            case "1" -> "DNI";
            case "4" -> "CE";
            case "6" -> "RUC";
            default -> "Documento";
        };
    }

    private String recipientDocTypeCode(Integer type) {
        if (type == null) {
            return "-";
        }
        return switch (type) {
            case 1 -> "1";
            case 4 -> "4";
            case 6 -> "6";
            default -> String.valueOf(type);
        };
    }

    private String resolveTransportDisplay(GuideRemissionDocument document) {
        if ("02".equals(safe(document.getTransferModeCode()))) {
            return firstNotBlank(document.getDriverFullName(), "TRANSPORTE PRIVADO");
        }
        return firstNotBlank(document.getTransporterName(), "-");
    }

    private String resolveTransportDocument(GuideRemissionDocument document) {
        if ("02".equals(safe(document.getTransferModeCode()))) {
            return firstNotBlank(document.getDriverDni(), document.getDriverLicense(), "-");
        }
        return firstNotBlank(document.getTransporterDocumentNumber(), document.getLegacyTransportEntityId(), "-");
    }

    private String transferReason(String code) {
        return switch (safe(code)) {
            case "01" -> "Venta";
            case "02" -> "Compra";
            case "04" -> "Traslado entre establecimientos";
            case "08" -> "Importación";
            case "09" -> "Exportación";
            default -> firstNotBlank(code, "-");
        };
    }

    private String transferMode(String code) {
        return switch (safe(code)) {
            case "01" -> "TRANSPORTE PÚBLICO";
            case "02" -> "TRANSPORTE PRIVADO";
            default -> firstNotBlank(code, "-");
        };
    }

    private String formatDate(LocalDate date) {
        return date == null ? "" : date.format(DATE_FORMATTER);
    }

    private String formatTime(LocalTime time) {
        return time == null ? "" : time.format(TIME_FORMATTER);
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
        if (value == null) {
            return "";
        }

        return value
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .trim();
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private record FieldCell(String label, String value) {
    }

    private record FieldRow(List<FieldCell> cells) {
    }

    private record PreparedItemRow(String code, List<String> descriptionLines, String quantity, String unit, float height) {
    }

    private record TableResult(int nextIndex) {
    }

    private record ImageFit(float width, float height) {
    }
}