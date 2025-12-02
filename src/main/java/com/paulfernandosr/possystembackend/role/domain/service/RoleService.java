package com.paulfernandosr.possystembackend.role.domain.service;

import com.paulfernandosr.possystembackend.permission.domain.Permission;
import com.paulfernandosr.possystembackend.role.domain.port.output.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepository roleRepository;

    public Collection<Permission> getRolePermissions(Long roleId) {
        return roleRepository.findRolePermissionsByRoleId(roleId);
    }
}
