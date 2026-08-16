package com.makeup.user.service;

import com.makeup.common.exception.AppException;
import com.makeup.common.exception.ErrorCode;
import com.makeup.user.dto.AuthResponseDto;
import com.makeup.user.dto.LoginRequestDto;
import com.makeup.user.dto.LogoutRequestDto;
import com.makeup.user.dto.RefreshTokenRequestDto;
import com.makeup.user.dto.RegisterRequestDto;
import com.makeup.user.dto.UserResponseDto;
import com.makeup.user.entity.MuaProfileEntity;
import com.makeup.user.entity.RoleEntity;
import com.makeup.user.entity.UserEntity;
import com.makeup.user.repository.MuaProfileRepository;
import com.makeup.user.repository.RoleRepository;
import com.makeup.user.repository.UserRepository;
import com.makeup.user.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String MOCK_OTP = "123456"; // Default mock OTP for testing
    private static final String REDIS_REFRESH_TOKEN_PREFIX = "refresh_token:";
    private final Map<String, String> otpStorage = new ConcurrentHashMap<>();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final MuaProfileRepository muaProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final StringRedisTemplate redisTemplate;

    public String sendOtp(String phone) {
        otpStorage.put(phone, MOCK_OTP);
        return MOCK_OTP;
    }

    @Transactional
    public AuthResponseDto register(RegisterRequestDto request) {
        // 1. Verify OTP Code
        String validOtp = otpStorage.getOrDefault(request.getPhone(), MOCK_OTP);
        if (!validOtp.equals(request.getOtpCode())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Invalid or expired OTP code");
        }

        // 2. Check if phone is registered
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Phone number is already registered");
        }

        // 3. Resolve Target Role
        String roleName = request.getRole().toUpperCase().startsWith("ROLE_")
                ? request.getRole().toUpperCase()
                : "ROLE_" + request.getRole().toUpperCase();

        RoleEntity role = roleRepository.findByName(roleName)
                .orElseThrow(
                        () -> new AppException(ErrorCode.INVALID_REQUEST, "Invalid user role: " + request.getRole()));

        // 4. Create User Entity
        UserEntity user = UserEntity.builder()
                .phone(request.getPhone())
                .email(request.getEmail())
                .fullName(request.getFullName())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .isActive(true)
                .roles(Set.of(role))
                .build();

        user = userRepository.save(user);

        // 5. If registering as MUA, initialize MUA Extended Profile
        if ("ROLE_MUA".equals(role.getName())) {
            MuaProfileEntity muaProfile = MuaProfileEntity.builder()
                    .userId(user.getId())
                    .bio("Professional Makeup Artist")
                    .isVerified(false)
                    .rating(BigDecimal.valueOf(5.00))
                    .totalReviews(0)
                    .totalCompletedJobs(0)
                    .currentStatus("OFFLINE")
                    .build();
            muaProfileRepository.save(muaProfile);
        }

        // 6. Generate JWT Token
        return buildAuthResponse(user);
    }

    public AuthResponseDto login(LoginRequestDto request) {
        // 1. Find user by phone
        UserEntity user = userRepository.findByPhone(request.getPhone())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND,
                        "User with phone " + request.getPhone() + " not found"));

        // 2. Validate Password or OTP
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                throw new AppException(ErrorCode.UNAUTHORIZED, "Invalid phone number or password");
            }

        } else {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Password or OTP code is required for login");
        }

        // 3. Generate JWT Token
        return buildAuthResponse(user);
    }

    public AuthResponseDto refreshToken(RefreshTokenRequestDto request) {
        String refreshToken = request.getRefreshToken();

        if (!tokenProvider.validateToken(refreshToken)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Invalid or expired refresh token");
        }

        String tokenType = tokenProvider.getTokenType(refreshToken);
        if (!"REFRESH".equals(tokenType)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Token is not a valid refresh token");
        }

        Long userId = Long.parseLong(tokenProvider.getUserIdFromToken(refreshToken));

        // Check Redis for stored refresh token
        String storedToken = redisTemplate.opsForValue().get(REDIS_REFRESH_TOKEN_PREFIX + userId);
        if (storedToken == null || !storedToken.equals(refreshToken)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "Refresh token is invalid or has been revoked");
        }

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "User not found"));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "User account is inactive");
        }

        return buildAuthResponse(user);
    }

    public void logout(LogoutRequestDto request) {
        String refreshToken = request.getRefreshToken();
        if (tokenProvider.validateToken(refreshToken)) {
            String tokenType = tokenProvider.getTokenType(refreshToken);
            if ("REFRESH".equals(tokenType)) {
                Long userId = Long.parseLong(tokenProvider.getUserIdFromToken(refreshToken));
                redisTemplate.delete(REDIS_REFRESH_TOKEN_PREFIX + userId);
            }
        }
    }

    private AuthResponseDto buildAuthResponse(UserEntity user) {
        var roleNames = user.getRoles().stream().map(RoleEntity::getName).toList();
        String accessToken = tokenProvider.generateToken(user.getId(), roleNames);
        String refreshToken = tokenProvider.generateRefreshToken(user.getId());

        // Get avatarUrl from UserEntity or MuaProfileEntity
        String avatar = user.getAvatarUrl();
        if ((avatar == null || avatar.isBlank()) && roleNames.contains("ROLE_MUA")) {
            avatar = muaProfileRepository.findById(user.getId())
                    .map(MuaProfileEntity::getAvatarUrl)
                    .orElse(null);
        }
        if (avatar == null || avatar.isBlank()) {
            avatar = MuaService.DEFAULT_AVATAR_URL;
        }

        // Store Refresh Token in Redis with TTL
        redisTemplate.opsForValue().set(
                REDIS_REFRESH_TOKEN_PREFIX + user.getId(),
                refreshToken,
                tokenProvider.getRefreshTokenExpirationInMs(),
                TimeUnit.MILLISECONDS
        );

        UserResponseDto userDto = UserResponseDto.builder()
                .id(user.getId())
                .phone(user.getPhone())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .avatarUrl(avatar)
                .roles(roleNames)
                .build();

        return AuthResponseDto.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresInMs(86400000)
                .userId(user.getId())
                .phone(user.getPhone())
                .fullName(user.getFullName())
                .avatarUrl(avatar)
                .roles(roleNames)
                .user(userDto)
                .build();
    }
}
