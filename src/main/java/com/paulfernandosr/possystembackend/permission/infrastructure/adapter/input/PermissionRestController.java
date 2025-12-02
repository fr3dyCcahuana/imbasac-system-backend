package com.paulfernandosr.possystembackend.permission.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.permission.domain.Permission;
import com.paulfernandosr.possystembackend.permission.domain.port.input.GetAllPermissionsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@RequiredArgsConstructor
@RequestMapping("/permissions")
public class PermissionRestController {
    private final GetAllPermissionsUseCase getAllPermissionsUseCase;

    @GetMapping
    public ResponseEntity<SuccessResponse<Collection<Permission>>> getAllPermissions() {
        return ResponseEntity.ok(SuccessResponse.ok(getAllPermissionsUseCase.getAllPermissions()));
    }
}
