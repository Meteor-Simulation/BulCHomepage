package com.bulc.homepage.exception;

public class DeactivatedAccountException extends RuntimeException {
    public DeactivatedAccountException(String message) {
        super(message);
    }
}
