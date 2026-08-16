package com.makeup.payment.client;

import com.makeup.common.response.ApiResponse;
import com.makeup.payment.dto.BookingResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * OpenFeign Client kết nối trực tiếp từ payment-service sang booking-service (Port 8081).
 * Giúp payment-service tự động truy vấn chi tiết đơn hàng (Giá tiền totalFee, status, customerId, muaId)
 * trực tiếp từ booking-service khi cần đối soát hoặc khi Kafka event chưa có đủ thông tin.
 */
@FeignClient(name = "booking-service", url = "${payment.booking-service.url:http://localhost:8081}")
public interface BookingClient {

    @GetMapping("/api/v1/bookings/{bookingId}")
    ApiResponse<BookingResponseDto> getBookingById(@PathVariable("bookingId") String bookingId);
}
