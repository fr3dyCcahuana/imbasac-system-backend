package com.paulfernandosr.possystembackend.station.domain.port.input;

import com.paulfernandosr.possystembackend.station.domain.Station;
import com.paulfernandosr.possystembackend.station.domain.StationStatus;

import java.util.Collection;

public interface GetAllStationsUseCase {
    Collection<Station> getAllStations(StationStatus status);
}
