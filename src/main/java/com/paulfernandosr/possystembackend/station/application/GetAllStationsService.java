package com.paulfernandosr.possystembackend.station.application;

import com.paulfernandosr.possystembackend.station.domain.Station;
import com.paulfernandosr.possystembackend.station.domain.StationStatus;
import com.paulfernandosr.possystembackend.station.domain.port.input.GetAllStationsUseCase;
import com.paulfernandosr.possystembackend.station.domain.port.output.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class GetAllStationsService implements GetAllStationsUseCase {
    private final StationRepository stationRepository;

    @Override
    public Collection<Station> getAllStations(StationStatus status) {
        return stationRepository.findAll(status);
    }
}
