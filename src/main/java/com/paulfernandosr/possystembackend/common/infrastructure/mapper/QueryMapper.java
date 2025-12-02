package com.paulfernandosr.possystembackend.common.infrastructure.mapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

public class QueryMapper {
    public static String formatAsLikeParam(String param) {
        return "%" + param + "%";
    }

    public static LocalDateTime mapTimestamp(Timestamp timestamp) {
        return Optional.ofNullable(timestamp)
                .map(Timestamp::toLocalDateTime)
                .orElse(null);
    }

    public static <T extends Enum<T>> String mapEnum(T anEnum) {
        return Optional.ofNullable(anEnum)
                .map(Enum::toString)
                .orElse(null);
    }
}
