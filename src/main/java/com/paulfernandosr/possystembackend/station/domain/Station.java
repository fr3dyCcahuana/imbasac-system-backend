package com.paulfernandosr.possystembackend.station.domain;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Station {
    private Long id;
    private String name;
    private String number;
    private boolean enabled;
    private StationStatus status;

    public boolean isOpen() {
        return StationStatus.OPEN.equals(status);
    }
}
