package com.paulfernandosr.possystembackend.station.application;

import com.paulfernandosr.possystembackend.station.domain.Station;
import com.paulfernandosr.possystembackend.station.domain.exception.StationNotFoundException;
import com.paulfernandosr.possystembackend.station.domain.port.input.GetStationInfoUseCase;
import com.paulfernandosr.possystembackend.station.domain.port.output.StationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetStationInfoService implements GetStationInfoUseCase {
    private final StationRepository stationRepository;

    @Override
    public Station getStationInfoById(Long stationId) {
        return stationRepository.findById(stationId)
                .orElseThrow(() -> new StationNotFoundException("Station not found with identification: " + stationId));
    }
}
