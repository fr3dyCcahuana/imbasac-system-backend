package com.paulfernandosr.possystembackend.role.domain.port.output;

import com.paulfernandosr.possystembackend.permission.domain.Permission;
import com.paulfernandosr.possystembackend.role.domain.Role;

import java.util.Collection;
import java.util.Optional;

public interface RoleRepository {
    Optional<Role> findById(Long roleId);
    Collection<Role> findAll();
    Collection<Permission> findRolePermissionsByRoleId(Long roleId);
}
