package com.fooddelivery.payment.api.controller;

import com.fooddelivery.payment.domain.exception.IdempotencyKeyAlreadyUsedException;
import com.fooddelivery.payment.domain.exception.InvalidPaymentRequestException;
import com.fooddelivery.payment.domain.exception.InvalidPaymentStateException;
import com.fooddelivery.payment.domain.exception.PaymentNotFoundException;
import com.fooddelivery.payment.domain.exception.RefundExceedsPaymentAmountException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    ProblemDetail handleNotFound(PaymentNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({IdempotencyKeyAlreadyUsedException.class, InvalidPaymentStateException.class})
    ProblemDetail handleConflict(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({InvalidPaymentRequestException.class, RefundExceedsPaymentAmountException.class})
    ProblemDetail handleBadRequest(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid payment request");
    }
}
