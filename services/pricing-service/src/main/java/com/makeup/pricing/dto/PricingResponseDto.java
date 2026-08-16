package com.makeup.pricing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingResponseDto {
    private String servicePackageId;
    private double basePackageFee;
    private double optionsFee;
    private double packageSubtotal;
    private double travelDistanceFee;
    private double surgeMultiplier;
    private double totalFee;
    private String currency;
}
