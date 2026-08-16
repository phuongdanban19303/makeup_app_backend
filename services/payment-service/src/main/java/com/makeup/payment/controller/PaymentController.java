package com.makeup.payment.controller;

import com.makeup.common.response.ApiResponse;
import com.makeup.payment.dto.PaymentTransactionDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    @PostMapping("/process")
    public ResponseEntity<ApiResponse<PaymentTransactionDto>> processPayment(@RequestBody PaymentTransactionDto request) {
        request.setTransactionId(UUID.randomUUID().toString());
        request.setStatus("SUCCESS");
        request.setTimestamp(System.currentTimeMillis());
        return ResponseEntity.ok(ApiResponse.success(request, "Payment processed successfully"));
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<ApiResponse<PaymentTransactionDto>> getTransactionDetails(@PathVariable String transactionId) {
        PaymentTransactionDto dto = PaymentTransactionDto.builder()
                .transactionId(transactionId)
                .bookingId(UUID.randomUUID().toString())
                .customerId("cust-123")
                .muaId("mua-456")
                .amount(350000.0)
                .paymentMethod("E_WALLET")
                .status("SUCCESS")
                .timestamp(System.currentTimeMillis())
                .build();

        return ResponseEntity.ok(ApiResponse.success(dto));
    }
}
