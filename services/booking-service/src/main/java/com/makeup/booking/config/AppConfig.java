package com.makeup.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Cấu hình các Bean dùng chung cho booking-service.
 */
@Configuration
public class AppConfig {

    /**
     * Khởi tạo RestTemplate để thực hiện gọi API HTTP đồng bộ tới
     * pricing-service và location-service.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
