package com.pharmaflow.auth_service.presentation.dto.response;

public record ResponseMessageDto(
        String message
) {
    public static ResponseMessageDto of(String message) {
        return new ResponseMessageDto(message);
    }
}
