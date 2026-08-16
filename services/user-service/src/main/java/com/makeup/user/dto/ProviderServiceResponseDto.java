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
public class ProviderServiceResponseDto {

    private Long id;
    private Long providerId;
    private Long masterServiceId;
    private String category;
    private String serviceName;
    private String description;
    private BigDecimal basePrice;          // Tiền công cốt lõi không thể bỏ
    private BigDecimal defaultTotalPrice;   // basePrice + SUM(options default) -> Tiền hiển thị xem trước
    private Integer estimatedDurationMinutes;
    private String attributesJson;
    private Boolean isActive;
    private List<ProviderServiceOptionDto> options;
    private List<String> subServices;
    private ZonedDateTime createdAt;

    public List<String> getSubServices() {
        if (subServices != null && !subServices.isEmpty()) {
            return subServices;
        }
        if (options != null && !options.isEmpty()) {
            return options.stream().map(ProviderServiceOptionDto::getOptionName).toList();
        }
        return List.of();
    }
}
