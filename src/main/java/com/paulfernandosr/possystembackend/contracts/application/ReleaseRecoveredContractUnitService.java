package com.paulfernandosr.possystembackend.contracts.application;

import com.paulfernandosr.possystembackend.contracts.domain.exception.InvalidContractException;
import com.paulfernandosr.possystembackend.contracts.domain.model.ContractStatus;
import com.paulfernandosr.possystembackend.contracts.domain.port.input.ReleaseRecoveredContractUnitUseCase;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractRepository;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractSerialUnitRepository;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractVoidResponse;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReleaseRecoveredContractUnitService implements ReleaseRecoveredContractUnitUseCase {

    private final UserRepository userRepository;
    private final ContractRepository contractRepository;
    private final ContractSerialUnitRepository contractSerialUnitRepository;

    @Override
    @Transactional
    public ContractVoidResponse release(Long contractId, String reason, String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidContractException("Usuario inválido: " + username));

        var contract = contractRepository.lockById(contractId);
        if (contract == null) throw new InvalidContractException("Contrato no existe: " + contractId);

        if (contract.getStatus() != ContractStatus.RESUELTO_DECOMISO) {
            throw new InvalidContractException("Solo se puede liberar una moto de un contrato RESUELTO_DECOMISO.");
        }
        if (contract.getSaleId() != null) {
            throw new InvalidContractException("No se puede liberar una moto de un contrato con venta asociada.");
        }

        contractSerialUnitRepository.releaseRecoveredToStock(contractId);

        String notes = contract.getNotes();
        String line = "MOTO LIBERADA A EN_ALMACEN DESDE RECUPERADO_REVISION"
                + (reason == null || reason.isBlank() ? "" : ": " + reason.trim())
                + " | usuario=" + user.getUsername();
        notes = (notes == null || notes.isBlank()) ? line : notes + "\n" + line;
        contractRepository.updateStatus(contractId, ContractStatus.RESUELTO_DECOMISO, notes);

        return ContractVoidResponse.builder()
                .contractId(contractId)
                .previousStatus(ContractStatus.RESUELTO_DECOMISO.name())
                .status(ContractStatus.RESUELTO_DECOMISO.name())
                .message("Moto liberada a EN_ALMACEN y disponible para un nuevo contrato/venta.")
                .build();
    }
}
