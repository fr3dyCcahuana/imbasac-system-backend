package com.paulfernandosr.possystembackend.station.domain.port.input;

import com.paulfernandosr.possystembackend.station.domain.Station;

public interface CreateNewStationUseCase {
    void createNewStation(Station station);
}
