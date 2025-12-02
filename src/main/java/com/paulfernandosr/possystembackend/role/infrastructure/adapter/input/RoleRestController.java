package com.paulfernandosr.possystembackend.role.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.role.domain.port.input.GetAllRolesUseCase;
import com.paulfernandosr.possystembackend.role.domain.Role;
import com.paulfernandosr.possystembackend.role.domain.port.input.GetFullRoleInfoUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@RequiredArgsConstructor
@RequestMapping("/roles")
public class RoleRestController {
    private final GetAllRolesUseCase getAllRolesUseCase;
    private final GetFullRoleInfoUseCase getFullRoleInfoUseCase;

    @GetMapping
    public ResponseEntity<SuccessResponse<Collection<Role>>> getAllRoles() {
        return ResponseEntity.ok(SuccessResponse.ok(getAllRolesUseCase.getAllRoles()));
    }

    @GetMapping("/{roleId}")
    public ResponseEntity<SuccessResponse<Role>> getFullRoleInfo(@PathVariable Long roleId) {
        return ResponseEntity.ok(SuccessResponse.ok(getFullRoleInfoUseCase.getFullRoleInfoById(roleId)));
    }
}
