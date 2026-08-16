package com.makeup.user.controller;

import com.makeup.common.response.ApiResponse;
import com.makeup.user.dto.AuthResponseDto;
import com.makeup.user.dto.LoginRequestDto;
import com.makeup.user.dto.LogoutRequestDto;
import com.makeup.user.dto.RefreshTokenRequestDto;
import com.makeup.user.dto.RegisterRequestDto;
import com.makeup.user.dto.SendOtpRequestDto;
import com.makeup.user.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse<Map<String, String>>> sendOtp(@Valid @RequestBody SendOtpRequestDto request) {
        String otp = authService.sendOtp(request.getPhone());
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("phone", request.getPhone(), "otp", otp),
                "OTP code sent successfully (Mock OTP: 123456)"
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponseDto>> register(@Valid @RequestBody RegisterRequestDto request) {
        AuthResponseDto response = authService.register(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Account registered successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponseDto>> login(@Valid @RequestBody LoginRequestDto request) {
        AuthResponseDto response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponseDto>> refreshToken(@Valid @RequestBody RefreshTokenRequestDto request) {
        AuthResponseDto response = authService.refreshToken(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed successfully"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@Valid @RequestBody LogoutRequestDto request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }
}

