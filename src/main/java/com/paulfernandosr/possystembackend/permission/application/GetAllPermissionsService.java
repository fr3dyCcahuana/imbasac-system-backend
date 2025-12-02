package com.paulfernandosr.possystembackend.permission.application;

import com.paulfernandosr.possystembackend.permission.domain.Permission;
import com.paulfernandosr.possystembackend.permission.domain.port.input.GetAllPermissionsUseCase;
import com.paulfernandosr.possystembackend.permission.domain.port.output.PermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class GetAllPermissionsService implements GetAllPermissionsUseCase {
    private final PermissionRepository permissionRepository;

    @Override
    public Collection<Permission> getAllPermissions() {
        return permissionRepository.findAll();
    }
}
