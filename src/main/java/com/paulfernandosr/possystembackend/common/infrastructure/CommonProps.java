package com.paulfernandosr.possystembackend.common.infrastructure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "common")
public class CommonProps {
    private String hashedPassword;
}
