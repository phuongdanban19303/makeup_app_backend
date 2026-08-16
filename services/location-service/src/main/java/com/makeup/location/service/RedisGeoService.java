package com.makeup.location.service;

import com.makeup.location.dto.GpsLocationDto;
import com.makeup.location.dto.WorkerLocationStreamDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RedisGeoService {

    private final LocationService locationService;

    public RedisGeoService(LocationService locationService) {
        this.locationService = locationService;
    }

    public void updateMuaLocation(GpsLocationDto locationDto) {
        WorkerLocationStreamDto streamDto = WorkerLocationStreamDto.builder()
                .workerId(locationDto.getMuaId())
                .latitude(locationDto.getLatitude())
                .longitude(locationDto.getLongitude())
                .bookingId(locationDto.getBookingId())
                .timestamp(locationDto.getTimestamp())
                .build();
        locationService.processLocationStream(streamDto);
    }

    public List<String> findNearbyMuas(double latitude, double longitude, double radiusKm) {
        return locationService.findNearbyWorkers(latitude, longitude, radiusKm).stream()
                .map(w -> w.getWorkerId().toString())
                .toList();
    }
}
