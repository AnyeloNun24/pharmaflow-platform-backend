package com.pharmaflow.auth_service.service.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String email) {
        super("El email ya esta registrado: " + email);
    }
}
