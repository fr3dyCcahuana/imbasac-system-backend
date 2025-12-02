package com.paulfernandosr.possystembackend.role.application;

import com.paulfernandosr.possystembackend.role.domain.Role;
import com.paulfernandosr.possystembackend.role.domain.port.input.GetFullRoleInfoUseCase;
import com.paulfernandosr.possystembackend.role.domain.port.output.RoleRepository;
import com.paulfernandosr.possystembackend.role.domain.service.RoleService;
import com.paulfernandosr.possystembackend.user.domain.exception.RoleNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetFullRoleInfoService implements GetFullRoleInfoUseCase {
    private final RoleRepository roleRepository;
    private final RoleService roleService;

    @Override
    public Role getFullRoleInfoById(Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RoleNotFoundException("Role not found with identification: " + roleId));

        role.setPermissions(roleService.getRolePermissions(roleId));

        return role;
    }
}
