package com.paulfernandosr.possystembackend.deviceaccess.infrastructure.adapter.output;
import com.paulfernandosr.possystembackend.deviceaccess.domain.DeviceAccessMode;
import com.paulfernandosr.possystembackend.deviceaccess.domain.port.output.DevicePolicyRepository;
import com.paulfernandosr.possystembackend.role.domain.RoleName;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.Optional;
@Repository
@RequiredArgsConstructor
public class PostgresDevicePolicyRepository implements DevicePolicyRepository {
    private final JdbcClient jdbc;
    @Override public Optional<DeviceAccessMode> findForUser(Long userId, Long roleId) {
        return jdbc.sql("""
                SELECT CASE WHEN c.certificate_required THEN 'CERTIFICATE_REQUIRED' ELSE p.mode END
                FROM users u JOIN roles r ON r.id=u.role_id
                JOIN security_role_device_policy p ON p.role_id=r.id
                LEFT JOIN security_user_access_control c ON c.user_id=u.id
                WHERE u.id=:userId AND r.id=:roleId
                """).param("userId",userId).param("roleId",roleId)
                .query(String.class).optional().map(DeviceAccessMode::valueOf);
    }
    @Override public Optional<DeviceAccessMode> findByRole(Long roleId, RoleName roleName) {
        return jdbc.sql("""
                SELECT p.mode FROM security_role_device_policy p
                JOIN roles r ON r.id=p.role_id
                WHERE p.role_id=:roleId AND r.name::text=:roleName
                """).param("roleId",roleId).param("roleName",roleName.name())
                .query(String.class).optional().map(DeviceAccessMode::valueOf);
    }
}
