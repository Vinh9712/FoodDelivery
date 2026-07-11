package com.fooddelivery.order.api.controller;

import com.fooddelivery.order.domain.exception.InvalidOrderStateException;
import com.fooddelivery.order.domain.exception.InvalidOrderRequestException;
import com.fooddelivery.order.domain.exception.OrderDependencyException;
import com.fooddelivery.order.domain.exception.OrderNotFoundException;
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
}
