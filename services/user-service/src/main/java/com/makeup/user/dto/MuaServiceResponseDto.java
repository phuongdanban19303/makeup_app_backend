package com.makeup.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MuaServiceResponseDto {
    private Long id;
    private Long muaId;
    private String category;
    private String serviceName;
    private String description;
    private BigDecimal basePrice;
    private Integer estimatedDurationMinutes;
    private List<String> subServices;
    private Boolean isActive;
    private ZonedDateTime createdAt;
}
