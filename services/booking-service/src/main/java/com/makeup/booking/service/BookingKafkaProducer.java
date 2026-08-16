package com.makeup.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.makeup.booking.dto.BookingEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Service phát hành các sự kiện (Publish Events) tới Kafka Event Bus.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingKafkaProducer {

    public static final String TOPIC_BOOKING_REQUESTED = "booking-requested-topic";
    public static final String TOPIC_BOOKING_ACCEPTED = "booking-accepted-topic";
    public static final String TOPIC_BOOKING_REJECTED = "booking-rejected-topic";
    public static final String TOPIC_BOOKING_STATUS_UPDATED = "booking-status-updated-topic";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Bắn sự kiện "Booking_Requested" tới Kafka khi đã chọn và khóa thành công thợ candidate.
     */
    public void publishBookingRequested(BookingEventDto event) {
        log.info(">>> [KAFKA-PRODUCER] Publishing event [{}] to topic [{}] for MUA [{}]",
                event.getEventType(), TOPIC_BOOKING_REQUESTED, event.getMuaId());
        try {
            String jsonPayload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC_BOOKING_REQUESTED, event.getBookingId(), jsonPayload);
            log.info(">>> [KAFKA-PRODUCER] SUCCESS: Sent event [{}] to Kafka topic [{}]", event.getBookingId(), TOPIC_BOOKING_REQUESTED);
        } catch (Exception e) {
            log.error(">>> [KAFKA-PRODUCER] ERROR: Failed to publish Kafka event [{}]", TOPIC_BOOKING_REQUESTED, e);
        }
    }

    /**
     * Bắn sự kiện "Booking_Accepted" tới Kafka khi Thợ makeup chấp nhận ca.
     */
    public void publishBookingAccepted(BookingEventDto event) {
        log.info(">>> [KAFKA-PRODUCER] Publishing event [{}] to topic [{}] for Customer [{}]",
                event.getEventType(), TOPIC_BOOKING_ACCEPTED, event.getCustomerId());
        try {
            String jsonPayload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC_BOOKING_ACCEPTED, event.getBookingId(), jsonPayload);
            log.info(">>> [KAFKA-PRODUCER] SUCCESS: Sent accept event [{}] to Kafka topic [{}]", event.getBookingId(), TOPIC_BOOKING_ACCEPTED);
        } catch (Exception e) {
            log.error(">>> [KAFKA-PRODUCER] ERROR: Failed to publish Kafka event [{}]", TOPIC_BOOKING_ACCEPTED, e);
        }
    }

    /**
     * Bắn sự kiện "Booking_Rejected" tới Kafka khi Thợ từ chối đơn.
     */
    public void publishBookingRejected(BookingEventDto event) {
        log.info(">>> [KAFKA-PRODUCER] Publishing event [{}] to topic [{}] for MUA [{}]",
                event.getEventType(), TOPIC_BOOKING_REJECTED, event.getMuaId());
        try {
            String jsonPayload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC_BOOKING_REJECTED, event.getBookingId(), jsonPayload);
            log.info(">>> [KAFKA-PRODUCER] SUCCESS: Sent reject event [{}] to Kafka topic [{}]", event.getBookingId(), TOPIC_BOOKING_REJECTED);
        } catch (Exception e) {
            log.error(">>> [KAFKA-PRODUCER] ERROR: Failed to publish Kafka event [{}]", TOPIC_BOOKING_REJECTED, e);
        }
    }

    /**
     * Bắn sự kiện cập nhật trạng thái vòng đời chuyến đi (Order State Machine) tới Kafka
     */
    public void publishBookingStatusUpdated(BookingEventDto event) {
        log.info(">>> [KAFKA-PRODUCER] Publishing status update event [{}] -> status [{}] to topic [{}]",
                event.getEventType(), event.getStatus(), TOPIC_BOOKING_STATUS_UPDATED);
        try {
            String jsonPayload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC_BOOKING_STATUS_UPDATED, event.getBookingId(), jsonPayload);
            log.info(">>> [KAFKA-PRODUCER] SUCCESS: Sent status update event [{}] to Kafka topic [{}]", event.getBookingId(), TOPIC_BOOKING_STATUS_UPDATED);
        } catch (Exception e) {
            log.error(">>> [KAFKA-PRODUCER] ERROR: Failed to publish Kafka event [{}]", TOPIC_BOOKING_STATUS_UPDATED, e);
        }
    }
}
