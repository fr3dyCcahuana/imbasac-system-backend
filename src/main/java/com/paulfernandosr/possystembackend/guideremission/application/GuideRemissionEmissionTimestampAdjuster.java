package com.paulfernandosr.possystembackend.guideremission.application;

import com.paulfernandosr.possystembackend.guideremission.domain.GuideRemissionData;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class GuideRemissionEmissionTimestampAdjuster {
    private static final ZoneId LIMA_ZONE = ZoneId.of("America/Lima");
    private static final long SUNAT_CLOCK_SAFETY_DELAY_MINUTES = 2;

    public void applySafeEmissionTimestamp(GuideRemissionData guia) {
        applySafeEmissionTimestamp(guia, ZonedDateTime.now(LIMA_ZONE));
    }

    void applySafeEmissionTimestamp(GuideRemissionData guia, ZonedDateTime now) {
        if (guia == null || now == null) {
            return;
        }

        ZonedDateTime safeEmissionDateTime = now
                .withZoneSameInstant(LIMA_ZONE)
                .minusMinutes(SUNAT_CLOCK_SAFETY_DELAY_MINUTES)
                .withNano(0);

        guia.setFechaEmision(safeEmissionDateTime.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE));
        guia.setHoraEmision(safeEmissionDateTime.toLocalTime().format(DateTimeFormatter.ISO_LOCAL_TIME));
    }
}
