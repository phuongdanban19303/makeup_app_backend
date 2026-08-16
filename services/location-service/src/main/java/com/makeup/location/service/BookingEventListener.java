package com.makeup.location.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Listener lắng nghe sự kiện từ Kafka Event Bus và phát thông báo Popup thời gian thực
 * thông qua WebSocket Gateway (STOMP Broker) tới ứng dụng MUA App & Customer App.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingEventListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(Object rawPayload) {
        if (rawPayload == null) return new HashMap<>();
        if (rawPayload instanceof Map) {
            return (Map<String, Object>) rawPayload;
        }
        if (rawPayload instanceof String) {
            try {
                return objectMapper.readValue((String) rawPayload, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.error("Failed to parse JSON string payload from Kafka", e);
            }
        }
        return new HashMap<>();
    }

    /**
     * Lắng nghe sự kiện "booking-requested-topic" (Yêu cầu đặt lịch mới được ghép cho Thợ)
     */
    @KafkaListener(topics = "booking-requested-topic", groupId = "location-group")
    public void handleBookingRequestedEvent(Object rawPayload) {
        log.info("\n=======================================================");
        log.info(">>> [KAFKA-CONSUMER] Received event [booking-requested-topic]: {}", rawPayload);
        log.info("=======================================================");

        Map<String, Object> event = parsePayload(rawPayload);

        Object muaIdObj = event.get("muaId");
        if (muaIdObj == null) {
            muaIdObj = event.get("workerId");
        }

        if (muaIdObj != null) {
            String destination1 = "/topic/mua/" + muaIdObj + "/alerts";
            String destination2 = "/topic/worker/" + muaIdObj;
            log.info(">>> [KAFKA-CONSUMER] Pushing WebSocket Booking Alert Popup to MUA destinations [{}] and [{}]", destination1, destination2);

            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "NEW_BOOKING_REQUEST");
            payload.put("title", "Có đơn đặt lịch trang điểm mới!");
            payload.put("message", "Khách hàng gửi yêu cầu ghép đơn gần bạn. Bạn có muốn nhận ca?");
            payload.put("bookingId", event.getOrDefault("bookingId", ""));
            payload.put("bookingCode", event.getOrDefault("bookingCode", ""));
            payload.put("serviceName", event.getOrDefault("serviceName", "Makeup Dịch Vụ Khách Hàng"));
            payload.put("address", event.getOrDefault("address", "Địa chỉ khách hàng"));
            payload.put("customerAddress", event.getOrDefault("address", "Địa chỉ khách hàng"));
            payload.put("totalPrice", event.getOrDefault("totalFee", 0));
            payload.put("customerName", event.getOrDefault("customerName", "Khách Hàng"));
            payload.put("customerPhone", event.getOrDefault("customerPhone", ""));
            payload.put("payload", event);

            // Send to BOTH STOMP topics so worker app popup opens immediately
            messagingTemplate.convertAndSend(destination1, payload);
            messagingTemplate.convertAndSend(destination2, payload);
            log.info(">>> [KAFKA-CONSUMER] SUCCESS: Sent WebSocket alert for MUA [{}]", muaIdObj);
        } else {
            log.warn(">>> [KAFKA-CONSUMER] WARNING: Cannot push WebSocket popup: muaId / workerId is null in Kafka event");
        }
    }

    /**
     * Lắng nghe sự kiện "booking-accepted-topic" (Thợ đã chấp nhận ca)
     */
    @KafkaListener(topics = "booking-accepted-topic", groupId = "location-group")
    public void handleBookingAcceptedEvent(Object rawPayload) {
        log.info("\n=======================================================");
        log.info(">>> [KAFKA-CONSUMER] Received event [booking-accepted-topic]: {}", rawPayload);
        log.info("=======================================================");

        Map<String, Object> event = parsePayload(rawPayload);

        Object customerIdObj = event.get("customerId");
        if (customerIdObj != null) {
            String destination = "/topic/customer/" + customerIdObj + "/status";
            log.info(">>> [KAFKA-CONSUMER] Pushing WebSocket Status Update to Customer destination [{}]", destination);

            messagingTemplate.convertAndSend(destination, Map.of(
                    "type", "BOOKING_ACCEPTED",
                    "title", "Thợ đã nhận đơn!",
                    "message", "Thợ trang điểm đã chấp nhận đơn và đang chuẩn bị di chuyển.",
                    "payload", event
            ));
        }
    }

    /**
     * Lắng nghe sự kiện "booking-status-updated-topic" (Cập nhật vòng đời đơn)
     */
    @KafkaListener(topics = "booking-status-updated-topic", groupId = "location-group")
    public void handleBookingStatusUpdatedEvent(Object rawPayload) {
        log.info("\n=======================================================");
        log.info(">>> [KAFKA-CONSUMER] Received event [booking-status-updated-topic]: {}", rawPayload);
        log.info("=======================================================");

        Map<String, Object> event = parsePayload(rawPayload);

        Object customerIdObj = event.get("customerId");
        Object statusObj = event.get("status");
        if (customerIdObj != null && statusObj != null) {
            String status = statusObj.toString();
            String destination = "/topic/customer/" + customerIdObj + "/status";
            log.info(">>> [KAFKA-CONSUMER] Pushing WebSocket Status Update [{}] to Customer destination [{}]", status, destination);

            String title = "Cập nhật trạng thái chuyến đi";
            String message = "Đơn đặt lịch của bạn đã được cập nhật trạng thái mới: " + status;

            switch (status) {
                case "MUA_MOVING":
                    title = "Thợ đang di chuyển!";
                    message = "Thợ trang điểm đang trên đường di chuyển đến địa chỉ của bạn.";
                    break;
                case "ARRIVED":
                    title = "Thợ đã đến nơi!";
                    message = "Thợ trang điểm đã đến đúng địa chỉ nhà của bạn.";
                    break;
                case "MAKING_UP":
                    title = "Đang trang điểm!";
                    message = "Thợ trang điểm đang tiến hành thực hiện dịch vụ makeup.";
                    break;
                case "COMPLETED":
                    title = "Dịch vụ hoàn thành!";
                    message = "Cảm ơn bạn đã sử dụng dịch vụ! Đơn hàng đã hoàn thành xuất sắc.";
                    break;
                case "CANCELLED":
                    title = "Đơn hàng bị hủy!";
                    message = "Đơn đặt lịch đã bị hủy. Vui lòng liên hệ tổng đài hoặc thử đặt lại.";
                    break;
            }

            messagingTemplate.convertAndSend(destination, Map.of(
                    "type", "BOOKING_STATUS_CHANGE",
                    "status", status,
                    "title", title,
                    "message", message,
                    "payload", event
            ));
        }
    }
}
