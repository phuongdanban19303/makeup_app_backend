package com.makeup.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMuaProfileRequestDto {
    private String bio;
    private String avatarUrl;
    private String identityCardUrl;
    private String currentStatus; // 'ONLINE', 'OFFLINE', 'BUSY'
}
