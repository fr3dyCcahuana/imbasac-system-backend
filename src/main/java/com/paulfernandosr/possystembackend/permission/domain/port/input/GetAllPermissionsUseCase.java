package com.paulfernandosr.possystembackend.permission.domain.port.input;

import com.paulfernandosr.possystembackend.permission.domain.Permission;

import java.util.Collection;

public interface GetAllPermissionsUseCase {
    Collection<Permission> getAllPermissions();
}
