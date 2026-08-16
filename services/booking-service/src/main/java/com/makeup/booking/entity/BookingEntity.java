package com.makeup.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings", schema = "public")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingEntity {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "booking_code", nullable = false, unique = true)
    private String bookingCode;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "mua_id")
    private String muaId;

    @Column(name = "service_id", nullable = false)
    private String serviceId;

    @Column(name = "base_fee", nullable = false, precision = 38, scale = 2)
    private BigDecimal baseFee;

    @Column(name = "travel_fee", nullable = false, precision = 38, scale = 2)
    private BigDecimal travelFee;

    @Column(name = "total_fee", nullable = false, precision = 38, scale = 2)
    private BigDecimal totalFee;

    @Column(name = "surge_multiplier")
    private Double surgeMultiplier;

    @Column(name = "customer_address", nullable = false, columnDefinition = "TEXT")
    private String customerAddress;

    @Column(name = "latitude", nullable = false)
    private Double latitude;

    @Column(name = "longitude", nullable = false)
    private Double longitude;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
