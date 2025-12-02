package com.paulfernandosr.possystembackend.user.domain;

import com.paulfernandosr.possystembackend.permission.domain.Permission;
import com.paulfernandosr.possystembackend.role.domain.Role;
import com.paulfernandosr.possystembackend.role.domain.RoleName;
import lombok.*;

import java.util.Collection;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Long id;
    private String firstName;
    private String lastName;
    private String username;
    private String password;
    private Role role;
    private boolean enabled;
    private Collection<Permission> permissions;
    private UserStatus status;

    public User(Long id) {
        this.id = id;
    }

    public User(String username) {
        this.username = username;
    }

    public boolean isOnRegister() {
        return UserStatus.ON_REGISTER.equals(status);
    }

    public boolean isNotOnRegister() {
        return !isOnRegister();
    }

    public boolean isCashier() {
        return RoleName.CASHIER.equals(role.getName());
    }
}
