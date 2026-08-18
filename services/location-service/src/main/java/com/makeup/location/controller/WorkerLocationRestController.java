package com.makeup.location.controller;

import com.makeup.common.response.ApiResponse;
import com.makeup.location.dto.NearbyWorkerDto;
import com.makeup.location.dto.WorkerLocationStreamDto;
import com.makeup.location.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workers")
@RequiredArgsConstructor
@Tag(name = "Worker Location Telemetry", description = "REST APIs cho luồng định vị GPS & Tìm kiếm thợ lân cận")
public class WorkerLocationRestController {

    private final LocationService locationService;

    /**
     * REST API tìm danh sách Thợ (MUA) trong bán kính X km xung quanh vị trí khách hàng đặt ca.
     * Sử dụng GEOSEARCH của Redis GEO.
     */
    @GetMapping("/nearby")
    @Operation(summary = "Tìm danh sách thợ gần nhất", description = "Sử dụng lệnh GEOSEARCH trong Redis GEO để truy vấn danh sách thợ ONLINE trong bán kính X km, hỗ trợ lọc theo Category & Kỹ năng sub-services")
    public ResponseEntity<ApiResponse<List<NearbyWorkerDto>>> getNearbyWorkers(
            @Parameter(description = "Vĩ độ khách hàng (Latitude)", example = "10.776889") @RequestParam(defaultValue = "10.776889") double latitude,
            @Parameter(description = "Kinh độ khách hàng (Longitude)", example = "106.700806") @RequestParam(defaultValue = "106.700806") double longitude,
            @Parameter(description = "Bán kính tìm kiếm tính theo km", example = "5.0") @RequestParam(defaultValue = "5.0") double radiusKm,
            @Parameter(description = "Danh mục yêu cầu (e.g. BRIDAL, EVENT, STAGE)") @RequestParam(required = false) String category,
            @Parameter(description = "Danh sách sub-services/kỹ năng yêu cầu (e.g. MAKEUP_FACE, HAIR_STYLING)") @RequestParam(required = false) List<String> requiredSubServices) {

        List<NearbyWorkerDto> nearbyWorkers = locationService.findNearbyWorkers(latitude, longitude, radiusKm, category, requiredSubServices);
        return ResponseEntity.ok(ApiResponse.success(nearbyWorkers, "Tìm kiếm danh sách thợ lân cận thành công"));
    }

    /**
     * REST API cho phép push tọa độ GPS qua HTTP POST (Fallback khi không dùng WebSocket STOMP)
     */
    @PostMapping("/location/stream")
    @Operation(summary = "Push stream GPS (REST Fallback)", description = "Cập nhật vị trí thời gian thực qua HTTP POST")
    public ResponseEntity<ApiResponse<Void>> updateLocationViaRest(
            @AuthenticationPrincipal String currentUserId,
            @RequestBody WorkerLocationStreamDto locationDto) {
        if (currentUserId != null && !currentUserId.isBlank() && !"anonymousUser".equalsIgnoreCase(currentUserId)) {
            locationDto.setWorkerId(Long.parseLong(currentUserId));
        }
        locationService.processLocationStream(locationDto);
        return ResponseEntity.ok(ApiResponse.success(null, "Cập nhật vị trí GPS thành công"));
    }

    /**
     * REST API xóa vị trí thợ khỏi GPS khi thợ ấn "Tắt hoạt động" (Go Offline)
     */
    @DeleteMapping("/location")
    @Operation(summary = "Xóa vị trí thợ khỏi GPS", description = "Xóa tọa độ của thợ hiện tại khỏi Redis GEO khi bấm Tắt hoạt động")
    public ResponseEntity<ApiResponse<Void>> removeWorkerLocation(
            @AuthenticationPrincipal String currentUserId,
            @RequestParam(required = false) Long workerId) {
        Long targetId = (currentUserId != null && !currentUserId.isBlank() && !"anonymousUser".equalsIgnoreCase(currentUserId))
                ? Long.parseLong(currentUserId)
                : workerId;
        locationService.removeWorkerLocation(targetId);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa vị trí GPS của thợ thành công"));
    }

    @DeleteMapping("/{workerId}/location")
    @Operation(summary = "Xóa vị trí thợ theo ID khỏi GPS")
    public ResponseEntity<ApiResponse<Void>> removeWorkerLocationById(@PathVariable Long workerId) {
        locationService.removeWorkerLocation(workerId);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa vị trí GPS của thợ thành công"));
    }
}
