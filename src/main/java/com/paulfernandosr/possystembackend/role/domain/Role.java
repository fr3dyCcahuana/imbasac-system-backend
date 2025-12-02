package com.paulfernandosr.possystembackend.role.domain;

import com.paulfernandosr.possystembackend.permission.domain.Permission;
import lombok.*;

import java.util.Collection;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class Role {
    private Long id;
    private RoleName name;
    private String description;
    private Collection<Permission> permissions;
}
