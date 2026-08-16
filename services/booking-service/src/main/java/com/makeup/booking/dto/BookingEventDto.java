package com.makeup.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Event DTO dùng cho truyền thông điệp bất đồng bộ qua Kafka (Event Bus).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingEventDto implements Serializable {
    private String eventType; // "BOOKING_REQUESTED", "BOOKING_ACCEPTED", "BOOKING_REJECTED"
    private String bookingId;
    private String bookingCode;
    
    private Long customerId;
    private String customerName;
    private String customerPhone;

    private Long muaId;
    private String muaName;

    private Long servicePackageId;
    private String serviceName;

    private Double customerLat;
    private Double customerLng;
    private String address;

    private Double distanceKm;
    private BigDecimal totalFee;
    private String paymentMethod; // "CASH", "E_WALLET", "MOMO"
    private String status;
    private String timestamp;
}
