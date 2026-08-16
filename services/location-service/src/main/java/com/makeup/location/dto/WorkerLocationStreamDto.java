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
public class WorkerLocationStreamDto {

    @JsonAlias({"muaId", "mua_id", "worker_id"})
    private Long workerId;

    @JsonAlias({"lat"})
    private double latitude;

    @JsonAlias({"lng", "lon"})
    private double longitude;

    private String bookingId;

    private Long timestamp;
}
