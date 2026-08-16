package com.makeup.location.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderServiceOptionDto {
    private Long id;
    private String optionType; // 'COMPONENT' hoặc 'ADD_ON'
    private String optionName;
    private BigDecimal price;
    private Boolean isDefault;
    private Boolean isRemovable;
}
