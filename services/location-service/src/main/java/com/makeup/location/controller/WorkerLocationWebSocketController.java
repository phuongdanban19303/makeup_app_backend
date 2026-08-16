package com.makeup.location.controller;

import com.makeup.location.dto.WorkerLocationStreamDto;
import com.makeup.location.service.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WorkerLocationWebSocketController {

    private final LocationService locationService;

    /**
     * WebSocket STOMP Handler tiếp nhận GPS telemetry stream gửi định kỳ 5s/lần từ
     * MUA Mobile App.
     * Destination: /app/location/stream
     */
    @MessageMapping("/location/stream")
    public void handleLocationStream(@Payload WorkerLocationStreamDto locationDto) {
        log.debug("Received WebSocket GPS stream from workerId {}: lat={}, lng={}",
                locationDto.getWorkerId(), locationDto.getLatitude(), locationDto.getLongitude());
        locationService.processLocationStream(locationDto);
    }
}
