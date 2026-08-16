package com.makeup.user.controller;

import com.makeup.common.exception.AppException;
import com.makeup.common.exception.ErrorCode;
import com.makeup.common.response.ApiResponse;
import com.makeup.user.dto.UserProfileDto;
import com.makeup.user.entity.MuaProfileEntity;
import com.makeup.user.entity.RoleEntity;
import com.makeup.user.entity.UserEntity;
import com.makeup.user.repository.MuaProfileRepository;
import com.makeup.user.repository.UserRepository;
import com.makeup.user.service.MuaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final MuaProfileRepository muaProfileRepository;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileDto>> getMyProfile(@AuthenticationPrincipal String currentUserId) {
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "User not authenticated");
        }
        Long userId = Long.parseLong(currentUserId);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "User not found"));

        List<String> roles = user.getRoles() != null
                ? user.getRoles().stream().map(RoleEntity::getName).toList()
                : List.of("ROLE_CUSTOMER");

        String avatar = user.getAvatarUrl();
        if ((avatar == null || avatar.isBlank()) && roles.contains("ROLE_MUA")) {
            avatar = muaProfileRepository.findById(userId)
                    .map(MuaProfileEntity::getAvatarUrl)
                    .orElse(null);
        }
        if (avatar == null || avatar.isBlank()) {
            avatar = MuaService.DEFAULT_AVATAR_URL;
        }

        UserProfileDto dto = UserProfileDto.builder()
                .id(user.getId().toString())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhone())
                .email(user.getEmail())
                .avatarUrl(avatar)
                .userRole(roles.contains("ROLE_MUA") ? "MUA" : "CUSTOMER")
                .status(user.getIsActive() != null && user.getIsActive() ? "ACTIVE" : "INACTIVE")
                .roles(roles)
                .build();

        return ResponseEntity.ok(ApiResponse.success(dto));
    }

    @PutMapping("/me/avatar")
    public ResponseEntity<ApiResponse<UserProfileDto>> updateMyAvatar(
            @AuthenticationPrincipal String currentUserId,
            @RequestBody Map<String, String> payload) {
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "User not authenticated");
        }
        Long userId = Long.parseLong(currentUserId);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "User not found"));

        String newAvatarUrl = payload.get("avatarUrl");
        if (newAvatarUrl == null || newAvatarUrl.isBlank()) {
            newAvatarUrl = payload.get("avatar");
        }

        if (newAvatarUrl != null && !newAvatarUrl.isBlank()) {
            String cleanUrl = newAvatarUrl.trim();
            user.setAvatarUrl(cleanUrl);
            userRepository.save(user);

            // If user is a MUA, sync avatar to mua_profiles table as well
            muaProfileRepository.findById(userId).ifPresent(profile -> {
                profile.setAvatarUrl(cleanUrl);
                muaProfileRepository.save(profile);
            });
        }

        return getMyProfile(currentUserId);
    }
}
