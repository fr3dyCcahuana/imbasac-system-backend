package com.paulfernandosr.possystembackend.station.domain.exception;

public class InvalidStationException extends RuntimeException {
    public InvalidStationException(String message) {
        super(message);
    }
}
