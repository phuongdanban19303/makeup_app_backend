package com.makeup.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    SUCCESS(HttpStatus.OK, "SUCCESS", "Request completed successfully"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request parameters"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Requested resource not found"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User profile not found"),
    MUA_NOT_FOUND(HttpStatus.NOT_FOUND, "MUA_NOT_FOUND", "Makeup Artist not found"),
    INVALID_BOOKING_STATUS(HttpStatus.BAD_REQUEST, "INVALID_BOOKING_STATUS", "Invalid booking state transition"),
    PAYMENT_FAILED(HttpStatus.PAYMENT_REQUIRED, "PAYMENT_FAILED", "Payment transaction failed"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "An internal server error occurred"),
    UNCATEGORIZED_EXCEPTION(HttpStatus.INTERNAL_SERVER_ERROR, "UNCATEGORIZED_EXCEPTION", "Uncategorized exception occurred");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }
}
