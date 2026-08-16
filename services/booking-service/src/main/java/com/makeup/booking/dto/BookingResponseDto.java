package com.makeup.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO phản hồi thông tin đơn đặt lịch sau khi xử lý ghép đơn.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponseDto {
    private String id;
    private String bookingCode;

    private Long customerId;
    private String customerName;

    private Long muaId;
    private String muaName;

    private Long servicePackageId;
    private String serviceName;

    private Double customerLat;
    private Double customerLng;
    private String address;

    private BigDecimal basePrice;
    private Double movingDistanceKm;
    private BigDecimal movingFee;
    private Double surgeMultiplier;
    private BigDecimal totalFee;

    private String status;
    private Instant createdAt;
}
