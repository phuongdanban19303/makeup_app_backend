package com.makeup.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String bookingId;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String muaId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private BigDecimal platformCommissionFee;

    @Column(nullable = false)
    private BigDecimal muaNetPayout;

    @Column(nullable = false)
    private String paymentMethod; // E_WALLET, CREDIT_CARD, CASH

    @Column(nullable = false)
    private String status;        // PENDING, SUCCESS, FAILED

    @CreationTimestamp
    private LocalDateTime createdAt;
}
