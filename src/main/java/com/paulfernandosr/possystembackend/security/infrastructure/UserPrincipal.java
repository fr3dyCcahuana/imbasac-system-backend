package com.paulfernandosr.possystembackend.security.infrastructure;

import lombok.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Set;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal implements UserDetails {
    private String username;
    private String password;
    private Set<SimpleGrantedAuthority> authorities;
    private boolean enabled;
}
