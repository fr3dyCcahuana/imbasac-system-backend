package com.paulfernandosr.possystembackend.station.domain.port.output;

import com.paulfernandosr.possystembackend.station.domain.Station;
import com.paulfernandosr.possystembackend.station.domain.StationStatus;
import com.paulfernandosr.possystembackend.user.domain.User;

import java.util.Collection;
import java.util.Optional;

public interface StationRepository {
    void create(Station station);
    Optional<Station> findById(Long id);
    Optional<Station> findByUserOnRegister(User user);
    Collection<Station> findAll(StationStatus status);
}
