package com.makeup.payment.controller;

import com.makeup.common.response.ApiResponse;
import com.makeup.payment.dto.*;
import com.makeup.payment.entity.LedgerEntryEntity;
import com.makeup.payment.entity.TransactionEntity;
import com.makeup.payment.entity.UserBankAccountEntity;
import com.makeup.payment.entity.WithdrawalRequestEntity;
import com.makeup.payment.enums.UserType;
import com.makeup.payment.service.VnpayPaymentService;
import com.makeup.payment.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller cung cấp các REST API giao tiếp với Frontend / API Gateway
 * cho toàn bộ hệ thống Thanh toán, Ví điện tử, Ví tạm giữ Escrow và Rút tiền.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final VnpayPaymentService vnpayPaymentService;

    /**
     * API 1: Lấy số dư ví của chính tài khoản đang đăng nhập (Khách hàng hoặc Thợ makeup).
     * 
     * GET /api/v1/wallets/me/balance?userType=CUSTOMER
     */
    @GetMapping("/me/balance")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyWalletBalance(
            @AuthenticationPrincipal String currentUserId,
            @RequestParam(defaultValue = "CUSTOMER") UserType userType) {

        String userId = (currentUserId != null && !currentUserId.isBlank()) ? currentUserId : "guest-user";
        BigDecimal balance = walletService.getWalletBalance(userId, userType);

        Map<String, Object> data = Map.of(
                "userId", userId,
                "userType", userType,
                "balance", balance,
                "currency", "VND"
        );
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * API 2: Lấy số dư ví theo userId và loại ví cụ thể.
     * 
     * GET /api/v1/wallets/user/{userId}/balance?userType=WORKER
     */
    @GetMapping("/user/{userId}/balance")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWalletBalance(
            @PathVariable String userId,
            @RequestParam(defaultValue = "CUSTOMER") UserType userType) {

        BigDecimal balance = walletService.getWalletBalance(userId, userType);
        Map<String, Object> data = Map.of(
                "userId", userId,
                "userType", userType,
                "balance", balance,
                "currency", "VND"
        );
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    /**
     * API 3: Truy vấn lịch sử bút toán sổ cái (Double-Entry Financial Ledger) của một Ví.
     * Phục vụ cho tính năng Xem lịch sử biến động số dư / Đối soát minh bạch.
     * 
     * GET /api/v1/wallets/user/{userId}/ledger?userType=CUSTOMER
     */
    @GetMapping("/user/{userId}/ledger")
    public ResponseEntity<ApiResponse<List<LedgerEntryEntity>>> getWalletLedger(
            @PathVariable String userId,
            @RequestParam(defaultValue = "CUSTOMER") UserType userType) {

        List<LedgerEntryEntity> ledgerList = walletService.getWalletLedgerHistory(userId, userType);
        return ResponseEntity.ok(ApiResponse.success(ledgerList));
    }

    /**
     * API 4: Khởi tạo liên kết Nạp tiền vào ví qua Cổng thanh toán VNPay Sandbox.
     * POST /api/v1/wallets/top-up/vnpay
     */
    @PostMapping("/top-up/vnpay")
    public ResponseEntity<ApiResponse<Map<String, Object>>> initiateVnpayTopUp(
            @RequestBody TopUpRequestDto request,
            HttpServletRequest httpServletRequest) {
        String custId = (request != null && request.getCustomerId() != null && !request.getCustomerId().isBlank()) ? request.getCustomerId() : "1";
        long amount = (request != null && request.getAmount() != null) ? request.getAmount().longValue() : 100000L;
        String clientIp = httpServletRequest != null ? httpServletRequest.getRemoteAddr() : "127.0.0.1";

        log.info(">>> API: Khởi tạo nạp tiền VNPay cho Khách [{}] số tiền [{}]", custId, amount);
        String paymentUrl = vnpayPaymentService.createVnpayPaymentUrl(custId, amount, clientIp);
        Map<String, Object> data = Map.of("paymentUrl", paymentUrl);
        return ResponseEntity.ok(ApiResponse.success(data, "Tạo liên kết nạp tiền VNPay thành công"));
    }

    /**
     * API Test trực tiếp VNPay qua trình duyệt (GET)
     * GET /api/v1/wallets/test-vnpay?customerId=1&amount=50000
     */
    @GetMapping("/test-vnpay")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testVnpayTopUp(
            @RequestParam(defaultValue = "1") String customerId,
            @RequestParam(defaultValue = "50000") long amount,
            HttpServletRequest httpServletRequest) {
        String clientIp = httpServletRequest != null ? httpServletRequest.getRemoteAddr() : "127.0.0.1";
        log.info(">>> TEST API: Test nạp tiền VNPay cho Khách [{}] số tiền [{}]", customerId, amount);
        String paymentUrl = vnpayPaymentService.createVnpayPaymentUrl(customerId, amount, clientIp);
        Map<String, Object> data = Map.of("paymentUrl", paymentUrl);
        return ResponseEntity.ok(ApiResponse.success(data, "Test liên kết nạp tiền VNPay thành công"));
    }

    /**
     * API Webhook Callback IPN từ Cổng thanh toán VNPay.
     * GET & POST /api/v1/wallets/webhook/vnpay
     */
    @GetMapping("/webhook/vnpay")
    public ResponseEntity<Map<String, String>> handleVnpayIpnGet(@RequestParam Map<String, String> params) {
        return processVnpayIpn(params);
    }

    @PostMapping("/webhook/vnpay")
    public ResponseEntity<Map<String, String>> handleVnpayIpnPost(@RequestParam Map<String, String> params) {
        return processVnpayIpn(params);
    }

    private ResponseEntity<Map<String, String>> processVnpayIpn(Map<String, String> params) {
        log.info(">>> API: Đã nhận VNPay IPN Callback Params: {}", params);
        boolean isValid = vnpayPaymentService.verifyVnpaySignature(params);
        if (!isValid) {
            log.error(">>> [ERROR] Chữ ký VNPay IPN không hợp lệ!");
            return ResponseEntity.ok(Map.of("RspCode", "97", "Message", "Invalid Checksum"));
        }

        String vnpResponseCode = params.get("vnp_ResponseCode");
        String vnpTxnRef = params.get("vnp_TxnRef");
        String vnpAmountStr = params.get("vnp_Amount");
        String vnpOrderInfo = params.getOrDefault("vnp_OrderInfo", "");

        if ("00".equals(vnpResponseCode)) {
            String customerId = "1";
            if (vnpOrderInfo.contains("khach hang ")) {
                customerId = vnpOrderInfo.split("khach hang ")[1].trim();
            }
            long amountVal = (vnpAmountStr != null && !vnpAmountStr.isBlank()) ? (Long.parseLong(vnpAmountStr) / 100L) : 0L;
            BigDecimal amount = BigDecimal.valueOf(amountVal);
            String idempotencyKey = "TOPUP_VNPAY_" + vnpTxnRef;

            walletService.topUpWallet(customerId, amount, vnpTxnRef, idempotencyKey);
            log.info(">>> VNPay Webhook IPN xử lý cộng tiền ví cho khách [{}] thành công", customerId);
            return ResponseEntity.ok(Map.of("RspCode", "00", "Message", "Confirm Success"));
        }

        return ResponseEntity.ok(Map.of("RspCode", "00", "Message", "Confirm Success"));
    }

    /**
     * API 6: Tạm giữ tiền đơn hàng (Escrow Hold). Trừ Ví Khách -> Cộng Ví Tạm Giữ Sàn.
     * 
     * POST /api/v1/wallets/escrow/hold
     */
    @PostMapping("/escrow/hold")
    public ResponseEntity<ApiResponse<TransactionEntity>> escrowHold(@RequestBody EscrowRequestDto request) {
        TransactionEntity tx = walletService.escrowHold(
                request.getBookingId(),
                request.getCustomerId(),
                request.getAmount(),
                request.getPaymentMethod() != null ? request.getPaymentMethod() : "E_WALLET"
        );
        return ResponseEntity.ok(ApiResponse.success(tx, "Tạm giữ tiền đơn hàng thành công"));
    }

    /**
     * API 7: Giải phóng tiền khi đơn COMPLETED (Escrow Release).
     * 
     * TH1 (VÍ ĐIỆN TỬ): Trừ Ví Tạm Giữ -> Cộng Ví Doanh Thu Sàn (15% chiết khấu) + Cộng Ví Thợ (85% tiền công).
     * TH2 (TIỀN MẶT): Khách trả tiền mặt cho Thợ. Trừ 15% Phí Sàn từ Ví Thợ -> Cộng Ví Doanh Thu Sàn.
     * 
     * POST /api/v1/wallets/escrow/release
     */
    @PostMapping("/escrow/release")
    public ResponseEntity<ApiResponse<TransactionEntity>> escrowRelease(@RequestBody EscrowRequestDto request) {
        TransactionEntity tx = walletService.escrowRelease(
                request.getBookingId(),
                request.getCustomerId(),
                request.getWorkerId(),
                request.getAmount(),
                request.getPaymentMethod() != null ? request.getPaymentMethod() : "E_WALLET"
        );
        return ResponseEntity.ok(ApiResponse.success(tx, "Giải phóng tiền đơn hàng thành công"));
    }

    /**
     * API 8: Hoàn tiền khi đơn bị HỦY (Escrow Refund). Trừ Ví Tạm Giữ -> Hoàn Ví Khách.
     * 
     * POST /api/v1/wallets/escrow/refund
     */
    @PostMapping("/escrow/refund")
    public ResponseEntity<ApiResponse<TransactionEntity>> escrowRefund(@RequestBody EscrowRequestDto request) {
        TransactionEntity tx = walletService.escrowRefund(
                request.getBookingId(),
                request.getCustomerId(),
                request.getAmount(),
                request.getPaymentMethod() != null ? request.getPaymentMethod() : "E_WALLET"
        );
        return ResponseEntity.ok(ApiResponse.success(tx, "Hoàn tiền đơn hàng thành công"));
    }

    /**
     * API 9: Liên kết Tài khoản ngân hàng rút tiền.
     * 
     * POST /api/v1/wallets/bank-accounts
     */
    @PostMapping("/bank-accounts")
    public ResponseEntity<ApiResponse<UserBankAccountEntity>> addBankAccount(@RequestBody BankAccountDto dto) {
        UserBankAccountEntity entity = walletService.addBankAccount(dto.getUserId(), dto.getBankCode(), dto.getAccountNumber(), dto.getAccountName());
        return ResponseEntity.ok(ApiResponse.success(entity, "Thêm tài khoản ngân hàng thành công"));
    }

    /**
     * API 10: Lấy danh sách ngân hàng liên kết của user.
     * 
     * GET /api/v1/wallets/bank-accounts/user/{userId}
     */
    @GetMapping("/bank-accounts/user/{userId}")
    public ResponseEntity<ApiResponse<List<UserBankAccountEntity>>> getUserBankAccounts(@PathVariable String userId) {
        List<UserBankAccountEntity> list = walletService.getUserBankAccounts(userId);
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    /**
     * API 11: Tạo yêu cầu Rút tiền về Ngân hàng (Payout).
     * 
     * POST /api/v1/wallets/withdraw
     */
    @PostMapping("/withdraw")
    public ResponseEntity<ApiResponse<WithdrawalRequestEntity>> withdrawMoney(@RequestBody WithdrawalRequestDto dto) {
        WithdrawalRequestEntity entity = walletService.requestWithdrawal(
                dto.getUserId(),
                dto.getUserType(),
                dto.getBankAccountId(),
                dto.getAmount()
        );
        return ResponseEntity.ok(ApiResponse.success(entity, "Tạo yêu cầu rút tiền thành công"));
    }
}
