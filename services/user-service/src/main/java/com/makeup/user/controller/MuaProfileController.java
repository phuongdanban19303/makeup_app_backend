package com.makeup.user.controller;

import com.makeup.common.exception.AppException;
import com.makeup.common.exception.ErrorCode;
import com.makeup.common.response.ApiResponse;
import com.makeup.user.dto.*;
import com.makeup.user.service.MuaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/mua")
@RequiredArgsConstructor
public class MuaProfileController {

    private final MuaService muaService;

    // ==========================================
    // MUA PROFILE & IDENTITY CARD
    // ==========================================

    @GetMapping("/{muaId}/profile")
    public ResponseEntity<ApiResponse<MuaFullProfileResponseDto>> getMuaProfile(@PathVariable Long muaId) {
        MuaFullProfileResponseDto response = muaService.getMuaProfile(muaId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{muaId}/profile")
    public ResponseEntity<ApiResponse<MuaFullProfileResponseDto>> updateMuaProfile(
            @PathVariable Long muaId,
            @AuthenticationPrincipal String currentUserId,
            @RequestBody UpdateMuaProfileRequestDto request) {
        Long targetMuaId = resolveAuthorizedMuaId(muaId, currentUserId);
        MuaFullProfileResponseDto response = muaService.updateProfile(targetMuaId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Profile updated successfully"));
    }

    @PostMapping(value = "/{muaId}/upload-identity-card", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadIdentityCard(
            @PathVariable Long muaId,
            @AuthenticationPrincipal String currentUserId,
            @RequestParam("file") MultipartFile file) {
        Long targetMuaId = resolveAuthorizedMuaId(muaId, currentUserId);
        String identityCardUrl = muaService.uploadIdentityCard(targetMuaId, file);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("muaId", String.valueOf(targetMuaId), "identityCardUrl", identityCardUrl),
                "Identity card (CCCD) uploaded successfully"
        ));
    }

    @PostMapping("/summaries")
    public ResponseEntity<ApiResponse<List<MuaSummaryDto>>> getMuaSummaries(@RequestBody List<Long> muaIds) {
        List<MuaSummaryDto> response = muaService.getMuaSummaries(muaIds);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ==========================================
    // MUA SERVICES MANAGEMENT (DÙNG TRỰC TIẾP PROVIDER_SERVICES)
    // ==========================================

    @PostMapping("/{muaId}/services")
    public ResponseEntity<ApiResponse<ProviderServiceResponseDto>> createService(
            @PathVariable Long muaId,
            @AuthenticationPrincipal String currentUserId,
            @Valid @RequestBody ProviderServiceRequestDto request) {
        Long targetMuaId = resolveAuthorizedMuaId(muaId, currentUserId);
        ProviderServiceResponseDto response = muaService.createService(targetMuaId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Makeup service created successfully"));
    }

    @GetMapping("/{muaId}/services")
    public ResponseEntity<ApiResponse<List<ProviderServiceResponseDto>>> getMuaServices(
            @PathVariable Long muaId,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        List<ProviderServiceResponseDto> services = muaService.getMuaServices(muaId, includeInactive);
        return ResponseEntity.ok(ApiResponse.success(services));
    }

    @PutMapping("/{muaId}/services/{serviceId}")
    public ResponseEntity<ApiResponse<ProviderServiceResponseDto>> updateService(
            @PathVariable Long muaId,
            @PathVariable Long serviceId,
            @AuthenticationPrincipal String currentUserId,
            @Valid @RequestBody ProviderServiceRequestDto request) {
        Long targetMuaId = resolveAuthorizedMuaId(muaId, currentUserId);
        ProviderServiceResponseDto response = muaService.updateService(targetMuaId, serviceId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Makeup service updated successfully"));
    }

    @PatchMapping("/{muaId}/services/{serviceId}/toggle-status")
    public ResponseEntity<ApiResponse<ProviderServiceResponseDto>> toggleServiceStatus(
            @PathVariable Long muaId,
            @PathVariable Long serviceId,
            @AuthenticationPrincipal String currentUserId,
            @RequestParam(required = false) Boolean isActive) {
        Long targetMuaId = resolveAuthorizedMuaId(muaId, currentUserId);
        ProviderServiceResponseDto response = muaService.toggleServiceStatus(targetMuaId, serviceId, isActive);
        return ResponseEntity.ok(ApiResponse.success(response, "Service package status updated successfully"));
    }


    @DeleteMapping("/{muaId}/services/{serviceId}")
    public ResponseEntity<ApiResponse<Void>> deleteService(
            @PathVariable Long muaId,
            @PathVariable Long serviceId,
            @AuthenticationPrincipal String currentUserId,
            @RequestParam(defaultValue = "false") boolean permanent) {
        Long targetMuaId = resolveAuthorizedMuaId(muaId, currentUserId);
        muaService.deleteService(targetMuaId, serviceId, permanent);
        String msg = permanent ? "Service package deleted permanently" : "Service package hidden successfully";
        return ResponseEntity.ok(ApiResponse.success(null, msg));
    }

    // ==========================================
    // DYNAMIC BUNDLE SERVICES (ROW-BASED OPTIONS)
    // ==========================================

    @PostMapping("/{muaId}/bundle-services")
    public ResponseEntity<ApiResponse<ProviderServiceResponseDto>> createBundleService(
            @PathVariable Long muaId,
            @AuthenticationPrincipal String currentUserId,
            @Valid @RequestBody ProviderServiceRequestDto request) {
        Long targetMuaId = resolveAuthorizedMuaId(muaId, currentUserId);
        ProviderServiceResponseDto response = muaService.createBundleService(targetMuaId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Dynamic bundle service package created successfully"));
    }

    @GetMapping("/{muaId}/bundle-services")
    public ResponseEntity<ApiResponse<List<ProviderServiceResponseDto>>> getBundleServices(
            @PathVariable Long muaId,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        List<ProviderServiceResponseDto> response = muaService.getBundleServices(muaId, includeInactive);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{muaId}/bundle-services/{serviceId}")
    public ResponseEntity<ApiResponse<ProviderServiceResponseDto>> updateBundleService(
            @PathVariable Long muaId,
            @PathVariable Long serviceId,
            @AuthenticationPrincipal String currentUserId,
            @Valid @RequestBody ProviderServiceRequestDto request) {
        Long targetMuaId = resolveAuthorizedMuaId(muaId, currentUserId);
        ProviderServiceResponseDto response = muaService.updateBundleService(targetMuaId, serviceId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Dynamic bundle service package updated successfully"));
    }

    // ==========================================
    // MUA PORTFOLIO MANAGEMENT
    // ==========================================

    @PostMapping(value = "/{muaId}/portfolio", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public ResponseEntity<ApiResponse<MuaPortfolioResponseDto>> addPortfolioImageMultipart(
            @PathVariable Long muaId,
            @AuthenticationPrincipal String currentUserId,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "url", required = false) String url,
            @RequestParam(value = "imageUrl", required = false) String imageUrl,
            @RequestParam(value = "caption", required = false) String caption) {
        Long targetMuaId = resolveAuthorizedMuaId(muaId, currentUserId);
        String finalUrl = imageUrl != null && !imageUrl.isBlank() ? imageUrl : url;
        MuaPortfolioResponseDto response = muaService.addPortfolioImage(targetMuaId, file, finalUrl, caption);
        return ResponseEntity.ok(ApiResponse.success(response, "Portfolio image uploaded successfully"));
    }

    @PostMapping(value = "/{muaId}/portfolio", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<MuaPortfolioResponseDto>> addPortfolioImageJson(
            @PathVariable Long muaId,
            @AuthenticationPrincipal String currentUserId,
            @RequestBody MuaPortfolioRequestDto body) {
        Long targetMuaId = resolveAuthorizedMuaId(muaId, currentUserId);
        String finalUrl = body != null ? body.getImageUrl() : null;
        String finalCaption = body != null ? body.getCaption() : null;
        MuaPortfolioResponseDto response = muaService.addPortfolioImage(targetMuaId, null, finalUrl, finalCaption);
        return ResponseEntity.ok(ApiResponse.success(response, "Portfolio image uploaded successfully"));
    }

    @GetMapping("/{muaId}/portfolio")
    public ResponseEntity<ApiResponse<List<MuaPortfolioResponseDto>>> getMuaPortfolios(@PathVariable Long muaId) {
        List<MuaPortfolioResponseDto> portfolios = muaService.getMuaPortfolios(muaId);
        return ResponseEntity.ok(ApiResponse.success(portfolios));
    }

    @DeleteMapping("/{muaId}/portfolio/{portfolioId}")
    public ResponseEntity<ApiResponse<Void>> deletePortfolioImage(
            @PathVariable Long muaId,
            @PathVariable Long portfolioId,
            @AuthenticationPrincipal String currentUserId) {
        Long targetMuaId = resolveAuthorizedMuaId(muaId, currentUserId);
        muaService.deletePortfolioImage(targetMuaId, portfolioId);
        return ResponseEntity.ok(ApiResponse.success(null, "Portfolio image deleted successfully"));
    }

    private Long resolveAuthorizedMuaId(Long pathMuaId, String currentUserId) {
        if (currentUserId != null && !currentUserId.isBlank() && !"anonymousUser".equalsIgnoreCase(currentUserId)) {
            try {
                return Long.parseLong(currentUserId);
            } catch (NumberFormatException e) {
                return pathMuaId;
            }
        }
        return pathMuaId;
    }
}
