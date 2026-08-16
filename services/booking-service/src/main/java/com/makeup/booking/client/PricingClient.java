package com.makeup.booking.client;

import com.makeup.booking.dto.PricingRequestDto;
import com.makeup.booking.dto.PricingResponseDto;
import com.makeup.common.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * OpenFeign Client giao tiếp Declarative HTTP tới pricing-service.
 * Dùng Strongly-Typed DTO (PricingRequestDto / PricingResponseDto) chuẩn Production.
 */
@FeignClient(name = "pricing-service", url = "${services.pricing-service.url:http://localhost:8084}")
public interface PricingClient {

    /**
     * Gọi API tính cước dịch vụ & di chuyển (/api/v1/pricing/calculate)
     */
    @PostMapping("/api/v1/pricing/calculate")
    ApiResponse<PricingResponseDto> calculateServiceFee(@RequestBody PricingRequestDto request);
}
