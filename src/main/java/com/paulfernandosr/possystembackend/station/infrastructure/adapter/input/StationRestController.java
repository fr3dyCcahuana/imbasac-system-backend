package com.paulfernandosr.possystembackend.station.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.station.domain.Station;
import com.paulfernandosr.possystembackend.station.domain.StationStatus;
import com.paulfernandosr.possystembackend.station.domain.port.input.CreateNewStationUseCase;
import com.paulfernandosr.possystembackend.station.domain.port.input.GetAllStationsUseCase;
import com.paulfernandosr.possystembackend.station.domain.port.input.GetStationInfoUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;

@RestController
@RequiredArgsConstructor
@RequestMapping("/stations")
public class StationRestController {
    private final CreateNewStationUseCase createNewStationUseCase;
    private final GetAllStationsUseCase getAllStationsUseCase;
    private final GetStationInfoUseCase getStationInfoUseCase;

    @PostMapping
    public ResponseEntity<Void> createNewStation(@RequestBody Station station) {
        createNewStationUseCase.createNewStation(station);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<SuccessResponse<Collection<Station>>> getAllStations(@RequestParam(required = false) StationStatus status) {
        return ResponseEntity.ok(SuccessResponse.ok(getAllStationsUseCase.getAllStations(status)));
    }

    @GetMapping("/{stationId}")
    public ResponseEntity<SuccessResponse<Station>> getStationInfo(@PathVariable Long stationId) {
        return ResponseEntity.ok(SuccessResponse.ok(getStationInfoUseCase.getStationInfoById(stationId)));
    }
}
