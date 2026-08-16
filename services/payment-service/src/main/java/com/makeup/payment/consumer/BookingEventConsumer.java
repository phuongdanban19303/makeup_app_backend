package com.makeup.payment.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.makeup.common.response.ApiResponse;
import com.makeup.payment.client.BookingClient;
import com.makeup.payment.dto.BookingResponseDto;
import com.makeup.payment.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Kafka Event Consumer lắng nghe các sự kiện trạng thái đơn từ `booking-service`.
 * Tự động đồng bộ giá tiền đơn hàng qua Kafka Event Payload hoặc truy vấn trực tiếp qua FeignClient (BookingClient).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventConsumer {

    private final WalletService walletService;
    private final BookingClient bookingClient;
    private final ObjectMapper objectMapper;

    /**
     * Lắng nghe các topic sự kiện từ booking-service qua Kafka Event Bus.
     */
    @KafkaListener(topics = {
            "booking-status-updated-topic",
            "booking-requested-topic",
            "booking-accepted-topic",
            "booking-rejected-topic"
    }, groupId = "payment-service-group")
    public void consumeBookingEvent(String eventJson) {
        log.info(">>> [KAFKA-CONSUMER] Đã nhận event booking từ Kafka: {}", eventJson);
        try {
            // BƯỚC 1: Parse dữ liệu JSON từ thông điệp Kafka
            JsonNode root = objectMapper.readTree(eventJson);

            String bookingId = root.has("bookingId") ? root.get("bookingId").asText() : null;
            String customerId = root.has("customerId") ? root.get("customerId").asText() : null;
            String muaId = root.has("muaId") ? root.get("muaId").asText() : null;
            String status = root.has("status") ? root.get("status").asText() : null;
            String paymentMethod = root.has("paymentMethod") ? root.get("paymentMethod").asText() : "CASH";

            BigDecimal totalFee = BigDecimal.ZERO;
            if (root.has("totalFee") && !root.get("totalFee").isNull()) {
                totalFee = new BigDecimal(root.get("totalFee").asText());
            }

            if (bookingId == null) {
                log.warn(">>> [KAFKA-CONSUMER] Thiếu bookingId trong thông điệp Kafka, bỏ qua.");
                return;
            }

            // BƯỚC 2: TỰ ĐỘNG TRUY VẤN SANG BOOKING-SERVICE (nếu thiếu giá tiền totalFee hoặc thiếu muaId/customerId)
            if (totalFee.compareTo(BigDecimal.ZERO) <= 0 || customerId == null || customerId.isBlank() || muaId == null) {
                log.info(">>> [KAFKA-CONSUMER] Thông tin thiếu hoặc totalFee <= 0. Đang gọi FeignClient sang booking-service để lấy giá gốc...");
                try {
                    ApiResponse<BookingResponseDto> bookingResponse = bookingClient.getBookingById(bookingId);
                    if (bookingResponse != null && bookingResponse.getData() != null) {
                        BookingResponseDto dto = bookingResponse.getData();
                        if (totalFee.compareTo(BigDecimal.ZERO) <= 0 && dto.getTotalFee() != null) {
                            totalFee = dto.getTotalFee();
                        }
                        if (customerId == null && dto.getCustomerId() != null) {
                            customerId = String.valueOf(dto.getCustomerId());
                        }
                        if (muaId == null && dto.getMuaId() != null) {
                            muaId = String.valueOf(dto.getMuaId());
                        }
                        if (status == null && dto.getStatus() != null) {
                            status = dto.getStatus();
                        }
                        log.info(">>> [KAFKA-CONSUMER] Kết nối booking-service thành công! Lấy được giá đơn hàng #{}: [{}] VNĐ", bookingId, totalFee);
                    }
                } catch (Exception ex) {
                    log.warn(">>> [KAFKA-CONSUMER] Không thể gọi FeignClient sang booking-service (Có thể booking-service chưa chạy). Sử dụng thông tin từ Kafka.", ex);
                }
            }

            log.info(">>> [KAFKA-CONSUMER] Đang xử lý tài chính đơn [{}]: Trạng thái [{}], Phương thức [{}], Khách [{}], Thợ [{}], Giá trị [{}] VNĐ",
                    bookingId, status, paymentMethod, customerId, muaId, totalFee);

            // BƯỚC 3: Phân loại nghiệp vụ tài chính theo trạng thái đơn hàng và phương thức thanh toán
            if ("COMPLETED".equalsIgnoreCase(status) || "FINISHED".equalsIgnoreCase(status)) {
                if (muaId == null || muaId.isBlank()) {
                    log.error(">>> [ERROR] Không thể giải phóng tiền đơn [{}] vì thiếu thông tin Thợ makeup!", bookingId);
                    return;
                }
                walletService.escrowRelease(bookingId, customerId, muaId, totalFee, paymentMethod);
                log.info(">>> [KAFKA-CONSUMER] Escrow Release [{}] xử lý thành công cho đơn [{}]", paymentMethod, bookingId);

            } else if ("CANCELLED".equalsIgnoreCase(status) || "REJECTED".equalsIgnoreCase(status)) {
                walletService.escrowRefund(bookingId, customerId, totalFee, paymentMethod);
                log.info(">>> [KAFKA-CONSUMER] Escrow Refund [{}] xử lý thành công cho đơn [{}]", paymentMethod, bookingId);

            } else if ("ACCEPTED".equalsIgnoreCase(status) || "REQUESTED".equalsIgnoreCase(status) || "CONFIRMED".equalsIgnoreCase(status)) {
                walletService.escrowHold(bookingId, customerId, totalFee, paymentMethod);
                log.info(">>> [KAFKA-CONSUMER] Escrow Hold [{}] xử lý thành công cho đơn [{}]", paymentMethod, bookingId);
            }

        } catch (Exception e) {
            log.error(">>> [KAFKA-CONSUMER] Lỗi khi xử lý sự kiện Kafka Booking Event", e);
        }
    }
}
