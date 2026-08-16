package com.makeup.payment.dto;

import com.makeup.payment.enums.UserType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalRequestDto {
    private String userId;
    private UserType userType;
    private UUID bankAccountId;
    private BigDecimal amount;
}
