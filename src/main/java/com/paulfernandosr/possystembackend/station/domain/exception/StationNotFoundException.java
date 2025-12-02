package com.paulfernandosr.possystembackend.station.domain.exception;

public class StationNotFoundException extends RuntimeException {
    public StationNotFoundException(String message) {
        super(message);
    }
}
