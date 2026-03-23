package com.paulfernandosr.possystembackend.contracts.application;

import com.paulfernandosr.possystembackend.contracts.domain.exception.InvalidContractException;
import com.paulfernandosr.possystembackend.contracts.domain.model.ContractStatus;
import com.paulfernandosr.possystembackend.contracts.domain.port.input.PayContractInstallmentUseCase;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractAccountsReceivableLookupRepository;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractInstallmentRepository;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractRepository;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractInstallmentPaymentRequest;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractInstallmentPaymentResponse;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.RegisterAccountsReceivablePaymentUseCase;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.AccountsReceivablePaymentRequest;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.AccountsReceivablePaymentResponse;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class PayContractInstallmentService implements PayContractInstallmentUseCase {

    private final UserRepository userRepository;

    private final ContractRepository contractRepository;
    private final ContractInstallmentRepository contractInstallmentRepository;
    private final ContractAccountsReceivableLookupRepository arLookupRepository;

    private final RegisterAccountsReceivablePaymentUseCase registerAccountsReceivablePaymentUseCase;

    @Override
    @Transactional
    public ContractInstallmentPaymentResponse pay(Long contractId, int installmentNumber, ContractInstallmentPaymentRequest request, String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidContractException("Usuario inválido: " + username));

        if (contractId == null) throw new InvalidContractException("contractId es obligatorio.");
        if (installmentNumber <= 0) throw new InvalidContractException("installmentNumber debe ser >= 1.");
        if (request == null) throw new InvalidContractException("Request vacío.");
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidContractException("amount debe ser > 0.");
        }
        if (request.getMethod() == null) {
            throw new InvalidContractException("method es obligatorio.");
        }

        var contract = contractRepository.findById(contractId);
        if (contract == null) throw new InvalidContractException("Contrato no existe: " + contractId);

        if (contract.getStatus() != ContractStatus.VENDIDO) {
            throw new InvalidContractException("Para registrar pagos, el contrato debe estar VENDIDO (venta generada).");
        }
        if (contract.getSaleId() == null) {
            throw new InvalidContractException("Contrato no tiene saleId asociado.");
        }

        // obtener arId por saleId
        Long arId = arLookupRepository.findArIdBySaleId(contract.getSaleId());
        if (arId == null) {
            throw new InvalidContractException("No existe AccountsReceivable para saleId=" + contract.getSaleId());
        }

        // lock cuota
        var locked = contractInstallmentRepository.lockByContractIdAndNumber(contractId, installmentNumber);
        if (locked == null) throw new InvalidContractException("Cuota no existe. contractId=" + contractId + ", n=" + installmentNumber);

        if ("ANULADO".equalsIgnoreCase(nzs(locked.getStatus()))) {
            throw new InvalidContractException("Cuota está ANULADO.");
        }
        if ("PAGADO".equalsIgnoreCase(nzs(locked.getStatus()))) {
            throw new InvalidContractException("Cuota ya está PAGADO.");
        }

        BigDecimal installmentAmount = nz(locked.getAmount()).setScale(4, RoundingMode.HALF_UP);
        BigDecimal alreadyPaid = nz(locked.getPaidAmount()).setScale(4, RoundingMode.HALF_UP);

        BigDecimal remaining = installmentAmount.subtract(alreadyPaid).setScale(4, RoundingMode.HALF_UP);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidContractException("Cuota ya está completamente pagada.");
        }

        BigDecimal pay = request.getAmount().setScale(4, RoundingMode.HALF_UP);

        // ✅ PAGO EXACTO (la opción que elegiste)
        if (pay.compareTo(remaining) != 0) {
            throw new InvalidContractException("Pago debe ser EXACTO a la cuota pendiente. Pendiente=" + remaining + ", recibido=" + pay);
        }

        // registrar pago en CxC (actualiza deuda real del cliente)
        AccountsReceivablePaymentRequest arReq = AccountsReceivablePaymentRequest.builder()
                .amount(pay)
                .method(request.getMethod())
                .paidAt(request.getPaidAt())
                .note(appendNote(request.getNote(), contractId, installmentNumber))
                .build();

        AccountsReceivablePaymentResponse arResp = registerAccountsReceivablePaymentUseCase.register(arId, arReq, user.getUsername());

        // marcar cuota como pagada
        BigDecimal newPaid = alreadyPaid.add(pay).setScale(4, RoundingMode.HALF_UP);
        contractInstallmentRepository.updatePaidAmountAndStatus(
                contractId,
                installmentNumber,
                newPaid,
                "PAGADO",
                java.time.LocalDateTime.now(),
                user.getId(),
                username
        );

        return ContractInstallmentPaymentResponse.builder()
                .contractId(contractId)
                .installmentNumber(installmentNumber)
                .installmentStatus("PAGADO")
                .installmentPaidAmount(newPaid)
                .saleId(contract.getSaleId())
                .arId(arId)
                .receivable(arResp)
                .build();
    }

    private static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private static String nzs(String s) { return s == null ? "" : s; }

    private static String appendNote(String note, Long contractId, int n) {
        String extra = "PAGO CUOTA " + n + " - CONTRATO #" + contractId;
        if (note == null || note.isBlank()) return extra;
        if (note.contains(extra)) return note;
        return note + " | " + extra;
    }
}
