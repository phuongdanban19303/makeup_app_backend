package com.makeup.booking.controller;

import com.makeup.booking.dto.BookingCreateRequestDto;
import com.makeup.booking.dto.BookingRequestDto;
import com.makeup.booking.dto.BookingResponseDto;
import com.makeup.booking.enums.BookingStatus;
import com.makeup.booking.service.BookingMatchingService;
import com.makeup.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller xử lý các API liên quan đến Đặt lịch, Ghép thợ & Vòng đời đơn hàng
 * (Order State Machine).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingMatchingService bookingMatchingService;

    private Long parseUserIdSafely(String currentUserId, Long defaultFallback) {
        if (currentUserId != null && !currentUserId.isBlank() && !"anonymousUser".equalsIgnoreCase(currentUserId)) {
            try {
                return Long.parseLong(currentUserId);
            } catch (NumberFormatException e) {
                log.warn("Cannot parse currentUserId [{}] to Long, falling back to [{}]", currentUserId,
                        defaultFallback);
            }
        }
        return defaultFallback;
    }

    /**
     * API 1. Khách hàng gửi yêu cầu đặt lịch -> Ghép thợ rảnh gần nhất & Khóa thợ
     * (Distributed Lock)
     */
    @PostMapping("/request")
    public ResponseEntity<ApiResponse<BookingResponseDto>> requestBooking(
            @AuthenticationPrincipal String currentUserId,
            @RequestBody BookingCreateRequestDto request) {
        log.info("\n=======================================================");
        log.info(">>> [BOOKING-SERVICE] API POST /api/v1/bookings/request RECEIVED");
        log.info(">>> CustomerId: [{}], MUA ID Target: [{}], ServicePackageId: [{}]",
                currentUserId, request.getMuaId(), request.getServicePackageId());
        log.info("=======================================================");

        if (currentUserId != null && !currentUserId.isBlank() && !"anonymousUser".equalsIgnoreCase(currentUserId)) {
            try {
                request.setCustomerId(Long.parseLong(currentUserId));
            } catch (Exception e) {
                log.warn("Cannot parse customerId [{}], keeping default", currentUserId);
            }
        }

        BookingResponseDto response = bookingMatchingService.createAndMatchBooking(request);

        log.info(">>> [BOOKING-SERVICE] Booking Request SUCCESS: BookingId=[{}], MuaId=[{}], Status=[{}]",
                response.getId(), response.getMuaId(), response.getStatus());

        return ResponseEntity
                .ok(ApiResponse.success(response, "Booking requested and MUA matching initiated successfully"));
    }

    /**
     * API 2a. Thợ trang điểm (MUA) chấp nhận ca (Accept Booking)
     */
    @PostMapping("/{bookingId}/accept")
    public ResponseEntity<ApiResponse<BookingResponseDto>> acceptBooking(
            @PathVariable String bookingId,
            @AuthenticationPrincipal String currentUserId) {
        Long muaId = parseUserIdSafely(currentUserId, 2L);
        log.info("\n=======================================================");
        log.info(">>> [BOOKING-SERVICE] API POST /api/v1/bookings/{}/accept RECEIVED for MUA [{}]", bookingId, muaId);
        log.info("=======================================================");

        BookingResponseDto response = bookingMatchingService.acceptBooking(bookingId, muaId);

        log.info(">>> [BOOKING-SERVICE] Accept Booking SUCCESS: BookingId=[{}], NewStatus=[{}]",
                bookingId, response.getStatus());

        return ResponseEntity.ok(ApiResponse.success(response, "Booking accepted by MUA"));
    }

    /**
     * API 2b. Thợ trang điểm (MUA) từ chối ca (Reject Booking)
     */
    @PostMapping("/{bookingId}/reject")
    public ResponseEntity<ApiResponse<BookingResponseDto>> rejectBooking(
            @PathVariable String bookingId,
            @AuthenticationPrincipal String currentUserId) {
        Long muaId = parseUserIdSafely(currentUserId, 2L);
        log.info("\n=======================================================");
        log.info(">>> [BOOKING-SERVICE] API POST /api/v1/bookings/{}/reject RECEIVED for MUA [{}]", bookingId, muaId);
        log.info("=======================================================");

        BookingResponseDto response = bookingMatchingService.rejectBooking(bookingId, muaId);

        return ResponseEntity.ok(ApiResponse.success(response, "Booking rejected by MUA, trying next candidate"));
    }

    /**
     * Legacy Booking creation API
     */
    @PostMapping
    public ResponseEntity<ApiResponse<BookingRequestDto>> createBooking(
            @AuthenticationPrincipal String currentUserId,
            @RequestBody BookingRequestDto request) {
        request.setBookingId(UUID.randomUUID().toString());
        if (currentUserId != null && !currentUserId.isBlank()) {
            request.setCustomerId(currentUserId);
        }
        request.setStatus(BookingStatus.REQUESTED.name());
        return ResponseEntity.ok(ApiResponse.success(request, "Booking requested successfully"));
    }

    /**
     * API 3a. Thợ trang điểm (MUA) bắt đầu di chuyển đến nhà khách (MUA_MOVING)
     */
    @PostMapping("/{bookingId}/start-moving")
    public ResponseEntity<ApiResponse<BookingResponseDto>> startMoving(
            @PathVariable String bookingId,
            @AuthenticationPrincipal String currentUserId) {
        Long muaId = parseUserIdSafely(currentUserId, 2L);
        log.info(">>> [BOOKING-SERVICE] API POST /api/v1/bookings/{}/start-moving for MUA [{}]", bookingId, muaId);
        BookingResponseDto response = bookingMatchingService.startMoving(bookingId, muaId);
        return ResponseEntity.ok(ApiResponse.success(response, "MUA started moving to customer location"));
    }

    /**
     * API 3b. Thợ trang điểm (MUA) bấm "Đã đến nơi" (ARRIVED)
     */
    @PostMapping("/{bookingId}/arrived")
    public ResponseEntity<ApiResponse<BookingResponseDto>> markArrived(
            @PathVariable String bookingId,
            @AuthenticationPrincipal String currentUserId) {
        Long muaId = parseUserIdSafely(currentUserId, 2L);
        log.info(">>> [BOOKING-SERVICE] API POST /api/v1/bookings/{}/arrived for MUA [{}]", bookingId, muaId);
        BookingResponseDto response = bookingMatchingService.markArrived(bookingId, muaId);
        return ResponseEntity.ok(ApiResponse.success(response, "MUA arrived at customer location successfully"));
    }

    /**
     * API 3c. Thợ trang điểm (MUA) bấm "Bắt đầu Makeup" (MAKING_UP)
     */
    @PostMapping("/{bookingId}/start-makeup")
    public ResponseEntity<ApiResponse<BookingResponseDto>> startMakeup(
            @PathVariable String bookingId,
            @AuthenticationPrincipal String currentUserId) {
        Long muaId = parseUserIdSafely(currentUserId, 2L);
        log.info(">>> [BOOKING-SERVICE] API POST /api/v1/bookings/{}/start-makeup for MUA [{}]", bookingId, muaId);
        BookingResponseDto response = bookingMatchingService.startMakeup(bookingId, muaId);
        return ResponseEntity.ok(ApiResponse.success(response, "MUA started makeup service"));
    }

    /**
     * API 3d. Thợ trang điểm (MUA) bấm "Hoàn thành" (COMPLETED)
     */
    @PostMapping("/{bookingId}/complete")
    public ResponseEntity<ApiResponse<BookingResponseDto>> completeBooking(
            @PathVariable String bookingId,
            @AuthenticationPrincipal String currentUserId) {
        Long muaId = parseUserIdSafely(currentUserId, 2L);
        log.info(">>> [BOOKING-SERVICE] API POST /api/v1/bookings/{}/complete for MUA [{}]", bookingId, muaId);
        BookingResponseDto response = bookingMatchingService.completeBooking(bookingId, muaId);
        return ResponseEntity.ok(ApiResponse.success(response, "Booking service completed successfully"));
    }

    /**
     * API 3e. Hủy đơn đặt lịch khi có sự cố phát sinh (CANCELLED)
     */
    @PostMapping("/{bookingId}/cancel")
    public ResponseEntity<ApiResponse<BookingResponseDto>> cancelBooking(
            @PathVariable String bookingId,
            @RequestParam(required = false, defaultValue = "User requested cancellation") String reason) {
        log.info(">>> [BOOKING-SERVICE] API POST /api/v1/bookings/{}/cancel reason [{}]", bookingId, reason);
        BookingResponseDto response = bookingMatchingService.cancelBooking(bookingId, reason);
        return ResponseEntity.ok(ApiResponse.success(response, "Booking cancelled successfully"));
    }

    /**
     * API 4. Cập nhật chuyển trạng thái đơn tổng quát (State Machine Lifecycle)
     */
    @PutMapping("/{bookingId}/status")
    public ResponseEntity<ApiResponse<BookingResponseDto>> updateBookingStatus(
            @PathVariable String bookingId,
            @RequestParam BookingStatus targetStatus) {
        log.info(">>> [BOOKING-SERVICE] API PUT /api/v1/bookings/{}/status to [{}]", bookingId, targetStatus);
        BookingResponseDto response = bookingMatchingService.updateBookingStatus(bookingId, targetStatus);
        return ResponseEntity
                .ok(ApiResponse.success(response, "Booking status updated to " + targetStatus + " successfully"));
    }

    /**
     * API 5a. Lấy chi tiết đơn đặt lịch theo bookingId
     */
    @GetMapping("/{bookingId}")
    public ResponseEntity<ApiResponse<BookingResponseDto>> getBookingById(@PathVariable String bookingId) {
        log.info(">>> [BOOKING-SERVICE] API GET /api/v1/bookings/{}", bookingId);
        BookingResponseDto response = bookingMatchingService.getBookingById(bookingId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * API 5b. Lấy ca đang hoạt động của Thợ MUA (Dùng để khôi phục tiến trình khi
     * F5)
     */
    @GetMapping("/worker/active")
    public ResponseEntity<ApiResponse<BookingResponseDto>> getActiveBookingForWorker(
            @AuthenticationPrincipal String currentUserId,
            @RequestParam(required = false) Long muaId) {
        Long workerId = parseUserIdSafely(currentUserId, muaId != null ? muaId : 2L);
        log.info(">>> [BOOKING-SERVICE] API GET /api/v1/bookings/worker/active for MUA [{}]", workerId);
        BookingResponseDto response = bookingMatchingService.getActiveBookingForWorker(workerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * API 5c. Lấy các ca đang chờ Thợ MUA xác nhận (MATCHING /
     * WAITING_FOR_MUA_CONFIRM)
     */
    @GetMapping("/worker/pending-requests")
    public ResponseEntity<ApiResponse<java.util.List<BookingResponseDto>>> getPendingBookingsForWorker(
            @AuthenticationPrincipal String currentUserId,
            @RequestParam(required = false) Long muaId) {
        Long workerId = parseUserIdSafely(currentUserId, muaId != null ? muaId : 2L);
        log.info(">>> [BOOKING-SERVICE] API GET /api/v1/bookings/worker/pending-requests for MUA [{}]", workerId);
        java.util.List<BookingResponseDto> response = bookingMatchingService.getPendingBookingsForWorker(workerId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * API 5d. Lấy ca đang hoạt động của Khách hàng (Dùng để khôi phục tiến trình
     * khi F5/vào trang chủ)
     */
    @GetMapping("/customer/active")
    public ResponseEntity<ApiResponse<BookingResponseDto>> getActiveBookingForCustomer(
            @AuthenticationPrincipal String currentUserId,
            @RequestParam(required = false) Long customerId) {
        Long cId = parseUserIdSafely(currentUserId, customerId != null ? customerId : 1L);
        log.info(">>> [BOOKING-SERVICE] API GET /api/v1/bookings/customer/active for Customer [{}]", cId);
        BookingResponseDto response = bookingMatchingService.getActiveBookingForCustomer(cId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
