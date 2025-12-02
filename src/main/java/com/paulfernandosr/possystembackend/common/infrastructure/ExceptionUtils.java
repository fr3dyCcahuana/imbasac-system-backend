package com.paulfernandosr.possystembackend.common.infrastructure;

import java.util.Optional;

public class ExceptionUtils {
    public static String getError(Exception exception) {
        return Optional.ofNullable(exception)
                .map(Exception::getMessage)
                .orElse(null);
    }

    public static String getCause(Exception exception) {
        return Optional.ofNullable(exception)
                .map(Exception::getCause)
                .map(Throwable::getMessage)
                .orElse(null);
    }
}
