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
public class MuaServiceRequestDto {

    @NotBlank(message = "Category is required (e.g., BRIDAL, EVENT, STAGE, BASIC)")
    private String category;

    @NotBlank(message = "Service name is required")
    private String serviceName;

    private String description;

    @NotNull(message = "Base price is required")
    @DecimalMin(value = "0.0", message = "Price must be >= 0")
    private BigDecimal basePrice;

    @NotNull(message = "Estimated duration is required")
    @Min(value = 1, message = "Duration must be at least 1 minute")
    private Integer estimatedDurationMinutes;

    private List<String> subServices; // e.g. ["MAKEUP_FACE", "HAIR_STYLING", "EYELASH"]
}
