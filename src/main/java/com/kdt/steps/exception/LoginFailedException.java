package com.kdt.steps.exception;

public class LoginFailedException extends UnauthorizedException {
    public LoginFailedException() { super("invalid credentials"); }
}
