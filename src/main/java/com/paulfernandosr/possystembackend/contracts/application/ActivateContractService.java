package com.paulfernandosr.possystembackend.contracts.application;

import com.paulfernandosr.possystembackend.contracts.domain.exception.InvalidContractException;
import com.paulfernandosr.possystembackend.contracts.domain.model.ContractStatus;
import com.paulfernandosr.possystembackend.contracts.domain.port.input.ActivateContractUseCase;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractItemRepository;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractPaymentRepository;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractRepository;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractSerialUnitRepository;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractActivateRequest;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractDetailResponse;
import com.paulfernandosr.possystembackend.salev2.domain.model.PaymentType;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ActivateContractService implements ActivateContractUseCase {

    private final UserRepository userRepository;
    private final ContractRepository contractRepository;
    private final ContractItemRepository contractItemRepository;
    private final ContractSerialUnitRepository contractSerialUnitRepository;
    private final ContractPaymentRepository contractPaymentRepository;
    private final GetContractService getContractService;

    @Override
    @Transactional
    public ContractDetailResponse activate(Long contractId, ContractActivateRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidContractException("Usuario inválido: " + username));

        var contract = contractRepository.lockById(contractId);
        if (contract == null) throw new InvalidContractException("Contrato no existe: " + contractId);
        if (contract.getStatus() != ContractStatus.PENDIENTE && contract.getStatus() != ContractStatus.CONFIRMADO) {
            throw new InvalidContractException("Solo se puede activar un contrato PENDIENTE o CONFIRMADO.");
        }
        if (contract.getSaleId() != null) {
            throw new InvalidContractException("El contrato ya tiene venta asociada.");
        }

        var item = contractItemRepository.findByContractId(contractId);
        if (item == null) throw new InvalidContractException("Contrato no tiene item.");
        contractSerialUnitRepository.assertStillReserved(contractId, item.getSerialUnitId());

        if (contract.getPaymentType() == PaymentType.CREDITO) {
            BigDecimal initialAmount = contract.getInitialAmount() == null ? BigDecimal.ZERO : contract.getInitialAmount();
            if (initialAmount.compareTo(BigDecimal.ZERO) > 0) {
                if (request == null || request.getInitialPaymentMethod() == null) {
                    throw new InvalidContractException("initialPaymentMethod es obligatorio para registrar el pago inicial del crédito.");
                }
                contractPaymentRepository.insert(
                        contractId,
                        "INICIAL",
                        null,
                        initialAmount,
                        request.getInitialPaymentMethod().name(),
                        LocalDateTime.now(),
                        request.getNote(),
                        user.getId(),
                        user.getUsername()
                );
            }

            if (request != null && Boolean.TRUE.equals(request.getDeliveredToCustomer())) {
                contractSerialUnitRepository.markDeliveredOnCredit(contractId, item.getSerialUnitId());
            }

            contractRepository.updateStatus(contractId, ContractStatus.CREDITO_ACTIVO, appendNote(contract.getNotes(), "CREDITO ACTIVADO"));
        } else {
            contractRepository.updateStatus(contractId, ContractStatus.CONFIRMADO, contract.getNotes());
        }

        return getContractService.getById(contractId);
    }

    private String appendNote(String notes, String line) {
        if (notes == null || notes.isBlank()) return line;
        if (notes.contains(line)) return notes;
        return notes + "\n" + line;
    }
}
