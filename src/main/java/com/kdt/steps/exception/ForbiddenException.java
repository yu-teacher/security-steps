package com.kdt.steps.exception;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException() { super("not the owner"); }

    public ForbiddenException(String message) { super(message); }
}
