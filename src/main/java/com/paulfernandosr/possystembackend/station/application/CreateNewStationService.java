package com.paulfernandosr.possystembackend.station.application;

import com.paulfernandosr.possystembackend.station.domain.Station;
import com.paulfernandosr.possystembackend.station.domain.port.input.CreateNewStationUseCase;
import com.paulfernandosr.possystembackend.station.domain.port.output.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CreateNewStationService implements CreateNewStationUseCase {
    private final StationRepository stationRepository;

    @Override
    public void createNewStation(Station station) {
        stationRepository.create(station);
    }
}
