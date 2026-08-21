package com.makeup.booking.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Document(collection = "bookings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingDocument {

    @Id
    private String id;

    @Field("booking_code")
    private String bookingCode;

    private CustomerInfo customer;
    private MuaInfo mua;

    @Field("service_details")
    private ServiceDetailsInfo serviceDetails;

    private LocationInfo location;
    private PricingInfo pricing;

    private String notes;
    @Field("payment_method")
    private String paymentMethod; // "CASH", "E_WALLET", "VNPAY"
    private String status;

    @Field("state_history")
    private List<StateHistoryInfo> stateHistory;

    @CreatedDate
    @Field("created_at")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updated_at")
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerInfo {
        @Field("user_id")
        private Long userId;
        private String name;
        private String phone;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MuaInfo {
        @Field("user_id")
        private Long userId;
        private String name;
        private String phone;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceDetailsInfo {
        @Field("service_id")
        private Long serviceId;
        private String category;
        private String name;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocationInfo {
        private String type; // "Point"
        private List<Double> coordinates; // [lng, lat]
        private String address;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PricingInfo {
        @Field("base_price")
        private BigDecimal basePrice;

        @Field("moving_distance_km")
        private Double movingDistanceKm;

        @Field("moving_fee")
        private BigDecimal movingFee;

        @Field("surge_multiplier")
        private Double surgeMultiplier;

        @Field("total_fee")
        private BigDecimal totalFee;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StateHistoryInfo {
        private String status;
        private String timestamp;
    }
}
