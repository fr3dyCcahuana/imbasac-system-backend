package com.paulfernandosr.possystembackend.customer.application;

import com.paulfernandosr.possystembackend.customer.infrastructure.config.CustomerMapProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerMapZoomConfigurationTest {
    @Test
    void defaultsAllowInspectingAnIndividualAddress() {
        var map = new CustomerMapProperties().getMap();
        assertStreetDetailZoom(map.getMaxZoom(), map.getUrbanZoom());
    }

    @ParameterizedTest
    @ValueSource(strings = {"application.yml", "application-local.yml", "application-prod.yml"})
    void profilesDoNotOverrideStreetDetailWithACityLevelLimit(String file) {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource(file));
        Properties values = yaml.getObject();
        assertThat(values).isNotNull();
        double maxZoom = Double.parseDouble(values.getProperty("app.customer-map.map.max-zoom"));
        double urbanZoom = Double.parseDouble(values.getProperty("app.customer-map.map.urban-zoom"));
        assertStreetDetailZoom(maxZoom, urbanZoom);
    }

    private void assertStreetDetailZoom(double maxZoom, double urbanZoom) {
        assertThat(maxZoom).as("manual zoom must allow inspecting a building").isGreaterThanOrEqualTo(20);
        assertThat(urbanZoom).as("selecting a customer should show street detail").isGreaterThanOrEqualTo(17);
        assertThat(urbanZoom).isLessThanOrEqualTo(maxZoom);
    }
}
