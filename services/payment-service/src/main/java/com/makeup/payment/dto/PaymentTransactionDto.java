package com.makeup.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransactionDto {
    private String transactionId;
    private String bookingId;
    private String customerId;
    private String muaId;
    private double amount;
    private String paymentMethod; // "E_WALLET", "CREDIT_CARD", "CASH"
    private String status;        // "PENDING", "SUCCESS", "FAILED"
    private long timestamp;
}
