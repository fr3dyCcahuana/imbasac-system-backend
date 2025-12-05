package com.paulfernandosr.possystembackend.catalog.domain.exception;

import com.paulfernandosr.possystembackend.common.domain.exception.DomainException;
import lombok.Getter;

@Getter
public class CatalogItemAlreadyExistsException extends DomainException {

    public static final String ERROR_MESSAGE = "catalog.item.exists.message";

    public CatalogItemAlreadyExistsException(String message) {
        super(message);
    }

    @Override
    public String getErrorMessage() {
        return ERROR_MESSAGE;
    }
}
