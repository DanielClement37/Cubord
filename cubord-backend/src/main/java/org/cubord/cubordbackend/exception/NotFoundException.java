package org.cubord.cubordbackend.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends CubordException {
    public NotFoundException(String resource, Object id) {
        super("RESOURCE_NOT_FOUND",
                String.format("%s with id %s not found", resource, id),
                HttpStatus.NOT_FOUND);
    }

    public NotFoundException(String message) {
        super("RESOURCE_NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }
}