package com.makeup.location.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MuaSummaryDto {
    private Long userId;
    private String fullName;
    private String avatarUrl;
    private Double rating;
    private Integer totalCompletedJobs;
    private String currentStatus;
    private List<MuaServiceResponseDto> services;
}
