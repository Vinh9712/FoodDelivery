package com.fooddelivery.delivery.api.controller;

import com.fooddelivery.delivery.domain.exception.DeliveryAccessDeniedException;
import com.fooddelivery.delivery.domain.exception.DeliveryNotFoundException;
import com.fooddelivery.delivery.domain.exception.DriverNotEligibleException;
import com.fooddelivery.delivery.domain.exception.DriverNotFoundException;
import com.fooddelivery.delivery.domain.exception.InvalidDeliveryStateException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DeliveryNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(DeliveryNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DriverNotFoundException.class)
    public ResponseEntity<Map<String, String>> driverNotFound(DriverNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler({InvalidDeliveryStateException.class, DriverNotEligibleException.class})
    public ResponseEntity<Map<String, String>> conflict(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(DeliveryAccessDeniedException.class)
    public ResponseEntity<Map<String, String>> forbidden(DeliveryAccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }
}
