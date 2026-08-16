package com.makeup.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO chứa thông tin yêu cầu tạo đơn đặt lịch từ Khách hàng.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingCreateRequestDto {
    private Long customerId;
    private String customerName;
    private String customerPhone;

    private Long muaId;
    private String muaName;

    private Long servicePackageId;
    private String serviceCategory;
    private String serviceName;
    private Double basePackageFee;
    private Double optionsFee;
    private java.util.List<Object> selectedOptions;

    private Double customerLat;
    private Double customerLng;
    private String address;

    private String notes;
    private String paymentMethod; // "CASH", "E_WALLET", "MOMO"
}
