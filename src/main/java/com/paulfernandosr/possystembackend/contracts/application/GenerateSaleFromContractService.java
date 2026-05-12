package com.paulfernandosr.possystembackend.contracts.application;

import com.paulfernandosr.possystembackend.contracts.domain.exception.InvalidContractException;
import com.paulfernandosr.possystembackend.contracts.domain.model.ContractStatus;
import com.paulfernandosr.possystembackend.contracts.domain.port.input.GenerateSaleFromContractUseCase;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.AccountsReceivableOverrideRepository;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractInstallmentRepository;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractItemRepository;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractRepository;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractSerialUnitRepository;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.SaleContractLinkRepository;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractGenerateSaleRequest;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractGenerateSaleResponse;
import com.paulfernandosr.possystembackend.salev2.domain.model.DocType;
import com.paulfernandosr.possystembackend.salev2.domain.model.LineKind;
import com.paulfernandosr.possystembackend.salev2.domain.model.PaymentType;
import com.paulfernandosr.possystembackend.salev2.domain.model.TaxStatus;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.CreateSaleV2UseCase;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2CreateRequest;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2DocumentResponse;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final AccountsReceivableOverrideRepository accountsReceivableOverrideRepository;

    private final CreateSaleV2UseCase createSaleV2UseCase;

    @Override
    @Transactional
    public ContractGenerateSaleResponse generateSale(Long contractId,
                                                     ContractGenerateSaleRequest req,
                                                     String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidContractException("Usuario inválido: " + username));

        var contract = contractRepository.findById(contractId);
        if (contract == null) {
            throw new InvalidContractException("Contrato no existe: " + contractId);
        }

        if (contract.getStatus() != ContractStatus.CONFIRMADO) {
            throw new InvalidContractException("Contrato debe estar CONFIRMADO para generar venta interna.");
        }

        if (contract.getSaleId() != null) {
            throw new InvalidContractException("Contrato ya tiene venta asociada: saleId=" + contract.getSaleId());
        }

        var item = contractItemRepository.findByContractId(contractId);
        if (item == null) {
            throw new InvalidContractException("Contrato no tiene item.");
        }

        contractSerialUnitRepository.assertStillReserved(contractId, item.getSerialUnitId());

        if (req == null) {
            req = ContractGenerateSaleRequest.builder().build();
        }

        Long stationIdForSale = contract.getStationId();

        LocalDate issueDate = req.getIssueDate() != null
                ? req.getIssueDate()
                : (contract.getIssueDate() != null ? contract.getIssueDate() : LocalDate.now());

        /*
         * Se crea BOLETA/FACTURA en ventas, pero NO se emite a SUNAT aquí.
         * La emisión sigue separada en contract-sunat-draft.
         */
        DocType docType = req.getDocType() != null
                ? req.getDocType()
                : defaultDocType(contract.getCustomerDocType());

        if (docType != DocType.BOLETA && docType != DocType.FACTURA) {
            throw new InvalidContractException("Solo BOLETA o FACTURA son válidos para generar venta desde contrato.");
        }

        String series = docType == DocType.FACTURA ? "F004" : "B004";

        TaxStatus taxStatus = req.getTaxStatus() != null ? req.getTaxStatus() : TaxStatus.NO_GRAVADA;
        boolean igvIncluded = taxStatus == TaxStatus.GRAVADA && Boolean.TRUE.equals(req.getIgvIncluded());

        /*
         * Precio original del contrato para la venta.
         * El precio tributario editable va en sale_contract_sunat_draft.
         */
        BigDecimal unitPriceForContractSale = contract.getCashPrice() != null
                ? contract.getCashPrice()
                : item.getUnitPrice();

        SaleV2CreateRequest saleReq = SaleV2CreateRequest.builder()
                .stationId(stationIdForSale)
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
                .taxReason(taxStatus == TaxStatus.NO_GRAVADA ? "20" : null)
                .igvIncluded(igvIncluded)
                .igvRate(new BigDecimal("18.00"))

                .paymentType(contract.getPaymentType())
                .notes(buildNotes(contractId, contract.getNotes()))

                .items(List.of(SaleV2CreateRequest.Item.builder()
                        .productId(item.getProductId())
                        .quantity(BigDecimal.ONE)
                        .discountPercent(BigDecimal.ZERO)
                        .lineKind(LineKind.VENDIDO)
                        .unitPriceOverride(unitPriceForContractSale)
                        .serialUnitIds(List.of(item.getSerialUnitId()))
                        .build()))
                .build();

        if (contract.getPaymentType() == PaymentType.CONTADO) {
            if (req.getPaymentMethod() == null) {
                throw new InvalidContractException("paymentMethod es obligatorio para contratos CONTADO.");
            }

            saleReq.setPayment(SaleV2CreateRequest.Payment.builder()
                    .method(req.getPaymentMethod())
                    .build());
        } else {
            LocalDate lastDue = contractInstallmentRepository.findLastDueDate(contractId);
            saleReq.setDueDate(lastDue);

            if (lastDue != null) {
                saleReq.setCreditDays((int) ChronoUnit.DAYS.between(issueDate, lastDue));
            }
        }

        SaleV2DocumentResponse document = createSaleV2UseCase.create(saleReq, username);

        saleContractLinkRepository.linkSaleToContract(document.getSaleId(), contractId);

        contractRepository.updateStatusAndSale(
                contractId,
                ContractStatus.VENDIDO,
                document.getSaleId(),
                contract.getNotes()
        );

        /*
         * Para crédito:
         * La venta queda con cashPrice, pero la deuda real del contrato
         * debe cuadrar con totalAmount.
         */
        if (contract.getPaymentType() == PaymentType.CREDITO) {
            Long arId = accountsReceivableOverrideRepository.findArIdBySaleId(document.getSaleId());

            if (arId != null) {
                LocalDate lastDue = contractInstallmentRepository.findLastDueDate(contractId);

                accountsReceivableOverrideRepository.overrideTotals(
                        arId,
                        contract.getTotalAmount(),
                        lastDue
                );

                if (contract.getCustomerId() != null) {
                    accountsReceivableOverrideRepository.recalculateCustomerAccount(contract.getCustomerId());
                }
            }
        }

        return ContractGenerateSaleResponse.builder()
                .contractId(contractId)
                .saleId(document.getSaleId())
                .saleDocType(docType.name())
                .saleSeries(document.getSeries())
                .saleNumber(document.getNumber())
                .issueDate(document.getIssueDate())
                .build();
    }

    private DocType defaultDocType(String customerDocType) {
        return "RUC".equalsIgnoreCase(String.valueOf(customerDocType))
                ? DocType.FACTURA
                : DocType.BOLETA;
    }

    private String buildNotes(Long contractId, String existing) {
        String extra = "VENTA DESDE CONTRATO SIN EMISION SUNAT #" + contractId;

        if (existing == null || existing.isBlank()) {
            return extra;
        }

        if (existing.contains(extra)) {
            return existing;
        }

        return existing + "\n" + extra;
    }
}
