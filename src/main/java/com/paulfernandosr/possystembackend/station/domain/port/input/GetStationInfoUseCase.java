package com.paulfernandosr.possystembackend.station.domain.port.input;

import com.paulfernandosr.possystembackend.station.domain.Station;

public interface GetStationInfoUseCase {
    Station getStationInfoById(Long stationId);
}
