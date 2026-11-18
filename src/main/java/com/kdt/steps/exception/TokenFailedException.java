package com.kdt.steps.exception;

public class TokenFailedException extends UnauthorizedException {
    public TokenFailedException() { super("invalid token"); }
}
