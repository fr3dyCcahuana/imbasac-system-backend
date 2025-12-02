package com.paulfernandosr.possystembackend.role.domain.port.input;

import com.paulfernandosr.possystembackend.role.domain.Role;

import java.util.Collection;

public interface GetAllRolesUseCase {
    Collection<Role> getAllRoles();
}
