package com.makeup.payment.config;

import com.makeup.payment.entity.WalletEntity;
import com.makeup.payment.enums.UserType;
import com.makeup.payment.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Component khởi tạo Ví Hệ Thống khi ứng dụng `payment-service` vừa khởi động hoàn tất.
 * Tự động kiểm tra và tạo 2 loại Ví đặc biệt:
 * 1. Ví Tạm Giữ Trung Gian (`SYSTEM_ESCROW`): Nơi trung chuyển tiền từ lúc khách đặt đơn đến lúc thợ hoàn thành ca.
 * 2. Ví Doanh Thu Sàn (`SYSTEM_REVENUE`): Nơi tích lũy 15% phí chiết khấu hoa hồng của sàn dịch vụ.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemWalletInitializer {

    private final WalletRepository walletRepository;

    @Value("${payment.system.escrow-user-id:00000000-0000-0000-0000-000000000001}")
    private String escrowUserId;

    @Value("${payment.system.revenue-user-id:00000000-0000-0000-0000-000000000002}")
    private String revenueUserId;

    /**
     * Tự động chạy ngay khi Spring Boot khởi động xong (ApplicationReadyEvent).
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeSystemWallets() {
        log.info(">>> Đang kiểm tra và khởi tạo các Ví Hệ Thống (Escrow & Revenue)...");
        
        // BƯỚC 1: Kiểm tra và khởi tạo Ví Tạm Giữ Trung Gian (SYSTEM_ESCROW)
        walletRepository.findByUserIdAndUserType(escrowUserId, UserType.SYSTEM_ESCROW)
                .orElseGet(() -> {
                    log.info(">>> Tạo mới Ví Tạm Giữ Hệ Thống (SYSTEM_ESCROW) với ID: {}", escrowUserId);
                    return walletRepository.save(WalletEntity.builder()
                            .userId(escrowUserId)
                            .userType(UserType.SYSTEM_ESCROW)
                            .balance(BigDecimal.ZERO)
                            .currency("VND")
                            .status("ACTIVE")
                            .build());
                });

        // BƯỚC 2: Kiểm tra và khởi tạo Ví Doanh Thu Sàn (SYSTEM_REVENUE)
        walletRepository.findByUserIdAndUserType(revenueUserId, UserType.SYSTEM_REVENUE)
                .orElseGet(() -> {
                    log.info(">>> Tạo mới Ví Doanh Thu Sàn (SYSTEM_REVENUE) với ID: {}", revenueUserId);
                    return walletRepository.save(WalletEntity.builder()
                            .userId(revenueUserId)
                            .userType(UserType.SYSTEM_REVENUE)
                            .balance(BigDecimal.ZERO)
                            .currency("VND")
                            .status("ACTIVE")
                            .build());
                });
        
        log.info(">>> Khởi tạo Ví Hệ Thống hoàn tất thành công.");
    }
}
