package com.makeup.user.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class ProviderServiceRequestDto {

    private Long masterServiceId;

    @NotBlank(message = "Category is required (e.g., Makeup, Hair, Nail)")
    private String category;

    @NotBlank(message = "Service name is required")
    private String serviceName;

    private String description;

    @NotNull(message = "Base price (core fee) is required")
    @DecimalMin(value = "0.0", message = "Base price must be >= 0")
    private BigDecimal basePrice; // Tiền công cốt lõi không thể bỏ

    @NotNull(message = "Estimated duration is required")
    @Min(value = 1, message = "Duration must be at least 1 minute")
    private Integer estimatedDurationMinutes;

    private String attributesJson; // e.g. {"brand_used": "MAC, Dior", "style": "Tự nhiên"}

    private List<ProviderServiceOptionDto> options; // Danh sách components & add-ons
}
