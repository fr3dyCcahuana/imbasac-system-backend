package com.paulfernandosr.possystembackend.permission.domain.port.output;

import com.paulfernandosr.possystembackend.permission.domain.Permission;

import java.util.Collection;

public interface PermissionRepository {
    Collection<Permission> findAll();
}
