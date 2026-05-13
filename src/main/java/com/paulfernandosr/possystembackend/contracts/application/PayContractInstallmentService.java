package com.paulfernandosr.possystembackend.contracts.application;

import com.paulfernandosr.possystembackend.contracts.domain.exception.InvalidContractException;
import com.paulfernandosr.possystembackend.contracts.domain.model.ContractStatus;
import com.paulfernandosr.possystembackend.contracts.domain.port.input.PayContractInstallmentUseCase;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractInstallmentRepository;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractPaymentRepository;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractRepository;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractInstallmentPaymentRequest;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractInstallmentPaymentResponse;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PayContractInstallmentService implements PayContractInstallmentUseCase {

    private final UserRepository userRepository;
    private final ContractRepository contractRepository;
    private final ContractInstallmentRepository contractInstallmentRepository;
    private final ContractPaymentRepository contractPaymentRepository;

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

        var contract = contractRepository.lockById(contractId);
        if (contract == null) throw new InvalidContractException("Contrato no existe: " + contractId);

        if (contract.getStatus() != ContractStatus.CREDITO_ACTIVO) {
            throw new InvalidContractException("Para registrar cuotas, el contrato debe estar CREDITO_ACTIVO.");
        }
        if (contract.getSaleId() != null) {
            throw new InvalidContractException("El contrato ya tiene venta/comprobante asociado. No se pueden registrar cuotas.");
        }

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
        if (pay.compareTo(remaining) != 0) {
            throw new InvalidContractException("Pago debe ser EXACTO a la cuota pendiente. Pendiente=" + remaining + ", recibido=" + pay);
        }

        LocalDateTime paidAt = request.getPaidAt() != null ? request.getPaidAt() : LocalDateTime.now();

        Long paymentId = contractPaymentRepository.insert(
                contractId,
                "CUOTA",
                installmentNumber,
                pay,
                request.getMethod().name(),
                paidAt,
                appendNote(request.getNote(), contractId, installmentNumber),
                user.getId(),
                user.getUsername()
        );

        BigDecimal newPaid = alreadyPaid.add(pay).setScale(4, RoundingMode.HALF_UP);
        contractInstallmentRepository.updatePaidAmountAndStatus(
                contractId,
                installmentNumber,
                newPaid,
                "PAGADO",
                paidAt,
                user.getId(),
                username
        );

        boolean allPaid = contractInstallmentRepository.allInstallmentsPaid(contractId);
        if (allPaid) {
            contractRepository.updateStatus(contractId, ContractStatus.PAGADO_PENDIENTE_SUNAT, appendFinalNote(contract.getNotes()));
        }

        return ContractInstallmentPaymentResponse.builder()
                .contractId(contractId)
                .installmentNumber(installmentNumber)
                .installmentStatus("PAGADO")
                .installmentPaidAmount(newPaid)
                .saleId(null)
                .arId(paymentId)
                .receivable(null)
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

    private static String appendFinalNote(String notes) {
        String line = "CREDITO PAGADO COMPLETO - PENDIENTE DE EMISION SUNAT";
        if (notes == null || notes.isBlank()) return line;
        if (notes.contains(line)) return notes;
        return notes + "\n" + line;
    }
}
