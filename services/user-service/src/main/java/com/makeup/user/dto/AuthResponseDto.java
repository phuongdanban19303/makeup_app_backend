package com.makeup.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponseDto {

    private String accessToken;
    private String refreshToken;
    private String tokenType; // "Bearer"
    private long expiresInMs;
    private Long userId;
    private String phone;
    private String fullName;
    private String avatarUrl;
    private List<String> roles;
    private UserResponseDto user;
}

