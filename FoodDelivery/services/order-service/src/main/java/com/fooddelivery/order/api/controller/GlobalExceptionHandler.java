package com.fooddelivery.order.api.controller;

import com.fooddelivery.order.domain.exception.InvalidOrderStateException;
import com.fooddelivery.order.domain.exception.InvalidOrderRequestException;
import com.fooddelivery.order.domain.exception.OrderDependencyException;
import com.fooddelivery.order.domain.exception.OrderNotFoundException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Global exception handler for Order Service REST API.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail handleOrderNotFound(OrderNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Order Not Found");
        problem.setType(URI.create("https://api.fooddelivery.com/errors/order-not-found"));
        return problem;
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ProblemDetail handleInvalidOrderState(InvalidOrderStateException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Invalid Order State");
        problem.setType(URI.create("https://api.fooddelivery.com/errors/invalid-order-state"));
        return problem;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLockingFailure(OptimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Order state changed while the request was being processed. Reload and try again.");
        problem.setTitle("Order State Conflict");
        problem.setType(URI.create("https://api.fooddelivery.com/errors/order-state-conflict"));
        return problem;
    }

    @ExceptionHandler(InvalidOrderRequestException.class)
    public ProblemDetail handleInvalidOrderRequest(InvalidOrderRequestException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid Order Request");
        problem.setType(URI.create("https://api.fooddelivery.com/errors/invalid-order-request"));
        return problem;
    }

    @ExceptionHandler(OrderDependencyException.class)
    public ProblemDetail handleOrderDependency(OrderDependencyException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("Order Dependency Unavailable");
        problem.setType(URI.create("https://api.fooddelivery.com/errors/order-dependency-unavailable"));
        return problem;
    }

    @ExceptionHandler(FeignException.class)
    public ProblemDetail handleFeignException(FeignException ex) {
        HttpStatus status = HttpStatus.resolve(ex.status());
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }
        log.warn("Downstream microservice call failed with status {}", ex.status(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                status,
                "Downstream microservice call failed"
        );
        problem.setTitle("Microservice Communication Error");
        problem.setType(URI.create("https://api.fooddelivery.com/errors/microservice-communication-failure"));
        return problem;
    }
}
