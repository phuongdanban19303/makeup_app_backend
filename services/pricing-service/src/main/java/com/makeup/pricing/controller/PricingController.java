package com.makeup.pricing.controller;

import com.makeup.common.response.ApiResponse;
import com.makeup.pricing.dto.PricingRequestDto;
import com.makeup.pricing.dto.PricingResponseDto;
import com.makeup.pricing.service.PricingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller cho Pricing Service.
 */
@RestController
@RequestMapping("/api/v1/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final PricingService pricingService;

    @PostMapping("/calculate")
    public ResponseEntity<ApiResponse<PricingResponseDto>> calculateServiceFee(@RequestBody PricingRequestDto request) {
        PricingResponseDto response = pricingService.calculateServiceFee(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
