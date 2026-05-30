package com.pharmaflow.auth_service.presentation.dto.response;

import com.pharmaflow.auth_service.config.security.CustomUserDetails;
import lombok.Builder;

import java.util.Set;

@Builder
public record ResponseLoginDto(
        String accessToken,
        String refreshToken,
        String tokenType,
        Long userId,
        String email,
        Set<String> roles
) {
    public static ResponseLoginDto of(CustomUserDetails user, String accessToken, String refreshToken) {
        return ResponseLoginDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .userId(user.getUserId())
                .email(user.getUsername())
                .roles(user.getRoleNames())
                .build();
    }
}
