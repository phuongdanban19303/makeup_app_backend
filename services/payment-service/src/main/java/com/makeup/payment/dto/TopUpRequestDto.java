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
public class TopUpRequestDto {
    private String customerId;
    private BigDecimal amount;
    private String paymentMethod; // "VNPAY", "E_WALLET", "PAYOS"
}
