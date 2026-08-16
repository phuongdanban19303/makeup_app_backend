package com.makeup.booking.service;

import com.makeup.booking.client.LocationClient;
import com.makeup.booking.client.PricingClient;
import com.makeup.booking.document.BookingDocument;
import com.makeup.booking.dto.*;
import com.makeup.booking.enums.BookingStatus;
import com.makeup.booking.repository.BookingMongoRepository;
import com.makeup.booking.statemachine.BookingStateMachine;
import com.makeup.common.exception.AppException;
import com.makeup.common.exception.ErrorCode;
import com.makeup.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import com.makeup.booking.entity.BookingEntity;
import com.makeup.booking.entity.BookingStatusLogEntity;
import com.makeup.booking.repository.BookingJpaRepository;
import com.makeup.booking.repository.BookingStatusLogRepository;

import java.time.LocalDateTime;

/**
 * ====================================================================================
 * CORE MATCHING ENGINE & DISPATCHING SERVICE (ĐỘNG CƠ GHÉP ĐƠN THỜI GIAN THỰC)
 * ====================================================================================
 * Order State Machine Lifecycle & Direct MUA Booking Support
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingMatchingService {

    private final BookingMongoRepository mongoRepository;
    private final BookingJpaRepository bookingJpaRepository;
    private final BookingStatusLogRepository bookingStatusLogRepository;
    private final RedisLockService redisLockService;
    private final BookingKafkaProducer kafkaProducer;
    private final BookingStateMachine stateMachine;

    // OpenFeign Declarative REST Clients (Type-Safe DTOs)
    private final PricingClient pricingClient;
    private final LocationClient locationClient;

    private static final double DEFAULT_RADIUS_KM = 3.0; // Bán kính tìm kiếm thợ 3 km
    private static final long MUA_LOCK_TTL_SECONDS = 30; // Thời gian khóa thợ 30 giây

    /**
     * LUỒNG 1: Tạo đơn đặt lịch trực tiếp theo Thợ MUA do Khách chọn (Direct MUA
     * Booking)
     */
    public BookingResponseDto createAndMatchBooking(BookingCreateRequestDto request) {
        log.info("Starting direct booking process for customerId: {}, target MUA: {}, servicePackageId: {}",
                request.getCustomerId(), request.getMuaId(), request.getServicePackageId());

        if (request.getMuaId() == null) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Vui lòng chọn Thợ trang điểm (MUA) bạn muốn đặt lịch!");
        }

        Long targetMuaId = request.getMuaId();
        String targetMuaName = request.getMuaName() != null ? request.getMuaName() : ("MUA #" + targetMuaId);

        String bookingId = UUID.randomUUID().toString();
        String bookingCode = "BK-" + System.currentTimeMillis();

        // --------------------------------------------------------------------------------
        // BƯỚC 1: QUÉT VỊ TRÍ VÀ TÍNH KHOẢNG CÁCH THỰC TẾ TỚI THỢ MUA ĐÃ CHỌN
        // --------------------------------------------------------------------------------
        Double distanceKm = 1.0;
        List<NearbyWorkerDto> nearbyMuas = fetchNearbyMuasFromLocationService(
                request.getCustomerLat() != null ? request.getCustomerLat() : 10.7769,
                request.getCustomerLng() != null ? request.getCustomerLng() : 106.7009,
                10.0); // Bán kính 10 km

        for (NearbyWorkerDto candidate : nearbyMuas) {
            if (candidate.getWorkerId() != null && candidate.getWorkerId().equals(targetMuaId)) {
                if (candidate.getDistanceKm() > 0) {
                    distanceKm = candidate.getDistanceKm();
                }
                break;
            }
        }

        // --------------------------------------------------------------------------------
        // BƯỚC 2: THỰC HIỆN DISTRIBUTED LOCK TRÊN REDIS ĐỂ KHÓA THỢ MUA ĐÃ CHỌN
        // --------------------------------------------------------------------------------
        boolean acquired = redisLockService.acquireLock(targetMuaId.toString(), bookingId, MUA_LOCK_TTL_SECONDS);
        if (!acquired) {
            log.warn("MUA [{}] is currently locked/busy for booking [{}]", targetMuaId, bookingCode);
            throw new AppException(ErrorCode.INVALID_REQUEST, "Thợ trang điểm " + targetMuaName
                    + " đang bận xử lý đơn khác. Vui lòng chọn thợ khác hoặc thử lại sau!");
        }

        log.info("MUA [{}] locked successfully for direct booking [{}], distance: {} km", targetMuaId, bookingCode,
                distanceKm);

        // --------------------------------------------------------------------------------
        // BƯỚC 3: TÍNH CƯỚC TỰ ĐỘNG BẰNG PRICING SERVICE DÙNG SỐ KM THỰC TẾ
        // --------------------------------------------------------------------------------
        PricingResponseDto pricingResult = calculateFeeFromPricingService(request, distanceKm);

        BigDecimal basePrice = BigDecimal.valueOf(pricingResult.getPackageSubtotal());
        BigDecimal travelDistanceFee = BigDecimal.valueOf(pricingResult.getTravelDistanceFee());
        Double surgeMultiplier = pricingResult.getSurgeMultiplier();
        BigDecimal totalFee = BigDecimal.valueOf(pricingResult.getTotalFee());

        log.info(
                "Calculated pricing via OpenFeign DTO for booking [{}]: Base = {}, Distance = {}km, Surge = {}, Total = {}",
                bookingCode, basePrice, distanceKm, surgeMultiplier, totalFee);

        // --------------------------------------------------------------------------------
        // BƯỚC 4: TẠO DOCUMENT LƯU VÀO MONGODB COLLECTION "bookings" (STATUS: MATCHING)
        // --------------------------------------------------------------------------------
        List<BookingDocument.StateHistoryInfo> stateHistory = new ArrayList<>();
        stateHistory.add(new BookingDocument.StateHistoryInfo(BookingStatus.CREATED.name(), Instant.now().toString()));
        stateHistory.add(new BookingDocument.StateHistoryInfo(BookingStatus.MATCHING.name(), Instant.now().toString()));

        BookingDocument bookingDoc = BookingDocument.builder()
                .id(bookingId)
                .bookingCode(bookingCode)
                .customer(BookingDocument.CustomerInfo.builder()
                        .userId(request.getCustomerId())
                        .name(request.getCustomerName())
                        .phone(request.getCustomerPhone())
                        .build())
                .mua(BookingDocument.MuaInfo.builder()
                        .userId(targetMuaId)
                        .name(targetMuaName)
                        .build())
                .serviceDetails(BookingDocument.ServiceDetailsInfo.builder()
                        .serviceId(request.getServicePackageId())
                        .category(request.getServiceCategory())
                        .name(request.getServiceName())
                        .build())
                .location(BookingDocument.LocationInfo.builder()
                        .type("Point")
                        .coordinates(List.of(
                                request.getCustomerLng() != null ? request.getCustomerLng() : 106.7009,
                                request.getCustomerLat() != null ? request.getCustomerLat() : 10.7769))
                        .address(request.getAddress())
                        .build())
                .pricing(BookingDocument.PricingInfo.builder()
                        .basePrice(basePrice)
                        .movingDistanceKm(distanceKm)
                        .movingFee(travelDistanceFee)
                        .surgeMultiplier(surgeMultiplier)
                        .totalFee(totalFee)
                        .build())
                .notes(request.getNotes())
                .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod() : "CASH")
                .status(BookingStatus.MATCHING.name())
                .stateHistory(stateHistory)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        BookingDocument savedDoc = mongoRepository.save(bookingDoc);
        log.info("Direct booking document [{}] saved to MongoDB with status MATCHING for MUA [{}]", savedDoc.getId(),
                targetMuaId);

        // Sync to PostgreSQL bookings and booking_status_logs
        syncToPostgres(savedDoc, null, "Booking Created MATCHING");

        // --------------------------------------------------------------------------------
        // BƯỚC 5: PHÁT EVENT KAFKA "BOOKING_REQUESTED" ĐỂ PUSH POPUP TỚI MUA APP VIA
        // WEBSOCKET
        // --------------------------------------------------------------------------------
        BookingEventDto eventDto = BookingEventDto.builder()
                .eventType("BOOKING_REQUESTED")
                .bookingId(bookingId)
                .bookingCode(bookingCode)
                .customerId(request.getCustomerId())
                .customerName(request.getCustomerName())
                .customerPhone(request.getCustomerPhone())
                .muaId(targetMuaId)
                .muaName(targetMuaName)
                .servicePackageId(request.getServicePackageId())
                .serviceName(request.getServiceName())
                .customerLat(request.getCustomerLat())
                .customerLng(request.getCustomerLng())
                .address(request.getAddress())
                .distanceKm(distanceKm)
                .totalFee(totalFee)
                .paymentMethod(savedDoc.getPaymentMethod())
                .status(BookingStatus.MATCHING.name())
                .timestamp(Instant.now().toString())
                .build();

        // 1. Publish event to Kafka Bus
        kafkaProducer.publishBookingRequested(eventDto);

        // 2. Direct HTTP Feign notify to Location Service for 100% instant WebSocket
        // popup delivery
        try {
            locationClient.notifyBooking(eventDto);
            log.info("Direct HTTP Feign notification sent to location-service for MUA [{}]", targetMuaId);
        } catch (Exception e) {
            log.warn("Direct HTTP Feign notification to location-service failed (Kafka fallback active): {}",
                    e.getMessage());
        }

        return mapToResponseDto(savedDoc);

    }

    /**
     * LUỒNG 2a: THỢ BẤM NHẬN ĐƠN (MUA ACCEPT BOOKING)
     */
    public BookingResponseDto acceptBooking(String bookingId, Long muaId) {
        log.info("MUA [{}] accepted booking [{}]", muaId, bookingId);

        BookingDocument booking = mongoRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn đặt lịch"));

        BookingStatus currentStatus = BookingStatus.valueOf(booking.getStatus());
        BookingStatus targetStatus = BookingStatus.ACCEPTED;

        // Validation máy trạng thái (State Machine)
        if (!stateMachine.canTransition(currentStatus, targetStatus)) {
            throw new AppException(ErrorCode.INVALID_BOOKING_STATUS,
                    "Không thể chuyển trạng thái từ " + currentStatus + " sang " + targetStatus);
        }

        // Cập nhật trạng thái đơn: MATCHING -> ACCEPTED / MUA_MOVING
        booking.setStatus(BookingStatus.ACCEPTED.name());
        if (booking.getStateHistory() == null) {
            booking.setStateHistory(new ArrayList<>());
        }
        booking.getStateHistory()
                .add(new BookingDocument.StateHistoryInfo(BookingStatus.ACCEPTED.name(), Instant.now().toString()));
        booking.setUpdatedAt(Instant.now());
        BookingDocument savedDoc = mongoRepository.save(booking);

        // Sync to PostgreSQL bookings and booking_status_logs
        syncToPostgres(savedDoc, currentStatus.name(), "MUA Accepted Booking");

        // Bắn thông báo Kafka cho Khách hàng: "Thợ đang trên đường đến"
        BookingEventDto acceptedEvent = BookingEventDto.builder()
                .eventType("BOOKING_ACCEPTED")
                .bookingId(bookingId)
                .bookingCode(booking.getBookingCode())
                .customerId(booking.getCustomer().getUserId())
                .muaId(muaId)
                .totalFee(booking.getPricing() != null ? booking.getPricing().getTotalFee() : null)
                .paymentMethod(booking.getPaymentMethod() != null ? booking.getPaymentMethod() : "CASH")
                .status(BookingStatus.ACCEPTED.name())
                .timestamp(Instant.now().toString())
                .build();

        kafkaProducer.publishBookingAccepted(acceptedEvent);

        // Direct REST push notification to Location Service so Customer app updates
        // instantly in 5ms
        try {
            locationClient.notifyBooking(acceptedEvent);
            log.info("Direct HTTP Feign notification sent to location-service for ACCEPTED event [{}]", bookingId);
        } catch (Exception e) {
            log.warn("Direct HTTP Feign notification to location-service failed: {}", e.getMessage());
        }

        return mapToResponseDto(savedDoc);
    }

    /**
     * LUỒNG 2b: THỢ TỪ CHỐI ĐƠN (MUA REJECT BOOKING -> CANCEL BOOKING)
     */
    public BookingResponseDto rejectBooking(String bookingId, Long muaId) {
        log.info("MUA [{}] rejected booking [{}]. Marking booking as CANCELLED.", muaId, bookingId);

        BookingDocument booking = mongoRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn đặt lịch"));

        // 1. Giải phóng Redis Lock cho thợ từ chối
        redisLockService.releaseLock(muaId.toString(), bookingId);

        // 2. Cập nhật trạng thái đơn thành CANCELLED
        booking.setStatus(BookingStatus.CANCELLED.name());
        if (booking.getStateHistory() == null) {
            booking.setStateHistory(new ArrayList<>());
        }
        booking.getStateHistory()
                .add(new BookingDocument.StateHistoryInfo(BookingStatus.CANCELLED.name(), Instant.now().toString()));
        booking.setUpdatedAt(Instant.now());
        BookingDocument cancelledDoc = mongoRepository.save(booking);

        // Sync to PostgreSQL bookings and booking_status_logs
        syncToPostgres(cancelledDoc, "MATCHING", "MUA Rejected Booking");

        BookingEventDto rejectedEvent = BookingEventDto.builder()
                .eventType("BOOKING_REJECTED")
                .bookingId(bookingId)
                .bookingCode(booking.getBookingCode())
                .customerId(booking.getCustomer() != null ? booking.getCustomer().getUserId() : null)
                .muaId(muaId)
                .totalFee(booking.getPricing() != null ? booking.getPricing().getTotalFee() : null)
                .paymentMethod(booking.getPaymentMethod() != null ? booking.getPaymentMethod() : "CASH")
                .status("REJECTED")
                .timestamp(Instant.now().toString())
                .build();

        // 3. Phát sự kiện Kafka & Direct REST
        kafkaProducer.publishBookingRejected(rejectedEvent);
        try {
            locationClient.notifyBooking(rejectedEvent);
        } catch (Exception e) {
            log.warn("Direct HTTP notification for REJECTED event failed: {}", e.getMessage());
        }

        return mapToResponseDto(cancelledDoc);
    }

    // ====================================================================================
    // LUỒNG VÒNG ĐỜI ĐƠN HÀNG (ORDER STATE MACHINE LIFECYCLE TRANSITIONS)
    // ====================================================================================

    /**
     * 1. Thợ bắt đầu di chuyển tới vị trí khách hàng (ACCEPTED -> MUA_MOVING)
     */
    public BookingResponseDto startMoving(String bookingId, Long muaId) {
        log.info("MUA [{}] started moving to customer location for booking [{}]", muaId, bookingId);
        return updateBookingStatus(bookingId, BookingStatus.MUA_MOVING, "BOOKING_MUA_MOVING");
    }

    /**
     * 2. Thợ đã tới địa chỉ nhà khách hàng (MUA_MOVING -> ARRIVED)
     * Thợ bấm nút "Đã đến nơi" trên ứng dụng MUA App.
     */
    public BookingResponseDto markArrived(String bookingId, Long muaId) {
        log.info("MUA [{}] arrived at customer location for booking [{}]", muaId, bookingId);
        return updateBookingStatus(bookingId, BookingStatus.ARRIVED, "BOOKING_ARRIVED");
    }

    /**
     * 3. Thợ bắt đầu thực hiện dịch vụ trang điểm (ARRIVED -> MAKING_UP)
     * Thợ bấm nút "Bắt đầu Makeup" trên ứng dụng MUA App.
     */
    public BookingResponseDto startMakeup(String bookingId, Long muaId) {
        log.info("MUA [{}] started makeup service for booking [{}]", muaId, bookingId);
        return updateBookingStatus(bookingId, BookingStatus.MAKING_UP, "BOOKING_MAKING_UP");
    }

    /**
     * 4. Thợ hoàn thành dịch vụ trang điểm (MAKING_UP -> COMPLETED)
     * Thợ bấm nút "Hoàn thành" trên ứng dụng MUA App.
     */
    public BookingResponseDto completeBooking(String bookingId, Long muaId) {
        log.info("MUA [{}] completed service for booking [{}]", muaId, bookingId);

        BookingDocument booking = mongoRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn đặt lịch"));

        // Giải phóng Redis Lock cho thợ sau khi hoàn thành đơn
        if (booking.getMua() != null && booking.getMua().getUserId() != null) {
            redisLockService.releaseLock(booking.getMua().getUserId().toString(), bookingId);
        }

        return updateBookingStatus(bookingId, BookingStatus.COMPLETED, "BOOKING_COMPLETED");
    }

    /**
     * 5. Hủy chuyến do có sự cố phát sinh (State -> CANCELLED)
     */
    public BookingResponseDto cancelBooking(String bookingId, String reason) {
        log.warn("Booking [{}] is being cancelled. Reason: {}", bookingId, reason);

        BookingDocument booking = mongoRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.MUA_NOT_FOUND, "Không tìm thấy đơn đặt lịch"));

        if (booking.getMua() != null && booking.getMua().getUserId() != null) {
            redisLockService.releaseLock(booking.getMua().getUserId().toString(), bookingId);
        }

        return updateBookingStatus(bookingId, BookingStatus.CANCELLED, "BOOKING_CANCELLED");
    }

    public BookingResponseDto updateBookingStatus(String bookingId, BookingStatus targetStatus) {
        return updateBookingStatus(bookingId, targetStatus, null);
    }

    /**
     * Core helper: Thực hiện chuyển đổi trạng thái bằng StateMachine, cập nhật
     * MongoDB & bắn Kafka Event
     */
    public BookingResponseDto updateBookingStatus(String bookingId, BookingStatus targetStatus, String eventType) {
        BookingDocument booking = mongoRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn đặt lịch"));

        BookingStatus currentStatus = BookingStatus.valueOf(booking.getStatus());

        // Kiểm tra tính hợp lệ của luồng chuyển trạng thái qua State Machine
        if (!stateMachine.canTransition(currentStatus, targetStatus)) {
            log.error("Invalid status transition attempt for booking [{}]: {} -> {}", bookingId, currentStatus,
                    targetStatus);
            throw new AppException(ErrorCode.INVALID_BOOKING_STATUS,
                    "Không thể chuyển trạng thái từ " + currentStatus + " sang " + targetStatus);
        }

        // Cập nhật trạng thái mới
        booking.setStatus(targetStatus.name());
        if (booking.getStateHistory() == null) {
            booking.setStateHistory(new ArrayList<>());
        }
        booking.getStateHistory()
                .add(new BookingDocument.StateHistoryInfo(targetStatus.name(), Instant.now().toString()));
        booking.setUpdatedAt(Instant.now());

        BookingDocument savedDoc = mongoRepository.save(booking);
        log.info("Booking [{}] status transitioned successfully: {} -> {}", bookingId, currentStatus, targetStatus);

        // Sync to PostgreSQL bookings and booking_status_logs
        syncToPostgres(savedDoc, currentStatus.name(), "Status updated to " + targetStatus.name());

        // Bắn sự kiện cập nhật trạng thái chuyến đi tới Kafka Event Bus
        BookingEventDto eventDto = BookingEventDto.builder()
                .eventType(eventType != null ? eventType : ("BOOKING_" + targetStatus.name()))
                .bookingId(bookingId)
                .bookingCode(booking.getBookingCode())
                .customerId(booking.getCustomer() != null ? booking.getCustomer().getUserId() : null)
                .muaId(booking.getMua() != null ? booking.getMua().getUserId() : null)
                .totalFee(booking.getPricing() != null ? booking.getPricing().getTotalFee() : null)
                .paymentMethod(booking.getPaymentMethod() != null ? booking.getPaymentMethod() : "CASH")
                .status(targetStatus.name())
                .timestamp(Instant.now().toString())
                .build();

        kafkaProducer.publishBookingStatusUpdated(eventDto);

        // Direct REST push notification to Location Service so Customer app updates
        // instantly in 5ms
        try {
            locationClient.notifyBooking(eventDto);
            log.info("Direct HTTP Feign notification sent to location-service for status update event [{}] -> [{}]",
                    bookingId, targetStatus);
        } catch (Exception e) {
            log.warn("Direct HTTP Feign notification to location-service failed: {}", e.getMessage());
        }

        return mapToResponseDto(savedDoc);

    }

    /**
     * Helper: Gọi pricing-service bằng OpenFeign DTO (Default fallback fee = 1.0)
     */
    private PricingResponseDto calculateFeeFromPricingService(BookingCreateRequestDto request, double distanceInKm) {
        double baseFee = request.getBasePackageFee() != null ? request.getBasePackageFee() : 0.0;
        double optionsFee = request.getOptionsFee() != null ? request.getOptionsFee() : 0.0;
        try {
            PricingRequestDto pricingRequest = PricingRequestDto.builder()
                    .servicePackageId(
                            request.getServicePackageId() != null ? request.getServicePackageId().toString() : "1")
                    .basePackageFee(baseFee)
                    .optionsFee(optionsFee)
                    .distanceInKm(distanceInKm)
                    .customerLat(request.getCustomerLat() != null ? request.getCustomerLat() : 10.7769)
                    .customerLng(request.getCustomerLng() != null ? request.getCustomerLng() : 106.7009)
                    .build();

            ApiResponse<PricingResponseDto> response = pricingClient.calculateServiceFee(pricingRequest);
            if (response != null && response.getData() != null) {
                return response.getData();
            }
        } catch (Exception e) {
            log.error("Failed to call pricing-service via OpenFeign, fallback to default fee calculation", e);
        }
        double subtotal = baseFee + optionsFee;
        double travelFee = distanceInKm * 10000.0;
        return PricingResponseDto.builder()
                .servicePackageId(
                        request.getServicePackageId() != null ? request.getServicePackageId().toString() : "1")
                .basePackageFee(baseFee)
                .optionsFee(optionsFee)
                .packageSubtotal(subtotal)
                .travelDistanceFee(travelFee)
                .surgeMultiplier(1.0)
                .totalFee(subtotal + travelFee)
                .currency("VND")
                .build();
    }

    /**
     * Helper: Gọi location-service bằng OpenFeign DTO
     */
    private List<NearbyWorkerDto> fetchNearbyMuasFromLocationService(double lat, double lng, double radiusKm) {
        try {
            ApiResponse<List<NearbyWorkerDto>> response = locationClient.getNearbyMuas(lat, lng, radiusKm);
            if (response != null && response.getData() != null) {
                return response.getData();
            }
        } catch (Exception e) {
            log.error("Failed to fetch nearby MUAs from location-service via OpenFeign", e);
        }
        return List.of();
    }

    private BookingResponseDto mapToResponseDto(BookingDocument doc) {
        return BookingResponseDto.builder()
                .id(doc.getId())
                .bookingCode(doc.getBookingCode())
                .customerId(doc.getCustomer() != null ? doc.getCustomer().getUserId() : null)
                .customerName(doc.getCustomer() != null ? doc.getCustomer().getName() : null)
                .muaId(doc.getMua() != null ? doc.getMua().getUserId() : null)
                .muaName(doc.getMua() != null ? doc.getMua().getName() : null)
                .servicePackageId(doc.getServiceDetails() != null ? doc.getServiceDetails().getServiceId() : null)
                .serviceName(doc.getServiceDetails() != null ? doc.getServiceDetails().getName() : null)
                .customerLat(doc.getLocation() != null && doc.getLocation().getCoordinates() != null
                        ? doc.getLocation().getCoordinates().get(1)
                        : null)
                .customerLng(doc.getLocation() != null && doc.getLocation().getCoordinates() != null
                        ? doc.getLocation().getCoordinates().get(0)
                        : null)
                .address(doc.getLocation() != null ? doc.getLocation().getAddress() : null)
                .basePrice(doc.getPricing() != null ? doc.getPricing().getBasePrice() : null)
                .movingDistanceKm(doc.getPricing() != null ? doc.getPricing().getMovingDistanceKm() : null)
                .movingFee(doc.getPricing() != null ? doc.getPricing().getMovingFee() : null)
                .surgeMultiplier(doc.getPricing() != null ? doc.getPricing().getSurgeMultiplier() : null)
                .totalFee(doc.getPricing() != null ? doc.getPricing().getTotalFee() : null)
                .status(doc.getStatus())
                .createdAt(doc.getCreatedAt())
                .build();
    }

    public BookingResponseDto getBookingById(String bookingId) {
        BookingDocument booking = mongoRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn đặt lịch"));
        return mapToResponseDto(booking);
    }

    public BookingResponseDto getActiveBookingForWorker(Long muaId) {
        log.info("Fetching active booking for MUA [{}]", muaId);
        List<BookingDocument> activeList = mongoRepository.findByMuaUserIdAndStatusIn(
                muaId, List.of("ACCEPTED", "MUA_MOVING", "ARRIVED", "MAKING_UP"));
        if (activeList != null && !activeList.isEmpty()) {
            return mapToResponseDto(activeList.get(0));
        }
        return null;
    }

    public BookingResponseDto getActiveBookingForCustomer(Long customerId) {
        log.info("Fetching active booking for Customer [{}]", customerId);
        List<BookingDocument> activeList = mongoRepository.findByCustomerUserIdAndStatusIn(
                customerId,
                List.of("MATCHING", "WAITING_FOR_MUA_CONFIRM", "ACCEPTED", "MUA_MOVING", "ARRIVED", "MAKING_UP"));
        if (activeList != null && !activeList.isEmpty()) {
            return mapToResponseDto(activeList.get(0));
        }
        return null;
    }

    public List<BookingResponseDto> getPendingBookingsForWorker(Long muaId) {
        log.info("Fetching pending request bookings for MUA [{}]", muaId);
        List<BookingDocument> pendingList = mongoRepository.findByMuaUserIdAndStatusIn(
                muaId, List.of("MATCHING", "WAITING_FOR_MUA_CONFIRM"));
        if (pendingList == null)
            return List.of();
        return pendingList.stream().map(this::mapToResponseDto).toList();
    }

    private void syncToPostgres(BookingDocument doc, String previousStatus, String note) {
        try {
            BigDecimal baseFee = doc.getPricing() != null && doc.getPricing().getBasePrice() != null
                    ? doc.getPricing().getBasePrice()
                    : BigDecimal.ZERO;
            BigDecimal travelFee = doc.getPricing() != null && doc.getPricing().getMovingFee() != null
                    ? doc.getPricing().getMovingFee()
                    : BigDecimal.ZERO;
            BigDecimal totalFee = doc.getPricing() != null && doc.getPricing().getTotalFee() != null
                    ? doc.getPricing().getTotalFee()
                    : BigDecimal.ZERO;

            Double lat = doc.getLocation() != null && doc.getLocation().getCoordinates() != null
                    && doc.getLocation().getCoordinates().size() >= 2
                            ? doc.getLocation().getCoordinates().get(1)
                            : 0.0;
            Double lng = doc.getLocation() != null && doc.getLocation().getCoordinates() != null
                    && doc.getLocation().getCoordinates().size() >= 1
                            ? doc.getLocation().getCoordinates().get(0)
                            : 0.0;

            String customerIdStr = doc.getCustomer() != null && doc.getCustomer().getUserId() != null
                    ? doc.getCustomer().getUserId().toString()
                    : "1";
            String muaIdStr = doc.getMua() != null && doc.getMua().getUserId() != null
                    ? doc.getMua().getUserId().toString()
                    : null;
            String serviceIdStr = doc.getServiceDetails() != null && doc.getServiceDetails().getServiceId() != null
                    ? doc.getServiceDetails().getServiceId().toString()
                    : "1";

            BookingEntity entity = BookingEntity.builder()
                    .id(doc.getId())
                    .bookingCode(
                            doc.getBookingCode() != null ? doc.getBookingCode() : ("BK-" + System.currentTimeMillis()))
                    .customerId(customerIdStr)
                    .muaId(muaIdStr)
                    .serviceId(serviceIdStr)
                    .baseFee(baseFee)
                    .travelFee(travelFee)
                    .totalFee(totalFee)
                    .surgeMultiplier(doc.getPricing() != null && doc.getPricing().getSurgeMultiplier() != null
                            ? doc.getPricing().getSurgeMultiplier().doubleValue()
                            : 1.0)
                    .customerAddress(doc.getLocation() != null && doc.getLocation().getAddress() != null
                            ? doc.getLocation().getAddress()
                            : "")
                    .latitude(lat)
                    .longitude(lng)
                    .status(doc.getStatus())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            bookingJpaRepository.save(entity);
            log.info("Successfully synced Booking [{}] to PostgreSQL table 'bookings'", doc.getId());

            // Write status audit log to PostgreSQL booking_status_logs
            BookingStatusLogEntity statusLog = BookingStatusLogEntity.builder()
                    .bookingId(doc.getId())
                    .previousStatus(previousStatus)
                    .newStatus(doc.getStatus())
                    .note(note != null ? note : ("Status changed to " + doc.getStatus()))
                    .createdAt(LocalDateTime.now())
                    .build();

            bookingStatusLogRepository.save(statusLog);
            log.info(
                    "Successfully saved audit log entry to PostgreSQL table 'booking_status_logs' for booking [{}] ({})",
                    doc.getId(), doc.getStatus());

        } catch (Exception e) {
            log.error("Failed to sync booking [{}] to PostgreSQL: {}", doc.getId(), e.getMessage(), e);
        }
    }
}
