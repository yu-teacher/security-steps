package com.kdt.steps.exception;

public record ApiError(
        String code,
        String message
) {
}
