package com.fooddelivery.restaurant.domain.exception;

import java.util.UUID;

public class CategoryNotEmptyException extends RuntimeException {
    public CategoryNotEmptyException(UUID categoryId) {
        super("Menu category is not empty: " + categoryId);
    }
}
