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
public class UserProfileDto {
    private String id;
    private String fullName;
    private String phoneNumber;
    private String email;
    private String avatarUrl;
    private String userRole; // "CUSTOMER" or "MUA"
    private String status;   // "ACTIVE", "INACTIVE", "BUSY", "ONLINE", "OFFLINE"
    private List<String> roles;
}
