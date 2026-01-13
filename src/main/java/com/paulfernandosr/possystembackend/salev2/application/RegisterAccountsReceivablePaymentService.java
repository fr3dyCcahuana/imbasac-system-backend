package com.paulfernandosr.possystembackend.salev2.application;

import com.paulfernandosr.possystembackend.salev2.domain.exception.InvalidReceivableException;
import com.paulfernandosr.possystembackend.salev2.domain.model.PaymentMethod;
import com.paulfernandosr.possystembackend.salev2.domain.port.input.RegisterAccountsReceivablePaymentUseCase;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.AccountsReceivablePaymentRepository;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.AccountsReceivableRepository;
import com.paulfernandosr.possystembackend.salev2.domain.port.output.CustomerAccountRepository;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.AccountsReceivablePaymentRequest;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.AccountsReceivablePaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RegisterAccountsReceivablePaymentService implements RegisterAccountsReceivablePaymentUseCase {

    private final AccountsReceivableRepository accountsReceivableRepository;
    private final AccountsReceivablePaymentRepository accountsReceivablePaymentRepository;
    private final CustomerAccountRepository customerAccountRepository;

    @Override
    @Transactional
    public AccountsReceivablePaymentResponse register(Long arId, AccountsReceivablePaymentRequest request) {
        if (arId == null) throw new InvalidReceivableException("arId es obligatorio.");
        if (request == null) throw new InvalidReceivableException("Request vacío.");
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidReceivableException("amount debe ser > 0.");
        }

        PaymentMethod method = request.getMethod();
        if (method == null) throw new InvalidReceivableException("method es obligatorio.");

        AccountsReceivableRepository.LockedAr ar = accountsReceivableRepository.lockById(arId);
        if (ar == null) throw new InvalidReceivableException("Cuenta por cobrar no existe: " + arId);
        if ("PAGADO".equalsIgnoreCase(ar.getStatus())) {
            throw new InvalidReceivableException("La cuenta por cobrar ya está PAGADO.");
        }

        BigDecimal amount = request.getAmount().setScale(4, RoundingMode.HALF_UP);
        BigDecimal newPaid = nz(ar.getPaidAmount()).add(amount).setScale(4, RoundingMode.HALF_UP);
        BigDecimal total = nz(ar.getTotalAmount()).setScale(4, RoundingMode.HALF_UP);

        if (newPaid.compareTo(total) > 0) {
            throw new InvalidReceivableException("El pago excede el total. Total=" + total + ", nuevoPagado=" + newPaid);
        }

        BigDecimal newBalance = total.subtract(newPaid).setScale(4, RoundingMode.HALF_UP);

        String newStatus;
        LocalDate today = LocalDate.now();
        if (newBalance.compareTo(BigDecimal.ZERO) == 0) {
            newStatus = "PAGADO";
        } else {
            // Reglas acordadas: si está vencido, permanece VENCIDO mientras haya saldo
            newStatus = ar.getDueDate() != null && ar.getDueDate().isBefore(today) ? "VENCIDO" : "PENDIENTE";
        }

        LocalDateTime paidAt = request.getPaidAt() == null ? LocalDateTime.now() : request.getPaidAt();
        accountsReceivablePaymentRepository.insert(arId, amount, method.name(), paidAt, request.getNote());
        accountsReceivableRepository.updateAmountsAndStatus(arId, newPaid, newBalance, newStatus);

        // Customer account
        customerAccountRepository.ensureExists(ar.getCustomerId());
        customerAccountRepository.touchLastPaymentAt(ar.getCustomerId());
        customerAccountRepository.recalculate(ar.getCustomerId());

        return AccountsReceivablePaymentResponse.builder()
                .arId(arId)
                .paidAmount(newPaid)
                .balanceAmount(newBalance)
                .status(newStatus)
                .dueDate(ar.getDueDate())
                .build();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
