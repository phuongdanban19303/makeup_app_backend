package com.makeup.location.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GpsLocationDto {

    @JsonAlias({"workerId", "worker_id", "mua_id"})
    private Long muaId;

    @JsonAlias({"lat"})
    private double latitude;

    @JsonAlias({"lng", "lon"})
    private double longitude;

    private String bookingId;

    private Long timestamp;
}
