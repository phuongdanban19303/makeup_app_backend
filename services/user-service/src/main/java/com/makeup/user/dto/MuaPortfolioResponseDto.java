package com.makeup.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MuaPortfolioResponseDto {
    private Long id;
    private Long muaId;
    private String imageUrl;
    private String caption;
    private ZonedDateTime createdAt;
}
