package com.makeup.payment.service;

import com.makeup.payment.entity.*;
import com.makeup.payment.enums.*;
import com.makeup.payment.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Service trung tâm quản lý Ví điện tử, Ví Tạm giữ (Escrow) và Sổ cái kế toán
 * kép (Double-Entry Ledger).
 * Tất cả các thao tác cộng/trừ tiền đều được bao bọc trong DB Transaction
 * (ACID)
 * và sử dụng Pessimistic Locking (SELECT FOR UPDATE) để chống xung đột số dư
 * (Race Condition).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final UserBankAccountRepository userBankAccountRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;

    // Tỷ lệ chiết khấu sàn (VD: 0.15 = 15%), có thể thay đổi linh hoạt từ
    // application.yml
    @Value("${payment.commission.rate:0.15}")
    private double commissionRate;

    // ID đặc biệt định danh Ví Tạm Giữ Trung Gian của Hệ Thống
    @Value("${payment.system.escrow-user-id:00000000-0000-0000-0000-000000000001}")
    private String escrowUserId;

    // ID đặc biệt định danh Ví Doanh Thu Sàn của Hệ Thống
    @Value("${payment.system.revenue-user-id:00000000-0000-0000-0000-000000000002}")
    private String revenueUserId;

    /**
     * BƯỚC PHỤ TRỢ: Lấy hoặc tự động tạo Ví mới nếu chưa tồn tại trong CSDL.
     * 
     * @param userId   ID người dùng hoặc ID ví hệ thống
     * @param userType Loại ví ('CUSTOMER', 'WORKER', 'SYSTEM_ESCROW',
     *                 'SYSTEM_REVENUE')
     * @return WalletEntity đối tượng ví đã tìm thấy hoặc vừa tạo mới
     */
    @Transactional
    public WalletEntity getOrCreateWallet(String userId, UserType userType) {
        return walletRepository.findByUserIdAndUserType(userId, userType)
                .orElseGet(() -> walletRepository.save(WalletEntity.builder()
                        .userId(userId)
                        .userType(userType)
                        .balance(BigDecimal.ZERO)
                        .currency("VND")
                        .status("ACTIVE")
                        .build()));
    }

    /**
     * BƯỚC PHỤ TRỢ: Lấy nhanh số dư khả dụng của một Ví.
     */
    @Transactional(readOnly = true)
    public BigDecimal getWalletBalance(String userId, UserType userType) {
        return walletRepository.findByUserIdAndUserType(userId, userType)
                .map(WalletEntity::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    /**
     * KỊCH BẢN 1: Nạp tiền vào ví Khách (Top-up).
     * -------------------------------------------------------------
     * Luồng xử lý:
     * 1. Kiểm tra Idempotency key (Chống nạp tiền lặp lại 2 lần do lỗi retry mạng).
     * 2. Mở DB Transaction & Khóa Ví Khách (SELECT FOR UPDATE).
     * 3. Tạo bản ghi giao dịch Header (transactions).
     * 4. Cộng tiền vào số dư Ví Khách.
     * 5. Ghi sổ cái (ledger_entries) dòng bút toán CREDIT (Có).
     */
    @Transactional
    public TransactionEntity topUpWallet(String customerId, BigDecimal amount, String referenceId,
            String idempotencyKey) {
        log.info(">>> Processing TOPUP for customerId [{}], amount [{}], key [{}]", customerId, amount, idempotencyKey);

        // BƯỚC 1: Kiểm tra chống lặp giao dịch (Idempotency Check)
        Optional<TransactionEntity> existingTx = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTx.isPresent()) {
            log.info(">>> [IDEMPOTENCY] Transaction key [{}] đã được xử lý trước đó, bỏ qua.", idempotencyKey);
            return existingTx.get();
        }

        // BƯỚC 2: Tìm ví khách hàng và thực hiện Pessimistic Lock (Khóa dòng trong DB
        // để không ai sửa cùng lúc)
        WalletEntity custWallet = getOrCreateWallet(customerId, UserType.CUSTOMER);
        List<WalletEntity> lockedWallets = walletRepository
                .findAllByIdInOrderByIdAscForUpdate(List.of(custWallet.getId()));
        WalletEntity lockedCustWallet = lockedWallets.get(0);

        // BƯỚC 3: Tạo giao dịch tổng (Transaction Header)
        TransactionEntity tx = transactionRepository.save(TransactionEntity.builder()
                .referenceId(referenceId)
                .transactionType(TransactionType.TOPUP)
                .totalAmount(amount)
                .feeAmount(BigDecimal.ZERO)
                .status(TransactionStatus.SUCCESS)
                .idempotencyKey(idempotencyKey)
                .build());

        // BƯỚC 4: Cộng số dư Ví Khách
        BigDecimal newBalance = lockedCustWallet.getBalance().add(amount);
        lockedCustWallet.setBalance(newBalance);
        walletRepository.save(lockedCustWallet);

        // BƯỚC 5: Ghi bút toán Sổ cái Kế toán: CREDIT Ví Khách
        ledgerEntryRepository.save(LedgerEntryEntity.builder()
                .transactionId(tx.getId())
                .walletId(lockedCustWallet.getId())
                .entryType(EntryType.CREDIT) // CREDIT = Cộng tiền vào tài khoản
                .amount(amount)
                .balanceAfter(newBalance)
                .description("Nap tien vao vi khach qua cong thanh toan #" + referenceId)
                .build());

        log.info(">>> SUCCESS TOPUP: Customer [{}] new balance [{}]", customerId, newBalance);
        return tx;
    }

    /**
     * KỊCH BẢN 2: Đặt đơn & Tạm giữ tiền (Escrow Hold).
     * -------------------------------------------------------------
     * Luồng xử lý:
     * 1. Kiểm tra Idempotency key ("ESCROW_HOLD_" + orderId).
     * 2. Khóa đồng thời 2 ví: Ví Khách và Ví Tạm Giữ Sàn (SYSTEM_ESCROW) theo thứ
     * tự ID TĂNG DẦN (Tránh Deadlock).
     * 3. Kiểm tra số dư Ví Khách (Phải >= số tiền đơn hàng).
     * 4. Trừ tiền Ví Khách (DEBIT) -> Ghi sổ cái DEBIT.
     * 5. Cộng tiền Ví Tạm Giữ Sàn (CREDIT) -> Ghi sổ cái CREDIT.
     */
    @Transactional
    public TransactionEntity escrowHold(String orderId, String customerId, BigDecimal amount) {
        return escrowHold(orderId, customerId, amount, "E_WALLET");
    }

    /**
     * KỊCH BẢN 2: Đặt đơn & Tạm giữ tiền (Escrow Hold).
     * Hỗ trợ 2 phương thức thanh toán: Tiền mặt (CASH) và Ví điện tử (E_WALLET / MOMO).
     */
    @Transactional
    public TransactionEntity escrowHold(String orderId, String customerId, BigDecimal amount, String paymentMethod) {
        String idempotencyKey = "ESCROW_HOLD_" + orderId;
        log.info(">>> Processing ESCROW_HOLD for orderId [{}], customerId [{}], amount [{}], method [{}]",
                orderId, customerId, amount, paymentMethod);

        // BƯỚC 1: Chống lặp giao dịch tạm giữ tiền
        Optional<TransactionEntity> existingTx = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTx.isPresent()) {
            log.info(">>> [IDEMPOTENCY] Escrow Hold [{}] đã tồn tại, trả về kết quả cũ.", idempotencyKey);
            return existingTx.get();
        }

        // BƯỚC 2: Nếu là TIỀN MẶT (CASH) -> Không cần tạm giữ tiền từ Ví Khách hàng!
        if ("CASH".equalsIgnoreCase(paymentMethod)) {
            log.info(">>> [CASH] Đơn hàng #{}: Phương thức TIỀN MẶT. Bỏ qua tạm giữ Ví Khách hàng.", orderId);
            return transactionRepository.save(TransactionEntity.builder()
                    .referenceId(orderId)
                    .transactionType(TransactionType.ESCROW_HOLD)
                    .totalAmount(amount)
                    .feeAmount(BigDecimal.ZERO)
                    .status(TransactionStatus.SUCCESS)
                    .idempotencyKey(idempotencyKey)
                    .errorMessage("CASH_PAYMENT_NO_ESCROW_HOLD_REQUIRED")
                    .build());
        }

        // BƯỚC 3: Nếu là VÍ ĐIỆN TỬ -> Lấy thông tin Ví Khách và Ví Tạm Giữ Trung Gian
        WalletEntity custWallet = getOrCreateWallet(customerId, UserType.CUSTOMER);
        WalletEntity escrowWallet = getOrCreateWallet(escrowUserId, UserType.SYSTEM_ESCROW);

        // QUAN TRỌNG: Khóa các dòng trong CSDL theo thứ tự UUID tăng dần (ORDER BY id ASC FOR UPDATE)
        List<UUID> walletIds = Arrays.asList(custWallet.getId(), escrowWallet.getId());
        List<WalletEntity> lockedWallets = walletRepository.findAllByIdInOrderByIdAscForUpdate(walletIds);

        Map<UUID, WalletEntity> walletMap = new HashMap<>();
        for (WalletEntity w : lockedWallets) {
            walletMap.put(w.getId(), w);
        }

        WalletEntity lockedCust = walletMap.get(custWallet.getId());
        WalletEntity lockedEscrow = walletMap.get(escrowWallet.getId());

        // Kiểm tra số dư ví khả dụng của Khách
        if (lockedCust.getBalance().compareTo(amount) < 0) {
            log.error(">>> [ERROR] Customer [{}] số dư [{}] không đủ để tạm giữ [{}]", customerId, lockedCust.getBalance(), amount);
            throw new IllegalStateException("Số dư Ví Khách không đủ để tạm giữ đơn hàng #" + orderId);
        }

        TransactionEntity tx = transactionRepository.save(TransactionEntity.builder()
                .referenceId(orderId)
                .transactionType(TransactionType.ESCROW_HOLD)
                .totalAmount(amount)
                .feeAmount(BigDecimal.ZERO)
                .status(TransactionStatus.SUCCESS)
                .idempotencyKey(idempotencyKey)
                .build());

        // Trừ Ví Khách (DEBIT)
        BigDecimal custBalanceAfter = lockedCust.getBalance().subtract(amount);
        lockedCust.setBalance(custBalanceAfter);
        walletRepository.save(lockedCust);

        ledgerEntryRepository.save(LedgerEntryEntity.builder()
                .transactionId(tx.getId())
                .walletId(lockedCust.getId())
                .entryType(EntryType.DEBIT)
                .amount(amount)
                .balanceAfter(custBalanceAfter)
                .description("Tam giu tien don hang #" + orderId)
                .build());

        // Cộng Ví Tạm Giữ Sàn (CREDIT)
        BigDecimal escrowBalanceAfter = lockedEscrow.getBalance().add(amount);
        lockedEscrow.setBalance(escrowBalanceAfter);
        walletRepository.save(lockedEscrow);

        ledgerEntryRepository.save(LedgerEntryEntity.builder()
                .transactionId(tx.getId())
                .walletId(lockedEscrow.getId())
                .entryType(EntryType.CREDIT)
                .amount(amount)
                .balanceAfter(escrowBalanceAfter)
                .description("Nhan tam giu tien don hang #" + orderId)
                .build());

        log.info(">>> SUCCESS ESCROW_HOLD for order [{}]", orderId);
        return tx;
    }

    /**
     * KỊCH BẢN 3: Đơn COMPLETED & Giải phóng tiền (Escrow Release).
     */
    @Transactional
    public TransactionEntity escrowRelease(String orderId, String customerId, String workerId, BigDecimal totalAmount) {
        return escrowRelease(orderId, customerId, workerId, totalAmount, "E_WALLET");
    }

    /**
     * KỊCH BẢN 3: Đơn COMPLETED & Giải phóng tiền / Trừ chiết khấu sàn (Escrow Release).
     * 
     * TH1 (VÍ ĐIỆN TỬ): Trừ 100% Ví Tạm Giữ -> Cộng 15% Ví Doanh Thu Sàn -> Cộng 85% Ví Thợ.
     * TH2 (TIỀN MẶT): Khách trả 100% tiền mặt cho Thợ. Hệ thống trừ 15% Phí Sàn trực tiếp từ Ví Thợ -> Cộng Ví Doanh Thu Sàn.
     */
    @Transactional
    public TransactionEntity escrowRelease(String orderId, String customerId, String workerId, BigDecimal totalAmount, String paymentMethod) {
        String idempotencyKey = "ESCROW_RELEASE_" + orderId;
        log.info(">>> Processing ESCROW_RELEASE for orderId [{}], workerId [{}], totalAmount [{}], method [{}]",
                orderId, workerId, totalAmount, paymentMethod);

        Optional<TransactionEntity> existingTx = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTx.isPresent()) {
            log.info(">>> [IDEMPOTENCY] Escrow Release [{}] đã được xử lý trước đó.", idempotencyKey);
            return existingTx.get();
        }

        BigDecimal platformFee = totalAmount.multiply(BigDecimal.valueOf(commissionRate)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal workerNet = totalAmount.subtract(platformFee);

        // =========================================================================
        // TRƯỜNG HỢP A: KHÁCH TRẢ TIỀN MẶT (CASH)
        // Thợ nhận 100% tiền mặt từ Khách. Hệ thống khấu trừ 15% phí sàn từ Ví Thợ!
        // =========================================================================
        if ("CASH".equalsIgnoreCase(paymentMethod)) {
            log.info(">>> [CASH RELEASE] Đơn #{}: Khách trả tiền mặt [{}]. Khấu trừ [{}] phí sàn từ Ví Thợ [{}]",
                    orderId, totalAmount, platformFee, workerId);

            WalletEntity revenueWallet = getOrCreateWallet(revenueUserId, UserType.SYSTEM_REVENUE);
            WalletEntity workerWallet = getOrCreateWallet(workerId, UserType.WORKER);

            List<UUID> walletIds = Arrays.asList(revenueWallet.getId(), workerWallet.getId());
            List<WalletEntity> lockedWallets = walletRepository.findAllByIdInOrderByIdAscForUpdate(walletIds);

            Map<UUID, WalletEntity> walletMap = new HashMap<>();
            for (WalletEntity w : lockedWallets) {
                walletMap.put(w.getId(), w);
            }

            WalletEntity lockedRevenue = walletMap.get(revenueWallet.getId());
            WalletEntity lockedWorker = walletMap.get(workerWallet.getId());

            TransactionEntity tx = transactionRepository.save(TransactionEntity.builder()
                    .referenceId(orderId)
                    .transactionType(TransactionType.ESCROW_RELEASE)
                    .totalAmount(totalAmount)
                    .feeAmount(platformFee)
                    .status(TransactionStatus.SUCCESS)
                    .idempotencyKey(idempotencyKey)
                    .errorMessage("CASH_PAYMENT_COMMISSION_DEDUCTED")
                    .build());

            // 1. Trừ Ví Thợ: -15% platformFee (DEBIT)
            BigDecimal workerBalAfter = lockedWorker.getBalance().subtract(platformFee);
            lockedWorker.setBalance(workerBalAfter);
            walletRepository.save(lockedWorker);

            ledgerEntryRepository.save(LedgerEntryEntity.builder()
                    .transactionId(tx.getId())
                    .walletId(lockedWorker.getId())
                    .entryType(EntryType.DEBIT)
                    .amount(platformFee)
                    .balanceAfter(workerBalAfter)
                    .description("Khau tru 15% phi san don tien mat #" + orderId)
                    .build());

            // 2. Cộng Ví Doanh Thu Sàn: +15% platformFee (CREDIT)
            BigDecimal revenueBalAfter = lockedRevenue.getBalance().add(platformFee);
            lockedRevenue.setBalance(revenueBalAfter);
            walletRepository.save(lockedRevenue);

            ledgerEntryRepository.save(LedgerEntryEntity.builder()
                    .transactionId(tx.getId())
                    .walletId(lockedRevenue.getId())
                    .entryType(EntryType.CREDIT)
                    .amount(platformFee)
                    .balanceAfter(revenueBalAfter)
                    .description("Doanh thu phi san don tien mat #" + orderId)
                    .build());

            log.info(">>> SUCCESS CASH ESCROW_RELEASE for order [{}]: Platform Fee [{}] deducted from Worker [{}]", orderId, platformFee, workerId);
            return tx;
        }

        // =========================================================================
        // TRƯỜNG HỢP B: KHÁCH TRẢ BẰNG VÍ ĐIỆN TỬ (E_WALLET / MOMO)
        // =========================================================================
        WalletEntity escrowWallet = getOrCreateWallet(escrowUserId, UserType.SYSTEM_ESCROW);
        WalletEntity revenueWallet = getOrCreateWallet(revenueUserId, UserType.SYSTEM_REVENUE);
        WalletEntity workerWallet = getOrCreateWallet(workerId, UserType.WORKER);

        List<UUID> walletIds = Arrays.asList(escrowWallet.getId(), revenueWallet.getId(), workerWallet.getId());
        List<WalletEntity> lockedWallets = walletRepository.findAllByIdInOrderByIdAscForUpdate(walletIds);

        Map<UUID, WalletEntity> walletMap = new HashMap<>();
        for (WalletEntity w : lockedWallets) {
            walletMap.put(w.getId(), w);
        }

        WalletEntity lockedEscrow = walletMap.get(escrowWallet.getId());
        WalletEntity lockedRevenue = walletMap.get(revenueUserId != null ? revenueWallet.getId() : null);
        WalletEntity lockedWorker = walletMap.get(workerWallet.getId());

        TransactionEntity tx = transactionRepository.save(TransactionEntity.builder()
                .referenceId(orderId)
                .transactionType(TransactionType.ESCROW_RELEASE)
                .totalAmount(totalAmount)
                .feeAmount(platformFee)
                .status(TransactionStatus.SUCCESS)
                .idempotencyKey(idempotencyKey)
                .build());

        // 1. Trừ Ví Tạm Giữ (-100% totalAmount) -> DEBIT
        BigDecimal escrowBalAfter = lockedEscrow.getBalance().subtract(totalAmount);
        lockedEscrow.setBalance(escrowBalAfter);
        walletRepository.save(lockedEscrow);

        ledgerEntryRepository.save(LedgerEntryEntity.builder()
                .transactionId(tx.getId())
                .walletId(lockedEscrow.getId())
                .entryType(EntryType.DEBIT)
                .amount(totalAmount)
                .balanceAfter(escrowBalAfter)
                .description("Giai phong tien tam giu don #" + orderId)
                .build());

        // 2. Cộng Ví Doanh Thu Sàn (+15% platformFee) -> CREDIT
        BigDecimal revenueBalAfter = lockedRevenue.getBalance().add(platformFee);
        lockedRevenue.setBalance(revenueBalAfter);
        walletRepository.save(lockedRevenue);

        ledgerEntryRepository.save(LedgerEntryEntity.builder()
                .transactionId(tx.getId())
                .walletId(lockedRevenue.getId())
                .entryType(EntryType.CREDIT)
                .amount(platformFee)
                .balanceAfter(revenueBalAfter)
                .description("Doanh thu phi san don #" + orderId)
                .build());

        // 3. Cộng Ví Thợ (+85% workerNet) -> CREDIT
        BigDecimal workerBalAfter = lockedWorker.getBalance().add(workerNet);
        lockedWorker.setBalance(workerBalAfter);
        walletRepository.save(lockedWorker);

        ledgerEntryRepository.save(LedgerEntryEntity.builder()
                .transactionId(tx.getId())
                .walletId(lockedWorker.getId())
                .entryType(EntryType.CREDIT)
                .amount(workerNet)
                .balanceAfter(workerBalAfter)
                .description("Tien cong thuc nhan don #" + orderId)
                .build());

        log.info(">>> SUCCESS ESCROW_RELEASE for order [{}]: Platform Fee [{}], Worker Net [{}]", orderId, platformFee, workerNet);
        return tx;
    }

    /**
     * KỊCH BẢN 4: Hủy đơn & Hoàn tiền (Escrow Refund).
     */
    @Transactional
    public TransactionEntity escrowRefund(String orderId, String customerId, BigDecimal amount) {
        return escrowRefund(orderId, customerId, amount, "E_WALLET");
    }

    /**
     * KỊCH BẢN 4: Hủy đơn & Hoàn tiền (Escrow Refund).
     */
    @Transactional
    public TransactionEntity escrowRefund(String orderId, String customerId, BigDecimal amount, String paymentMethod) {
        String idempotencyKey = "ESCROW_REFUND_" + orderId;
        log.info(">>> Processing ESCROW_REFUND for orderId [{}], customerId [{}], amount [{}]", orderId, customerId,
                amount);

        // BƯỚC 1: Chống lặp hoàn tiền
        Optional<TransactionEntity> existingTx = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTx.isPresent()) {
            log.info(">>> [IDEMPOTENCY] Escrow Refund [{}] đã xử lý trước đó.", idempotencyKey);
            return existingTx.get();
        }

        // BƯỚC 2: Khóa Ví Tạm Giữ và Ví Khách
        WalletEntity escrowWallet = getOrCreateWallet(escrowUserId, UserType.SYSTEM_ESCROW);
        WalletEntity custWallet = getOrCreateWallet(customerId, UserType.CUSTOMER);

        List<UUID> walletIds = Arrays.asList(escrowWallet.getId(), custWallet.getId());
        List<WalletEntity> lockedWallets = walletRepository.findAllByIdInOrderByIdAscForUpdate(walletIds);

        Map<UUID, WalletEntity> walletMap = new HashMap<>();
        for (WalletEntity w : lockedWallets) {
            walletMap.put(w.getId(), w);
        }

        WalletEntity lockedEscrow = walletMap.get(escrowWallet.getId());
        WalletEntity lockedCust = walletMap.get(custWallet.getId());

        // BƯỚC 3: Tạo Transaction Header (ESCROW_REFUND)
        TransactionEntity tx = transactionRepository.save(TransactionEntity.builder()
                .referenceId(orderId)
                .transactionType(TransactionType.ESCROW_REFUND)
                .totalAmount(amount)
                .feeAmount(BigDecimal.ZERO)
                .status(TransactionStatus.SUCCESS)
                .idempotencyKey(idempotencyKey)
                .build());

        // BƯỚC 4: Trừ tiền Ví Tạm Giữ (DEBIT)
        BigDecimal escrowBalAfter = lockedEscrow.getBalance().subtract(amount);
        lockedEscrow.setBalance(escrowBalAfter);
        walletRepository.save(lockedEscrow);

        ledgerEntryRepository.save(LedgerEntryEntity.builder()
                .transactionId(tx.getId())
                .walletId(lockedEscrow.getId())
                .entryType(EntryType.DEBIT)
                .amount(amount)
                .balanceAfter(escrowBalAfter)
                .description("Hoan tra tien tam giu don huy #" + orderId)
                .build());

        // BƯỚC 5: Cộng tiền trả lại Ví Khách (CREDIT)
        BigDecimal custBalAfter = lockedCust.getBalance().add(amount);
        lockedCust.setBalance(custBalAfter);
        walletRepository.save(lockedCust);

        ledgerEntryRepository.save(LedgerEntryEntity.builder()
                .transactionId(tx.getId())
                .walletId(lockedCust.getId())
                .entryType(EntryType.CREDIT)
                .amount(amount)
                .balanceAfter(custBalAfter)
                .description("Nhan hoan tien don hang bi huy #" + orderId)
                .build());

        log.info(">>> SUCCESS ESCROW_REFUND for order [{}]", orderId);
        return tx;
    }

    /**
     * KỊCH BẢN 5: Tạo yêu cầu Rút tiền về Ngân hàng (Payout / Withdrawal).
     * -------------------------------------------------------------
     * Luồng xử lý:
     * 1. Kiểm tra hạn mức rút tối thiểu (50.000 VNĐ).
     * 2. Khóa Ví Khả dụng người dùng (Pessimistic Lock).
     * 3. Kiểm tra số dư có đủ không.
     * 4. Trừ tiền số dư khả dụng ngay lập tức (Tránh vừa rút vừa tiêu lạm).
     * 5. Tạo yêu cầu trong bảng withdrawal_requests (Trạng thái: PROCESSING).
     * 6. Ghi bút toán Sổ cái DEBIT.
     */
    @Transactional
    public WithdrawalRequestEntity requestWithdrawal(String userId, UserType userType, UUID bankAccountId,
            BigDecimal amount) {
        log.info(">>> Requesting withdrawal for user [{}], userType [{}], amount [{}]", userId, userType, amount);

        // BƯỚC 1: Kiểm tra điều kiện rút
        if (amount.compareTo(BigDecimal.valueOf(50000)) < 0) {
            throw new IllegalArgumentException("Số tiền rút tối thiểu là 50.000 VNĐ");
        }

        // BƯỚC 2: Khóa ví người dùng
        WalletEntity wallet = getOrCreateWallet(userId, userType);
        List<WalletEntity> lockedWallets = walletRepository.findAllByIdInOrderByIdAscForUpdate(List.of(wallet.getId()));
        WalletEntity lockedWallet = lockedWallets.get(0);

        if (lockedWallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Số dư ví không đủ để rút " + amount + " VNĐ");
        }

        // BƯỚC 3: Trừ tiền ví ngay lập tức
        BigDecimal newBal = lockedWallet.getBalance().subtract(amount);
        lockedWallet.setBalance(newBal);
        walletRepository.save(lockedWallet);

        // BƯỚC 4: Tạo lệnh yêu cầu rút tiền Payout
        WithdrawalRequestEntity request = withdrawalRequestRepository.save(WithdrawalRequestEntity.builder()
                .walletId(lockedWallet.getId())
                .userBankAccountId(bankAccountId)
                .amount(amount)
                .fee(BigDecimal.ZERO)
                .status(WithdrawalStatus.PROCESSING)
                .build());

        // BƯỚC 5: Tạo Transaction Header & Ghi Sổ cái DEBIT
        TransactionEntity tx = transactionRepository.save(TransactionEntity.builder()
                .referenceId(request.getId().toString())
                .transactionType(TransactionType.WITHDRAWAL)
                .totalAmount(amount)
                .feeAmount(BigDecimal.ZERO)
                .status(TransactionStatus.PENDING)
                .idempotencyKey("WITHDRAWAL_" + request.getId())
                .build());

        ledgerEntryRepository.save(LedgerEntryEntity.builder()
                .transactionId(tx.getId())
                .walletId(lockedWallet.getId())
                .entryType(EntryType.DEBIT)
                .amount(amount)
                .balanceAfter(newBal)
                .description("Yeu cau rut tien ve tai khoan ngan hang #" + request.getId())
                .build());

        return request;
    }

    /**
     * BƯỚC PHỤ TRỢ: Truy vấn lịch sử bút toán sổ cái của Ví (Phục vụ đối soát).
     */
    @Transactional(readOnly = true)
    public List<LedgerEntryEntity> getWalletLedgerHistory(String userId, UserType userType) {
        return walletRepository.findByUserIdAndUserType(userId, userType)
                .map(w -> ledgerEntryRepository.findByWalletIdOrderByCreatedAtDesc(w.getId()))
                .orElse(Collections.emptyList());
    }

    /**
     * BƯỚC PHỤ TRỢ: Thêm tài khoản ngân hàng chính chủ để rút tiền.
     */
    @Transactional
    public UserBankAccountEntity addBankAccount(String userId, String bankCode, String accountNumber,
            String accountName) {
        return userBankAccountRepository.save(UserBankAccountEntity.builder()
                .userId(userId)
                .bankCode(bankCode)
                .accountNumber(accountNumber)
                .accountName(accountName)
                .isVerified(true)
                .build());
    }

    /**
     * BƯỚC PHỤ TRỢ: Lấy danh sách tài khoản ngân hàng liên kết của user.
     */
    @Transactional(readOnly = true)
    public List<UserBankAccountEntity> getUserBankAccounts(String userId) {
        return userBankAccountRepository.findByUserId(userId);
    }
}
