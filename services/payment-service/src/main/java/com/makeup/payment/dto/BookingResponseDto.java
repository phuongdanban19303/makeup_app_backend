package com.makeup.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponseDto {
    private String bookingId;
    private String bookingCode;
    private Long customerId;
    private String customerName;
    private Long muaId;
    private String muaName;
    private BigDecimal totalFee;
    private String status;
}
