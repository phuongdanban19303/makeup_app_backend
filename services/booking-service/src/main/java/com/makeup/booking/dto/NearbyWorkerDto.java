package com.makeup.booking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO đại diện cho thông tin Thợ trang điểm gần nhất từ Location Service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NearbyWorkerDto {
    private Long workerId;
    private String fullName;
    private String avatarUrl;
    private Double rating;
    private Integer totalCompletedJobs;
    private String currentStatus;
    private double latitude;
    private double longitude;
    private double distanceKm;
    private List<Object> services;
}
