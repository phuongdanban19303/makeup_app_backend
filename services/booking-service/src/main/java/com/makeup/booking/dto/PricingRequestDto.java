package com.makeup.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO gửi yêu cầu tính cước tới Pricing Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingRequestDto {
    private String servicePackageId;
    private double basePackageFee;
    private double optionsFee;
    private double distanceInKm;
    private double customerLat;
    private double customerLng;
}
