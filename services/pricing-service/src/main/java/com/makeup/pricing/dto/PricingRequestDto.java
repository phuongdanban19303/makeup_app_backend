package com.makeup.pricing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PricingRequestDto {
    private String servicePackageId;
    private double basePackageFee; // Tiền công cốt lõi không thể bỏ
    private double optionsFee;      // Phụ phí từ các options / add-ons được chọn
    private double distanceInKm;    // Quãng đường di chuyển (km)
    private double customerLat;
    private double customerLng;
}
