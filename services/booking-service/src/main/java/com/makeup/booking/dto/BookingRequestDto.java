package com.makeup.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRequestDto {
    private String bookingId;
    private String customerId;
    private String muaId;
    private String servicePackageId;
    private double customerLat;
    private double customerLng;
    private String address;
    private double estimatedPrice;
    private String status;
}
