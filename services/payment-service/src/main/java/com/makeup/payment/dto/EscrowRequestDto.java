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
public class EscrowRequestDto {
    private String bookingId;
    private String customerId;
    private String workerId;
    private BigDecimal amount;
    private String paymentMethod; // "CASH", "E_WALLET", "VNPAY"
}
