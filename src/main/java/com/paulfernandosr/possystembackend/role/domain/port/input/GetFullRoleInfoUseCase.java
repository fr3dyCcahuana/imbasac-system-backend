package com.paulfernandosr.possystembackend.role.domain.port.input;

import com.paulfernandosr.possystembackend.role.domain.Role;

public interface GetFullRoleInfoUseCase {
    Role getFullRoleInfoById(Long roleId);
}
