package com.paulfernandosr.possystembackend.security.domain;

public record Token(String value, String type, long expiresIn) {
}
