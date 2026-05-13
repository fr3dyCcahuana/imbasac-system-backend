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

        if (contract.getStatus() == ContractStatus.FACTURADO) {
            throw new InvalidContractException("No se puede anular un contrato FACTURADO.");
        }
        if (contract.getStatus() == ContractStatus.CREDITO_ACTIVO) {
            throw new InvalidContractException("No se puede anular directamente un crédito activo. Use resolver por decomiso si corresponde.");
        }
        if (contract.getStatus() == ContractStatus.PAGADO_PENDIENTE_SUNAT) {
            throw new InvalidContractException("No se puede anular directamente un crédito pagado. Revise el proceso SUNAT o regularización administrativa.");
        }
        if (contract.getStatus() == ContractStatus.RESUELTO_DECOMISO) {
            return ContractVoidResponse.builder()
                    .contractId(contractId)
                    .previousStatus("RESUELTO_DECOMISO")
                    .status("RESUELTO_DECOMISO")
                    .message("Contrato ya fue resuelto por decomiso.")
                    .build();
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
                    ? ("ANULADO: " + reason + " | usuario=" + user.getUsername())
                    : (notes + "\nANULADO: " + reason + " | usuario=" + user.getUsername());
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
