package com.paulfernandosr.possystembackend.contracts.application;

import com.paulfernandosr.possystembackend.contracts.domain.exception.InvalidContractException;
import com.paulfernandosr.possystembackend.contracts.domain.model.ContractStatus;
import com.paulfernandosr.possystembackend.contracts.domain.port.input.ConfirmContractUseCase;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractItemRepository;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractRepository;
import com.paulfernandosr.possystembackend.contracts.domain.port.output.ContractSerialUnitRepository;
import com.paulfernandosr.possystembackend.contracts.infrastructure.adapter.input.dto.ContractDetailResponse;
import com.paulfernandosr.possystembackend.user.domain.User;
import com.paulfernandosr.possystembackend.user.domain.port.output.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConfirmContractService implements ConfirmContractUseCase {

    private final UserRepository userRepository;

    private final ContractRepository contractRepository;
    private final ContractItemRepository contractItemRepository;
    private final ContractSerialUnitRepository contractSerialUnitRepository;

    private final GetContractService getContractService;

    @Override
    @Transactional
    public ContractDetailResponse confirm(Long contractId, String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new InvalidContractException("Usuario inválido: " + username));

        var contract = contractRepository.findById(contractId);
        if (contract == null) throw new InvalidContractException("Contrato no existe: " + contractId);

        if (contract.getStatus() == ContractStatus.ANULADO) throw new InvalidContractException("Contrato ya está ANULADO.");
        if (contract.getStatus() == ContractStatus.VENDIDO) throw new InvalidContractException("Contrato ya está VENDIDO.");
        if (contract.getStatus() != ContractStatus.PENDIENTE) throw new InvalidContractException("Contrato no está PENDIENTE.");

        var item = contractItemRepository.findByContractId(contractId);
        if (item == null) throw new InvalidContractException("Contrato no tiene item.");

        contractSerialUnitRepository.assertStillReserved(contractId, item.getSerialUnitId());

        contractRepository.updateStatus(contractId, ContractStatus.CONFIRMADO, contract.getNotes());

        return getContractService.getById(contractId);
    }
}
