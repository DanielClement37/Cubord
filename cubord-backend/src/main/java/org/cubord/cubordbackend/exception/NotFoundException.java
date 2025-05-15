package org.cubord.cubordbackend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {
    /**
     * Creates a new NotFoundException with the specified error message.
     *
     * @param message The error message describing why the resource was not found
     */
    public NotFoundException(String message) {
        super(message);
    }
}
