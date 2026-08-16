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
@Table(name = "wallet_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String walletId;

    @Column(nullable = false)
    private String transactionType; // DEPOSIT, WITHDRAWAL, BOOKING_PAYOUT, COMMISSION_DEDUCTION

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private BigDecimal balanceAfter;

    private String referenceId; // bookingId or transactionId

    @CreationTimestamp
    private LocalDateTime createdAt;
}
