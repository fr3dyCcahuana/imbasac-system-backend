package com.paulfernandosr.possystembackend.timeactivity.infrastructure;

import com.paulfernandosr.possystembackend.timeactivity.application.TimeActivityService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "imbasac.time-activity", name = "enabled", havingValue = "true")
public class TimeActivityConfiguration {
    @Bean
    TimeActivityService timeActivityService(JdbcClient jdbc,
            @Value("${imbasac.time-activity.idle-minutes:10}") int idleMinutes,
            @Value("${imbasac.time-activity.policy-version:1}") int policyVersion,
            @Value("${imbasac.time-activity.read-commercial-results:false}") boolean readCommercialResults) {
        return new TimeActivityService(new TimeActivityStore(jdbc), idleMinutes, policyVersion, readCommercialResults);
    }
}
