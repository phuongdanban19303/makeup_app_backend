package com.makeup.booking.client;

import com.makeup.booking.dto.NearbyWorkerDto;
import com.makeup.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * OpenFeign Client giao tiếp Declarative HTTP tới location-service.
 * Dùng Strongly-Typed DTO (NearbyWorkerDto) chuẩn Production.
 */
@FeignClient(name = "location-service", url = "${services.location-service.url:http://localhost:8082}")
public interface LocationClient {

    /**
     * Truy vấn thợ trang điểm rảnh gần nhất trong bán kính radiusKm (/api/v1/location/nearby)
     */
    @GetMapping("/api/v1/location/nearby")
    ApiResponse<List<NearbyWorkerDto>> getNearbyMuas(
            @RequestParam("latitude") double latitude,
            @RequestParam("longitude") double longitude,
            @RequestParam("radiusKm") double radiusKm
    );

    /**
     * Bắn trực tiếp thông báo Đặt Lịch thời gian thực sang Location Service qua REST HTTP (Fail-safe backup)
     */
    @org.springframework.web.bind.annotation.PostMapping("/api/v1/location/notify-booking")
    ApiResponse<String> notifyBooking(@org.springframework.web.bind.annotation.RequestBody Object bookingEventDto);
}

