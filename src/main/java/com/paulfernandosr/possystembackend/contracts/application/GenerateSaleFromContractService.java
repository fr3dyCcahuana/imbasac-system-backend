package com.paulfernandosr.possystembackend.contracts.application;

import com.paulfernandosr.possystembackend.contracts.domain.exception.InvalidContractException;
import com.paulfernandosr.possystembackend.contracts.domain.model.ContractStatus;
import com.paulfernandosr.possystembackend.contracts.domain.port.input.GenerateSaleFromContractUseCase;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.*;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.*;
import com.paulfernandosr.possystembackend.salev2.domain.model.*;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.CreateSaleV2UseCase;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2CreateRequest;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2DocumentResponse;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GenerateSaleFromContractService implements GenerateSaleFromContractUseCase {

    private final UserRepository userRepository;

    private final ContractRepository contractRepository;
    private final ContractItemRepository contractItemRepository;
    private final ContractInstallmentRepository contractInstallmentRepository;
    private final ContractSerialUnitRepository contractSerialUnitRepository;
    private final SaleContractLinkRepository saleContractLinkRepository;

    private final CreateSaleV2UseCase createSaleV2UseCase;

    @Override
    @Transactional
    public ContractGenerateSaleResponse generateSale(Long contractId, ContractGenerateSaleRequest req, String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidContractException("Usuario inválido: " + username));

        var contract = contractRepository.findById(contractId);
        if (contract == null) throw new InvalidContractException("Contrato no existe: " + contractId);

        if (contract.getStatus() != ContractStatus.CONFIRMADO) {
            throw new InvalidContractException("Contrato debe estar CONFIRMADO para generar venta.");
        }
        if (contract.getSaleId() != null) {
            throw new InvalidContractException("Contrato ya tiene venta asociada: saleId=" + contract.getSaleId());
        }

        var item = contractItemRepository.findByContractId(contractId);
        if (item == null) throw new InvalidContractException("Contrato no tiene item.");

        contractSerialUnitRepository.assertStillReserved(contractId, item.getSerialUnitId());

        if (req == null) req = ContractGenerateSaleRequest.builder().build();

        DocType docType = req.getDocType() != null ? req.getDocType() : DocType.SIMPLE;
        String series = req.getSeries() != null ? req.getSeries() : "S001";
        LocalDate issueDate = req.getIssueDate() != null ? req.getIssueDate() : (contract.getIssueDate() != null ? contract.getIssueDate() : LocalDate.now());

        TaxStatus taxStatus = req.getTaxStatus() != null ? req.getTaxStatus() : TaxStatus.NO_GRAVADA;

        boolean useTotal = Boolean.TRUE.equals(req.getUseTotalAmountAsUnitPrice());

        var priceOverride = contract.getCashPrice();
        if (contract.getPaymentType() == PaymentType.CREDITO && useTotal) {
            priceOverride = contract.getTotalAmount();
        }

        SaleV2CreateRequest saleReq = SaleV2CreateRequest.builder()
                .stationId(contract.getStationId())
                .docType(docType)
                .series(series)
                .issueDate(issueDate)
                .currency(contract.getCurrency())
                .exchangeRate(contract.getExchangeRate())
                .priceList(contract.getPriceList())
                .customerId(contract.getCustomerId())
                .customerDocType(contract.getCustomerDocType())
                .customerDocNumber(contract.getCustomerDocNumber())
                .customerName(contract.getCustomerName())
                .customerAddress(contract.getCustomerAddress())
                .taxStatus(taxStatus)
                .igvIncluded(req.getIgvIncluded())
                .igvRate(req.getIgvRate())
                .paymentType(contract.getPaymentType())
                .notes(buildNotes(contractId, contract.getNotes()))
                .items(List.of(SaleV2CreateRequest.Item.builder()
                        .productId(item.getProductId())
                        .quantity(java.math.BigDecimal.ONE)
                        .discountPercent(java.math.BigDecimal.ZERO)
                        .lineKind(LineKind.VENDIDO)
                        .unitPriceOverride(priceOverride)
                        .serialUnitIds(List.of(item.getSerialUnitId()))
                        .build()))
                .build();

        if (contract.getPaymentType() == PaymentType.CONTADO) {
            if (req.getPaymentMethod() == null) {
                throw new InvalidContractException("paymentMethod es obligatorio para CONTADO.");
            }
            saleReq.setPayment(SaleV2CreateRequest.Payment.builder().method(req.getPaymentMethod()).build());
        } else {
            LocalDate lastDue = contractInstallmentRepository.findLastDueDate(contractId);
            saleReq.setDueDate(lastDue);
            if (lastDue != null) {
                saleReq.setCreditDays((int) ChronoUnit.DAYS.between(issueDate, lastDue));
            }
        }

        SaleV2DocumentResponse document = createSaleV2UseCase.create(saleReq, username);

        saleContractLinkRepository.linkSaleToContract(document.getSaleId(), contractId);

        contractRepository.updateStatusAndSale(contractId, ContractStatus.VENDIDO, document.getSaleId(), contract.getNotes());

        return ContractGenerateSaleResponse.builder()
                .contractId(contractId)
                .saleId(document.getSaleId())
                .saleDocType(docType.name())
                .saleSeries(document.getSeries())
                .saleNumber(document.getNumber())
                .issueDate(document.getIssueDate())
                .build();
    }

    private String buildNotes(Long contractId, String existing) {
        String extra = "VENTA DESDE CONTRATO #" + contractId;
        if (existing == null || existing.isBlank()) return extra;
        if (existing.contains(extra)) return existing;
        return existing + "\n" + extra;
    }
}
