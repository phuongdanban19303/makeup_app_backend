package com.makeup.user.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotBlank(message = "Option type is required ('COMPONENT' or 'ADD_ON')")
    private String optionType; // 'COMPONENT' hoặc 'ADD_ON'

    @NotBlank(message = "Option name is required")
    private String optionName; // e.g. "Đánh Kem Nền", "Che Khuyết Điểm", "Làm Tóc Cô Dâu"

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", message = "Price must be >= 0")
    private BigDecimal price; // e.g. 100000.0

    @NotNull(message = "isDefault flag is required")
    private Boolean isDefault; // true nếu mặc định có sẵn, false nếu add-on

    @NotNull(message = "isRemovable flag is required")
    private Boolean isRemovable; // true nếu cho phép khách bỏ, false nếu bắt buộc
}
