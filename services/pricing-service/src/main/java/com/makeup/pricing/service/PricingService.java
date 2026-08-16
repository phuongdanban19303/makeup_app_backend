package com.makeup.pricing.service;

import com.makeup.pricing.dto.PricingRequestDto;
import com.makeup.pricing.dto.PricingResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Service tính toán cước phí tự động (Dynamic Pricing Service).
 * <p>
 * CÔNG THỨC TÍNH PHÍ:
 * Tổng Cước Phí = ((Phí Gói Dịch Vụ Cốt Lõi + Phụ Phí Options) + Phí Di Chuyển Theo Quãng Đường) * Hệ Số Surge Multiplier.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingService {

    private static final String REDIS_SURGE_KEY = "pricing:surge:multiplier";
    private static final double DEFAULT_RATE_PER_KM = 10000.0; // 10.000 VND / km di chuyển
    private static final double DEFAULT_SURGE_MULTIPLIER = 1.0;

    private final StringRedisTemplate redisTemplate;

    /**
     * Tính toán chi tiết cước phí dịch vụ makeup, options chọn thêm & di chuyển.
     *
     * @param request DTO chứa mã gói, giá cơ bản, phụ phí options, quãng đường di chuyển và tọa độ
     * @return DTO chứa kết quả tính cước chi tiết
     */
    public PricingResponseDto calculateServiceFee(PricingRequestDto request) {
        double baseFee = (request != null && request.getBasePackageFee() > 0) ? request.getBasePackageFee() : 0.0;
        double optionsFee = (request != null && request.getOptionsFee() > 0) ? request.getOptionsFee() : 0.0;
        double packageSubtotal = baseFee + optionsFee;

        double distanceKm = (request != null && request.getDistanceInKm() > 0) ? request.getDistanceInKm() : 1.0;

        // 1. Tính phí di chuyển (Travel Distance Fee)
        double travelFee = distanceKm * DEFAULT_RATE_PER_KM;

        // 2. Đọc Surge Multiplier từ Redis Cache
        double surgeMultiplier = getSurgeMultiplierFromCache();

        // 3. Tính tổng phí cước
        double totalFee = (packageSubtotal + travelFee) * surgeMultiplier;

        log.info("Calculated pricing: BaseFee={}, OptionsFee={}, Subtotal={}, DistanceKm={}, TravelFee={}, SurgeMultiplier={}, TotalFee={}",
                baseFee, optionsFee, packageSubtotal, distanceKm, travelFee, surgeMultiplier, totalFee);

        return PricingResponseDto.builder()
                .servicePackageId(request != null ? request.getServicePackageId() : null)
                .basePackageFee(baseFee)
                .optionsFee(optionsFee)
                .packageSubtotal(packageSubtotal)
                .travelDistanceFee(travelFee)
                .surgeMultiplier(surgeMultiplier)
                .totalFee(totalFee)
                .currency("VND")
                .build();
    }

    /**
     * Truy vấn hệ số Surge Multiplier từ Redis Cache. Nếu không tìm thấy, mặc định trả về 1.0.
     */
    private double getSurgeMultiplierFromCache() {
        try {
            String surgeStr = redisTemplate.opsForValue().get(REDIS_SURGE_KEY);
            if (surgeStr != null) {
                return Double.parseDouble(surgeStr);
            }
        } catch (Exception e) {
            log.warn("Unable to fetch surge multiplier from Redis key '{}', using default multiplier 1.0", REDIS_SURGE_KEY, e);
        }
        return DEFAULT_SURGE_MULTIPLIER;
    }
}
