package com.paulfernandosr.possystembackend.guideremission.application;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionData;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class GuideRemissionEmissionTimestampAdjusterTest {
    private final GuideRemissionEmissionTimestampAdjuster adjuster = new GuideRemissionEmissionTimestampAdjuster();

    @Test
    void appliesSafeLimaTimestampTwoMinutesBeforeEmission() {
        GuideRemissionData guia = GuideRemissionData.builder()
                .fechaEmision("2026-09-30")
                .horaEmision("09:12:00")
                .build();

        adjuster.applySafeEmissionTimestamp(
                guia,
                ZonedDateTime.of(2026, 9, 30, 9, 10, 43, 0, ZoneId.of("America/Lima"))
        );

        assertThat(guia.getFechaEmision()).isEqualTo("2026-09-30");
        assertThat(guia.getHoraEmision()).isEqualTo("09:08:43");
    }

    @Test
    void adjustsDateWhenSafetyMarginCrossesMidnight() {
        GuideRemissionData guia = GuideRemissionData.builder()
                .fechaEmision("2026-10-01")
                .horaEmision("00:01:00")
                .build();

        adjuster.applySafeEmissionTimestamp(
                guia,
                ZonedDateTime.of(2026, 10, 1, 0, 1, 30, 0, ZoneId.of("America/Lima"))
        );

        assertThat(guia.getFechaEmision()).isEqualTo("2026-09-30");
        assertThat(guia.getHoraEmision()).isEqualTo("23:59:30");
    }
}
