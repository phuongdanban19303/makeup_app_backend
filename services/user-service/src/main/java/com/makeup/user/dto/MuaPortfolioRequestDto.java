package com.makeup.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MuaPortfolioRequestDto {
    private String imageUrl;
    private String caption;
}
