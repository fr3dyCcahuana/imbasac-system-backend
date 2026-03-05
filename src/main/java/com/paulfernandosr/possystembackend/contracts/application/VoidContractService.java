package com.paulfernandosr.possystembackend.contracts.application;

import com.paulfernandosr.possystembackend.contracts.domain.exception.InvalidContractException;
import com.paulfernandosr.possystembackend.contracts.domain.model.ContractStatus;
import com.paulfernandosr.possystembackend.contracts.domain.port.input.VoidContractUseCase;
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
public class VoidContractService implements VoidContractUseCase {

    private final UserRepository userRepository;

    private final ContractRepository contractRepository;
    private final ContractSerialUnitRepository contractSerialUnitRepository;

    @Override
    @Transactional
    public ContractVoidResponse voidContract(Long contractId, String reason, String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidContractException("Usuario inválido: " + username));

        var contract = contractRepository.findById(contractId);
        if (contract == null) throw new InvalidContractException("Contrato no existe: " + contractId);

        if (contract.getStatus() == ContractStatus.VENDIDO) {
            throw new InvalidContractException("No se puede anular un contrato que ya generó venta.");
        }
        if (contract.getStatus() == ContractStatus.ANULADO) {
            return ContractVoidResponse.builder()
                    .contractId(contractId)
                    .previousStatus("ANULADO")
                    .status("ANULADO")
                    .message("Contrato ya estaba anulado.")
                    .build();
        }

        String prev = contract.getStatus().name();

        contractSerialUnitRepository.releaseFromContract(contractId);

        String notes = contract.getNotes();
        if (reason != null && !reason.isBlank()) {
            notes = (notes == null || notes.isBlank())
                    ? ("ANULADO: " + reason)
                    : (notes + "\nANULADO: " + reason);
        }

        contractRepository.updateStatus(contractId, ContractStatus.ANULADO, notes);

        return ContractVoidResponse.builder()
                .contractId(contractId)
                .previousStatus(prev)
                .status("ANULADO")
                .message("Contrato anulado y unidad liberada.")
                .build();
    }
}
