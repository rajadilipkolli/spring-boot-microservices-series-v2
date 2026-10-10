package com.example.retailstore.webapp.exception;

public class UserAlreadyExistsException extends RuntimeException {
    /**
     * Creates an exception describing a duplicate username or email address.
     *
     * @param message description of the registration conflict
     */
    public UserAlreadyExistsException(String message) {
        super(message);
    }
}
