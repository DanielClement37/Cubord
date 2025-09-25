package org.cubord.cubordbackend.exception;

import org.springframework.http.HttpStatus;

public class BusinessRuleViolationException extends CubordException {
    public BusinessRuleViolationException(String message) {
        super("BUSINESS_RULE_VIOLATION", message, HttpStatus.BAD_REQUEST);
    }

    public BusinessRuleViolationException(String message, Throwable cause) {
        super("BUSINESS_RULE_VIOLATION", message, HttpStatus.BAD_REQUEST, cause);
    }
}
