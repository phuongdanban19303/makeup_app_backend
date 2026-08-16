package com.makeup.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MuaFullProfileResponseDto {
    private Long userId;
    private String fullName;
    private String phone;
    private String email;
    private String avatarUrl;
    private String bio;
    private String identityCardUrl;
    private Boolean isVerified;
    private BigDecimal rating;
    private Integer totalReviews;
    private Integer totalCompletedJobs;
    private String currentStatus;
    private List<ProviderServiceResponseDto> services;
    private List<MuaPortfolioResponseDto> portfolios;
}

