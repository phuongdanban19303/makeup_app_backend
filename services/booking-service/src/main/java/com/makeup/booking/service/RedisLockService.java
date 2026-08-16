package com.makeup.booking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service quản lý Khóa Phân Tán (Distributed Lock) dựa trên Redis.
 * <p>
 * MỤC ĐÍCH & Ý NGHĨA:
 * Trong hệ thống ghép đơn thời gian thực, nhiều yêu cầu đặt lịch có thể xảy ra
 * đồng thời.
 * Để tránh tình trạng Race Condition (2 khách hàng đặt cùng 1 Thợ makeup tại
 * một thời điểm),
 * ta dùng Distributed Lock để "khóa" tạm thời Thợ makeup đó.
 * <p>
 * CƠ CHẾ HOẠT ĐỘNG:
 * 1. Redis Command: SETNX (Set if Not Exists) thông qua
 * redisTemplate.opsForValue().setIfAbsent().
 * 2. Key format: "lock:mua:{muaId}"
 * 3. Value format: bookingId (định danh đơn đang giữ khóa)
 * 4. TTL (Time to live): Đặt thời gian hết hạn (vd: 30 giây) tránh tình trạng
 * Deadlock nếu service bị crash.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisLockService {

    private static final String LOCK_PREFIX = "lock:mua:";
    private final StringRedisTemplate redisTemplate;

    /**
     * Thử lấy Distributed Lock cho Thợ makeup.
     *
     * Mã thợ cần khóa
     * 
     * @param bookingId  Mã đơn đặt lịch
     * @param ttlSeconds Thời gian khóa tự động hết hạn (giây)
     * @return true nếu lấy lock thành công (thợ chưa bị khóa bởi đơn khác); false
     *         nếu thợ đã bị khóa.
     */
    public boolean acquireLock(String muaId, String bookingId, long ttlSeconds) {
        String lockKey = LOCK_PREFIX + muaId;

        // Thao tác nguyên tử (Atomic Operation): SET key value NX EX ttlSeconds
        Boolean isAcquired = redisTemplate.opsForValue().setIfAbsent(
                lockKey,
                bookingId,
                Duration.ofSeconds(ttlSeconds));

        boolean success = Boolean.TRUE.equals(isAcquired);
        if (success) {
            log.info("Acquired Redis Distributed Lock successfully for MUA [{}] with booking [{}] (TTL: {}s)",
                    muaId, bookingId, ttlSeconds);
        } else {
            log.warn(
                    "Failed to acquire Redis Distributed Lock for MUA [{}]. MUA is currently locked by another booking.",
                    muaId);
        }
        return success;
    }

    /**
     * Giải phóng Distributed Lock khi Thợ nhận đơn, từ chối đơn hoặc bị Timeout.
     * 
     * Chỉ giải phóng nếu giá trị lưu trong Redis trùng khớp với bookingId (tránh
     * xóa lock của đơn khác nếu đã hết hạn TTL).
     *
     * @param muaId     Mã thợ
     * @param bookingId Mã đơn đang giữ lock
     * @return true nếu giải phóng lock thành công
     */
    public boolean releaseLock(String muaId, String bookingId) {
        String lockKey = LOCK_PREFIX + muaId;
        String currentValue = redisTemplate.opsForValue().get(lockKey);

        // Kiểm tra đúng owner của lock trước khi xóa
        if (bookingId.equals(currentValue)) {
            Boolean isDeleted = redisTemplate.delete(lockKey);
            boolean success = Boolean.TRUE.equals(isDeleted);
            log.info("Released Redis Distributed Lock for MUA [{}] and booking [{}]: {}", muaId, bookingId, success);
            return success;
        }

        log.warn("Cannot release lock for MUA [{}]. Current lock value [{}] does not match booking [{}]",
                muaId, currentValue, bookingId);
        return false;
    }

    /**
     * Kiểm tra Thợ makeup hiện tại có đang bị khóa hay không.
     */
    public boolean isLocked(String muaId) {
        String lockKey = LOCK_PREFIX + muaId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
    }
}
