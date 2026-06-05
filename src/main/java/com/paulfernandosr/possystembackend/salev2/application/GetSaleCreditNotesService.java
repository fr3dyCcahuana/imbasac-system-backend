package com.paulfernandosr.possystembackend.salev2.application;

import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidSaleV2Exception;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.GetSaleCreditNotesUseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.SaleCreditNoteRepository;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.CreditNoteItemResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.CreditNoteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetSaleCreditNotesService implements GetSaleCreditNotesUseCase {

    private static final String STATUS_ACCEPTED = "ACEPTADO";
    private static final String STATUS_REJECTED = "RECHAZADO";
    private static final String STATUS_COMMUNICATION_ERROR = "ERROR_COMUNICACION";

    private final SaleCreditNoteRepository creditNoteRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CreditNoteResponse> getBySaleId(Long saleId) {
        if (saleId == null) {
            throw new InvalidSaleV2Exception("saleId es obligatorio.");
        }

        return creditNoteRepository.findCreditNotesBySaleId(saleId).stream()
                .map(this::toResponse)
                .toList();
    }

    private CreditNoteResponse toResponse(SaleCreditNoteRepository.CreditNoteView view) {
        String sunatStatus = view.getSunatStatus() == null ? "" : view.getSunatStatus().trim();
        boolean accepted = STATUS_ACCEPTED.equalsIgnoreCase(sunatStatus);
        boolean rejected = STATUS_REJECTED.equalsIgnoreCase(sunatStatus);
        boolean communicationError = STATUS_COMMUNICATION_ERROR.equalsIgnoreCase(sunatStatus);

        List<CreditNoteItemResponse> items = view.getItems().stream()
                .map(item -> CreditNoteItemResponse.builder()
                        .creditNoteItemId(item.getCreditNoteItemId())
                        .saleItemId(item.getSaleItemId())
                        .productId(item.getProductId())
                        .sku(item.getSku())
                        .description(item.getDescription())
                        .quantity(item.getQuantity())
                        .revenueTotal(item.getRevenueTotal())
                        .returnedToStock(item.getReturnedToStock())
                        .build())
                .toList();

        return CreditNoteResponse.builder()
                .creditNoteId(view.getCreditNoteId())
                .saleId(view.getSaleId())
                .docType(view.getDocType())
                .series(view.getSeries())
                .number(view.getNumber())
                .issueDate(view.getIssueDate())
                .creditNoteTypeCode(view.getCreditNoteTypeCode())
                .creditNoteTypeDescription(view.getCreditNoteTypeDescription())
                .reason(view.getReason())
                .returnedToStock(view.getReturnedToStock())
                .subtotal(view.getSubtotal())
                .igvAmount(view.getIgvAmount())
                .total(view.getTotal())
                .sunatStatus(view.getSunatStatus())
                .sunatCode(view.getSunatCode())
                .sunatDescription(view.getSunatDescription())
                .hashCode(view.getHashCode())
                .xmlPath(view.getXmlPath())
                .cdrPath(view.getCdrPath())
                .pdfPath(view.getPdfPath())
                .emittedAt(view.getEmittedAt())
                .accepted(accepted)
                .rejected(rejected)
                .communicationError(communicationError)
                .retryable(communicationError)
                .items(items)
                .build();
    }
}
