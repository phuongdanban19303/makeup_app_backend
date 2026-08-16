package com.makeup.user.controller;

import com.makeup.common.response.ApiResponse;
import com.makeup.user.dto.MasterServiceResponseDto;
import com.makeup.user.service.MasterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/master-services")
@RequiredArgsConstructor
public class MasterServiceController {

    private final MasterService masterService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<MasterServiceResponseDto>>> getMasterServices(
            @RequestParam(required = false) String categoryName) {
        List<MasterServiceResponseDto> list = masterService.getAllMasterServices(categoryName);
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MasterServiceResponseDto>> createMasterService(
            @RequestBody MasterServiceResponseDto request) {
        MasterServiceResponseDto response = masterService.createMasterService(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Master service standard created successfully"));
    }
}
