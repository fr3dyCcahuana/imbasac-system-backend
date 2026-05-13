package com.paulfernandosr.possystembackend.contracts.application;

import com.paulfernandosr.possystembackend.contracts.domain.exception.InvalidContractException;
import com.paulfernandosr.possystembackend.contracts.domain.model.ContractStatus;
import com.paulfernandosr.possystembackend.contracts.domain.port.input.ResolveContractRepossessionUseCase;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractInstallmentRepository;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractRepository;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractSerialUnitRepository;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractRepossessionRequest;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractVoidResponse;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResolveContractRepossessionService implements ResolveContractRepossessionUseCase {

    private final UserRepository userRepository;
    private final ContractRepository contractRepository;
    private final ContractInstallmentRepository contractInstallmentRepository;
    private final ContractSerialUnitRepository contractSerialUnitRepository;

    @Override
    @Transactional
    public ContractVoidResponse resolve(Long contractId, ContractRepossessionRequest request, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidContractException("Usuario inválido: " + username));

        if (request == null || request.getReason() == null || request.getReason().trim().length() < 5) {
            throw new InvalidContractException("reason es obligatorio y debe tener al menos 5 caracteres.");
        }
        if (request.getPaidAmountTreatment() == null || request.getPaidAmountTreatment().trim().isEmpty()) {
            throw new InvalidContractException("paidAmountTreatment es obligatorio: PENALIDAD / DEVOLUCION / TRANSFERENCIA / OTRO.");
        }

        var contract = contractRepository.lockById(contractId);
        if (contract == null) throw new InvalidContractException("Contrato no existe: " + contractId);

        if (contract.getStatus() != ContractStatus.CREDITO_ACTIVO) {
            throw new InvalidContractException("Solo se puede decomisar un contrato CREDITO_ACTIVO.");
        }
        if (contract.getSaleId() != null) {
            throw new InvalidContractException("No se puede decomisar un contrato que ya tiene venta/comprobante asociado.");
        }

        String previous = contract.getStatus().name();

        contractInstallmentRepository.cancelPendingInstallments(contractId);
        contractSerialUnitRepository.markRecoveredForReview(contractId);

        String notes = appendRepossessionNote(contract.getNotes(), request, user.getUsername());
        contractRepository.updateStatus(contractId, ContractStatus.RESUELTO_DECOMISO, notes);

        return ContractVoidResponse.builder()
                .contractId(contractId)
                .previousStatus(previous)
                .status(ContractStatus.RESUELTO_DECOMISO.name())
                .message("Contrato resuelto por decomiso. La moto quedó en RECUPERADO_REVISION.")
                .build();
    }

    private String appendRepossessionNote(String notes, ContractRepossessionRequest req, String username) {
        String line = "RESUELTO POR DECOMISO: " + req.getReason().trim()
                + " | tratamiento=" + req.getPaidAmountTreatment().trim().toUpperCase()
                + (req.getNote() == null || req.getNote().isBlank() ? "" : " | nota=" + req.getNote().trim())
                + " | usuario=" + username;
        if (notes == null || notes.isBlank()) return line;
        return notes + "\n" + line;
    }
}
