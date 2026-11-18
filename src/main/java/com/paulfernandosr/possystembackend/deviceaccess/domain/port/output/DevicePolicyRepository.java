package com.paulfernandosr.possystembackend.deviceaccess.domain.port.output;
import com.paulfernandosr.possystembackend.deviceaccess.domain.DeviceAccessMode;
import com.paulfernandosr.possystembackend.role.domain.RoleName;
import java.util.Optional;
public interface DevicePolicyRepository {
    Optional<DeviceAccessMode> findByRole(Long roleId, RoleName roleName);
    Optional<DeviceAccessMode> findForUser(Long userId, Long roleId);
}
