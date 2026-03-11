package com.paulfernandosr.possystembackend.contracts.application;

import com.paulfernandosr.possystembackend.contracts.domain.exception.InvalidContractException;
import com.paulfernandosr.possystembackend.contracts.domain.model.*;
import com.paulfernandosr.possystembackend.contracts.domain.port.input.CreateContractUseCase;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.*;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.*;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.output.row.ProductContractRow;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.output.row.SerialUnitContractRow;
import com.paulfernandosr.possystembackend.salev2.domain.model.LockedDocumentSeries;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.DocumentSeriesRepository;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CreateContractService implements CreateContractUseCase {

    private final UserRepository userRepository;

    private final DocumentSeriesRepository documentSeriesRepository;

    private final ContractRepository contractRepository;
    private final ContractItemRepository contractItemRepository;
    private final ContractInstallmentRepository contractInstallmentRepository;
    private final ContractGuarantorRepository contractGuarantorRepository;
    private final ContractCustomerProfileRepository contractCustomerProfileRepository;

    private final ContractProductRepository contractProductRepository;
    private final ContractSerialUnitRepository contractSerialUnitRepository;

    @Override
    @Transactional
    public ContractDocumentResponse create(ContractCreateRequest request, String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidContractException("Usuario inválido: " + username));

        validate(request);

        LocalDate issueDate = request.getIssueDate() != null ? request.getIssueDate() : LocalDate.now();

        LockedDocumentSeries locked = documentSeriesRepository.lockSeries(
                "CONTRACT",
                request.getSeries()
        );
        Long number = locked.getNextNumber();

        ProductContractRow product = contractProductRepository.findById(request.getProductId());
        if (product == null) {
            throw new InvalidContractException("Producto no encontrado: " + request.getProductId());
        }
        if (!"MOTOCICLETAS".equalsIgnoreCase(nzs(product.getCategory()))) {
            throw new InvalidContractException("Contrato solo aplica a categoría MOTOCICLETAS. category=" + product.getCategory());
        }
        if (Boolean.FALSE.equals(product.getManageBySerial())) {
            throw new InvalidContractException("MOTOCICLETAS debe ser manage_by_serial=true. productId=" + product.getId());
        }

        SerialUnitContractRow unit = contractSerialUnitRepository.lockById(request.getSerialUnitId());
        if (unit == null) throw new InvalidContractException("Unidad serial no existe: " + request.getSerialUnitId());
        if (!Objects.equals(unit.getProductId(), product.getId())) {
            throw new InvalidContractException("Unidad serial no pertenece al producto. serialUnitId=" + unit.getId());
        }
        if (unit.getVin() == null || unit.getVin().trim().isEmpty()) {
            throw new InvalidContractException("MOTOCICLETAS requiere VIN en la unidad. serialUnitId=" + unit.getId());
        }
        if (!"EN_ALMACEN".equalsIgnoreCase(nzs(unit.getStatus()))) {
            throw new InvalidContractException("Unidad no disponible (status=" + unit.getStatus() + "). serialUnitId=" + unit.getId());
        }
        if (unit.getContractId() != null) {
            throw new InvalidContractException("Unidad ya está reservada por contratoId=" + unit.getContractId());
        }

        Financials fin = computeFinancials(request);

        Contract contract = Contract.builder()
                .stationId(request.getStationId())
                .createdBy(user.getId())
                .series(request.getSeries())
                .number(number)
                .issueDate(issueDate)
                .currency(nzs(request.getCurrency(), "PEN"))
                .exchangeRate(request.getExchangeRate())
                .priceList(request.getPriceList())
                .customerId(request.getCustomerId())
                .customerDocType(request.getCustomerDocType())
                .customerDocNumber(request.getCustomerDocNumber())
                .customerName(request.getCustomerName())
                .customerAddress(request.getCustomerAddress())
                .paymentType(request.getPaymentType())
                .cashPrice(fin.cashPrice)
                .interestRateMonthly(fin.interestRateMonthly)
                .installments(fin.installments)
                .initialAmount(fin.initialAmount)
                .financedAmount(fin.financedAmount)
                .interestAmount(fin.interestAmount)
                .totalAmount(fin.totalAmount)
                .status(ContractStatus.PENDIENTE)
                .notes(request.getNotes())
                .build();

        Long contractId = contractRepository.insert(contract);

        ContractItem item = ContractItem.builder()
                .contractId(contractId)
                .productId(product.getId())
                .serialUnitId(unit.getId())
                .sku(product.getSku())
                .description(product.getName())
                .brand(product.getBrand())
                .model(product.getModel())
                .vin(unit.getVin())
                .unitPrice(fin.cashPrice)
                .build();

        contractItemRepository.insert(item);

        contractSerialUnitRepository.reserveForContract(unit.getId(), contractId);

        if (request.getPaymentType() != null && request.getPaymentType().name().equals("CREDITO")) {
            List<ContractInstallment> installments = buildInstallments(contractId, fin, request.getFirstDueDate(), issueDate);
            contractInstallmentRepository.insertBatch(contractId, installments);
        }

        if (request.getGuarantor() != null) {
            ContractGuarantorDto g = request.getGuarantor();
            contractGuarantorRepository.upsert(contractId, ContractGuarantor.builder()
                    .contractId(contractId)
                    .docType(g.getDocType())
                    .docNumber(g.getDocNumber())
                    .fullName(g.getFullName())
                    .address(g.getAddress())
                    .phone(g.getPhone())
                    .occupation(g.getOccupation())
                    .companyName(g.getCompanyName())
                    .monthlyIncome(g.getMonthlyIncome())
                    .build());
        }

        if (request.getCustomerProfile() != null) {
            ContractCustomerProfileDto p = request.getCustomerProfile();
            contractCustomerProfileRepository.upsert(contractId, ContractCustomerProfile.builder()
                    .contractId(contractId)
                    .maritalStatus(p.getMaritalStatus())
                    .nationality(p.getNationality())
                    .district(p.getDistrict())
                    .province(p.getProvince())
                    .housingType(p.getHousingType())
                    .rentAmount(p.getRentAmount())
                    .employerName(p.getEmployerName())
                    .employerAddress(p.getEmployerAddress())
                    .employmentTime(p.getEmploymentTime())
                    .netIncome(p.getNetIncome())
                    .spouseIncome(p.getSpouseIncome())
                    .otherIncome(p.getOtherIncome())
                    .totalIncome(p.getTotalIncome())
                    .customerReferences(p.getCustomerReferences())
                    .build());
        }

        documentSeriesRepository.incrementNextNumber(locked.getId());

        return ContractDocumentResponse.builder()
                .contractId(contractId)
                .series(request.getSeries())
                .number(number)
                .issueDate(issueDate)
                .status(contract.getStatus().name())
                .build();
    }

    private void validate(ContractCreateRequest r) {
        if (r == null) throw new InvalidContractException("Request vacío.");
        if (r.getStationId() == null) throw new InvalidContractException("stationId es obligatorio.");
        if (r.getSeries() == null || r.getSeries().trim().isEmpty()) throw new InvalidContractException("series es obligatorio.");
        if (r.getPriceList() == null) throw new InvalidContractException("priceList es obligatorio.");
        if (r.getPaymentType() == null) throw new InvalidContractException("paymentType es obligatorio.");
        if (r.getProductId() == null) throw new InvalidContractException("productId es obligatorio.");
        if (r.getSerialUnitId() == null) throw new InvalidContractException("serialUnitId es obligatorio.");
        if (r.getCashPrice() == null || r.getCashPrice().compareTo(BigDecimal.ZERO) <= 0)
            throw new InvalidContractException("cashPrice debe ser > 0.");
        if (r.getInstallments() == null) r.setInstallments(1);
        if (r.getInstallments() < 1 || r.getInstallments() > 6) throw new InvalidContractException("installments debe ser 1..6.");
        if (r.getInitialAmount() == null) r.setInitialAmount(BigDecimal.ZERO);
        if (r.getInitialAmount().compareTo(BigDecimal.ZERO) < 0) throw new InvalidContractException("initialAmount inválido.");
        if (r.getInterestRateMonthly() == null) r.setInterestRateMonthly(new BigDecimal("3.5"));
        if (r.getInterestRateMonthly().compareTo(BigDecimal.ZERO) < 0) throw new InvalidContractException("interestRateMonthly inválido.");
    }

    private static class Financials {
        BigDecimal cashPrice;
        BigDecimal interestRateMonthly;
        int installments;
        BigDecimal initialAmount;
        BigDecimal financedAmount;
        BigDecimal interestAmount;
        BigDecimal totalAmount;
    }

    private Financials computeFinancials(ContractCreateRequest r) {
        Financials f = new Financials();

        BigDecimal cash = r.getCashPrice().setScale(4, RoundingMode.HALF_UP);
        BigDecimal initial = r.getInitialAmount().setScale(4, RoundingMode.HALF_UP);

        if (initial.compareTo(cash) > 0) {
            throw new InvalidContractException("initialAmount no puede ser mayor a cashPrice.");
        }

        int n = r.getInstallments() == null ? 1 : r.getInstallments();

        BigDecimal rate = r.getInterestRateMonthly() == null ? new BigDecimal("3.5") : r.getInterestRateMonthly();
        rate = rate.setScale(3, RoundingMode.HALF_UP);

        if (r.getPaymentType().name().equals("CONTADO")) {
            n = 1;
            rate = BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
        }

        BigDecimal financed = cash.subtract(initial).max(BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);

        BigDecimal interest = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        if (r.getPaymentType().name().equals("CREDITO") && financed.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal rateFrac = rate.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP);
            interest = financed.multiply(rateFrac).multiply(new BigDecimal(n)).setScale(4, RoundingMode.HALF_UP);
        }

        BigDecimal total = financed.add(interest).setScale(4, RoundingMode.HALF_UP);
        if (r.getPaymentType().name().equals("CONTADO")) {
            total = cash;
            financed = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
            interest = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
            initial = cash;
        }

        f.cashPrice = cash;
        f.initialAmount = initial;
        f.installments = n;
        f.interestRateMonthly = rate;
        f.financedAmount = financed;
        f.interestAmount = interest;
        f.totalAmount = total;

        return f;
    }

    private List<ContractInstallment> buildInstallments(Long contractId, Financials fin, LocalDate firstDueDate, LocalDate issueDate) {

        int n = fin.installments;

        List<ContractInstallment> out = new ArrayList<>();
        if (n <= 0) return out;

        LocalDate first = (firstDueDate != null) ? firstDueDate : issueDate.plusDays(30);

        BigDecimal total = fin.totalAmount.setScale(4, RoundingMode.HALF_UP);

        BigDecimal base = total.divide(new BigDecimal(n), 4, RoundingMode.HALF_UP);

        BigDecimal acc = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        for (int i = 1; i <= n; i++) {
            BigDecimal amt = base;
            if (i == n) {
                amt = total.subtract(acc).setScale(4, RoundingMode.HALF_UP);
            } else {
                acc = acc.add(amt).setScale(4, RoundingMode.HALF_UP);
            }

            LocalDate due = first.plusMonths(i - 1L);

            out.add(ContractInstallment.builder()
                    .contractId(contractId)
                    .installmentNumber(i)
                    .dueDate(due)
                    .amount(amt)
                    .paidAmount(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP))
                    .status("PENDIENTE")
                    .build());
        }

        return out;
    }

    private static String nzs(String s) { return s == null ? "" : s; }
    private static String nzs(String s, String def) { return (s == null || s.isBlank()) ? def : s; }
}
