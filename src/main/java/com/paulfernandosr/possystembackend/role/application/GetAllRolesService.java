package com.paulfernandosr.possystembackend.role.application;

import com.paulfernandosr.possystembackend.role.domain.port.input.GetAllRolesUseCase;
import com.paulfernandosr.possystembackend.role.domain.port.output.RoleRepository;
import com.paulfernandosr.possystembackend.role.domain.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class GetAllRolesService implements GetAllRolesUseCase {
    private final RoleRepository roleRepository;

    @Override
    public Collection<Role> getAllRoles() {
        return roleRepository.findAll();
    }
}
