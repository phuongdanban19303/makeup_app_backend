package com.makeup.location.controller;

import com.makeup.common.response.ApiResponse;
import com.makeup.location.dto.GpsLocationDto;
import com.makeup.location.dto.NearbyWorkerDto;
import com.makeup.location.dto.WorkerLocationStreamDto;
import com.makeup.location.service.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/location")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    @PostMapping("/stream")
    public ResponseEntity<ApiResponse<Void>> updateGpsLocation(
            @AuthenticationPrincipal String currentUserId,
            @RequestBody GpsLocationDto locationDto) {
        Long workerId = (currentUserId != null && !currentUserId.isBlank() && !"anonymousUser".equalsIgnoreCase(currentUserId)) 
                ? Long.parseLong(currentUserId) 
                : locationDto.getMuaId();

        WorkerLocationStreamDto streamDto = WorkerLocationStreamDto.builder()
                .workerId(workerId)
                .latitude(locationDto.getLatitude())
                .longitude(locationDto.getLongitude())
                .bookingId(locationDto.getBookingId())
                .timestamp(locationDto.getTimestamp())
                .build();
        locationService.processLocationStream(streamDto);
        return ResponseEntity.ok(ApiResponse.success(null, "GPS location stream updated in Redis & PostgreSQL"));
    }

    @GetMapping("/nearby")
    public ResponseEntity<ApiResponse<List<NearbyWorkerDto>>> getNearbyMuas(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "5.0") double radiusKm,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) List<String> requiredSubServices) {
        List<NearbyWorkerDto> nearbyWorkers = locationService.findNearbyWorkers(latitude, longitude, radiusKm, category, requiredSubServices);
        return ResponseEntity.ok(ApiResponse.success(nearbyWorkers));
    }

    @DeleteMapping("/offline")
    public ResponseEntity<ApiResponse<Void>> removeGpsLocation(
            @AuthenticationPrincipal String currentUserId,
            @RequestParam(required = false) Long workerId) {
        Long targetId = (currentUserId != null && !currentUserId.isBlank() && !"anonymousUser".equalsIgnoreCase(currentUserId))
                ? Long.parseLong(currentUserId)
                : workerId;
        locationService.removeWorkerLocation(targetId);
        return ResponseEntity.ok(ApiResponse.success(null, "GPS location removed successfully from Redis"));
    }

    @DeleteMapping("/worker/{workerId}")
    public ResponseEntity<ApiResponse<Void>> removeGpsLocationByWorkerId(@PathVariable Long workerId) {
        locationService.removeWorkerLocation(workerId);
        return ResponseEntity.ok(ApiResponse.success(null, "GPS location removed successfully from Redis"));
    }

    @PostMapping("/notify-booking")
    public ResponseEntity<ApiResponse<String>> notifyBooking(@RequestBody java.util.Map<String, Object> event) {
        log.info("\n=======================================================");
        log.info(">>> [LOCATION-SERVICE] API POST /api/v1/location/notify-booking RECEIVED");
        log.info(">>> Real Booking Event payload: {}", event);
        log.info("=======================================================");

        Object muaIdObj = event.get("muaId");
        if (muaIdObj == null) {
            muaIdObj = event.get("workerId");
        }
        Object customerIdObj = event.get("customerId");
        Object bookingIdObj = event.get("bookingId");
        Object statusObj = event.get("status");

        // 1. Notify MUA Worker if new booking or worker alert
        if (muaIdObj != null) {
            String destination1 = "/topic/mua/" + muaIdObj + "/alerts";
            String destination2 = "/topic/worker/" + muaIdObj;

            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("type", "NEW_BOOKING_REQUEST");
            payload.put("title", "Có đơn đặt lịch trang điểm mới!");
            payload.put("message", "Khách hàng gửi yêu cầu ghép đơn gần bạn. Bạn có muốn nhận ca?");
            payload.put("bookingId", event.getOrDefault("bookingId", ""));
            payload.put("bookingCode", event.getOrDefault("bookingCode", ""));
            payload.put("serviceName", event.getOrDefault("serviceName", ""));
            payload.put("address", event.getOrDefault("address", ""));
            payload.put("customerAddress", event.getOrDefault("address", ""));
            payload.put("totalPrice", event.getOrDefault("totalFee", 0));
            payload.put("customerName", event.getOrDefault("customerName", ""));
            payload.put("customerPhone", event.getOrDefault("customerPhone", ""));
            payload.put("payload", event);

            messagingTemplate.convertAndSend(destination1, payload);
            messagingTemplate.convertAndSend(destination2, payload);
            log.info(">>> [LOCATION-SERVICE] SUCCESS: Sent direct STOMP alert for MUA [{}]", muaIdObj);
        }

        // 2. Notify Customer if status update (e.g. ACCEPTED, MUA_MOVING, ARRIVED, MAKING_UP, COMPLETED)
        if (customerIdObj != null || bookingIdObj != null) {
            String status = statusObj != null ? statusObj.toString() : "ACCEPTED";
            java.util.Map<String, Object> statusPayload = new java.util.HashMap<>();
            statusPayload.put("type", status.equals("ACCEPTED") ? "BOOKING_ACCEPTED" : "BOOKING_STATUS_CHANGE");
            statusPayload.put("status", status);
            statusPayload.put("title", "Cập nhật trạng thái ca đặt!");
            statusPayload.put("message", "Trạng thái đơn hàng của bạn hiện tại là: " + status);
            statusPayload.put("payload", event);

            if (customerIdObj != null) {
                String custTopic = "/topic/customer/" + customerIdObj + "/status";
                messagingTemplate.convertAndSend(custTopic, statusPayload);
                log.info(">>> [LOCATION-SERVICE] SUCCESS: Sent STOMP status update [{}] to Customer topic [{}]", status, custTopic);
            }
            if (bookingIdObj != null) {
                String bookingTopic = "/topic/booking/" + bookingIdObj;
                messagingTemplate.convertAndSend(bookingTopic, statusPayload);
                log.info(">>> [LOCATION-SERVICE] SUCCESS: Sent STOMP status update [{}] to Booking topic [{}]", status, bookingTopic);
            }
        }

        return ResponseEntity.ok(ApiResponse.success("Direct WebSocket popup & status update triggered successfully"));
    }
}

