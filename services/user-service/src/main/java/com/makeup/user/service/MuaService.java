package com.makeup.user.service;

import com.makeup.common.exception.AppException;
import com.makeup.common.exception.ErrorCode;
import com.makeup.user.dto.*;
import com.makeup.user.entity.MuaPortfolioEntity;
import com.makeup.user.entity.MuaProfileEntity;
import com.makeup.user.entity.ProviderServiceEntity;
import com.makeup.user.entity.ProviderServiceOptionEntity;
import com.makeup.user.entity.UserEntity;
import com.makeup.user.repository.MuaPortfolioRepository;
import com.makeup.user.repository.MuaProfileRepository;
import com.makeup.user.repository.ProviderServiceOptionRepository;
import com.makeup.user.repository.ProviderServiceRepository;
import com.makeup.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MuaService {

    public static final String DEFAULT_AVATAR_URL = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=400&q=80";

    private final UserRepository userRepository;
    private final MuaProfileRepository muaProfileRepository;
    private final MuaPortfolioRepository muaPortfolioRepository;
    private final ProviderServiceRepository providerServiceRepository;
    private final ProviderServiceOptionRepository providerServiceOptionRepository;
    private final ImageStorageService imageStorageService;

    @Transactional(readOnly = true)
    public MuaFullProfileResponseDto getMuaProfile(Long muaId) {
        UserEntity user = userRepository.findById(muaId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "MUA not found"));

        MuaProfileEntity profile = muaProfileRepository.findById(muaId)
                .orElseGet(() -> muaProfileRepository.save(MuaProfileEntity.builder()
                        .userId(muaId)
                        .bio("Professional Makeup Artist")
                        .currentStatus("OFFLINE")
                        .build()));

        List<ProviderServiceResponseDto> services = providerServiceRepository.findByProviderIdAndIsActiveTrue(muaId)
                .stream()
                .map(this::mapToProviderServiceResponse)
                .toList();

        List<MuaPortfolioResponseDto> portfolios = muaPortfolioRepository.findByMuaIdOrderByCreatedAtDesc(muaId)
                .stream()
                .map(this::mapToPortfolioResponse)
                .toList();

        String avatar = (profile != null && profile.getAvatarUrl() != null && !profile.getAvatarUrl().isBlank())
                ? profile.getAvatarUrl()
                : DEFAULT_AVATAR_URL;

        return MuaFullProfileResponseDto.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .email(user.getEmail())
                .avatarUrl(avatar)
                .bio(profile.getBio())
                .identityCardUrl(profile.getIdentityCardUrl())
                .isVerified(profile.getIsVerified())
                .rating(profile.getRating())
                .totalReviews(profile.getTotalReviews())
                .totalCompletedJobs(profile.getTotalCompletedJobs())
                .currentStatus(profile.getCurrentStatus())
                .services(services)
                .portfolios(portfolios)
                .build();
    }

    @Transactional
    public MuaFullProfileResponseDto updateProfile(Long muaId, UpdateMuaProfileRequestDto dto) {
        MuaProfileEntity profile = muaProfileRepository.findById(muaId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "MUA profile not found"));

        if (dto.getBio() != null) profile.setBio(dto.getBio());
        if (dto.getAvatarUrl() != null) {
            profile.setAvatarUrl(dto.getAvatarUrl());
            userRepository.findById(muaId).ifPresent(user -> {
                user.setAvatarUrl(dto.getAvatarUrl());
                userRepository.save(user);
            });
        }
        if (dto.getIdentityCardUrl() != null) profile.setIdentityCardUrl(dto.getIdentityCardUrl());
        if (dto.getCurrentStatus() != null) profile.setCurrentStatus(dto.getCurrentStatus().toUpperCase());

        muaProfileRepository.save(profile);
        return getMuaProfile(muaId);
    }

    @Transactional
    public String uploadIdentityCard(Long muaId, MultipartFile file) {
        MuaProfileEntity profile = muaProfileRepository.findById(muaId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND, "MUA profile not found"));

        String identityUrl = imageStorageService.uploadImage(file);
        profile.setIdentityCardUrl(identityUrl);
        muaProfileRepository.save(profile);

        return identityUrl;
    }

    // ==========================================
    // MUA SERVICES MANAGEMENT (REFICED TO USE PROVIDER_SERVICES)
    // ==========================================

    @Transactional
    public ProviderServiceResponseDto createService(Long muaId, ProviderServiceRequestDto dto) {
        return createBundleService(muaId, dto);
    }

    @Transactional(readOnly = true)
    public List<ProviderServiceResponseDto> getMuaServices(Long muaId, boolean includeInactive) {
        return getBundleServices(muaId, includeInactive);
    }

    @Transactional
    public ProviderServiceResponseDto updateService(Long muaId, Long serviceId, ProviderServiceRequestDto dto) {
        return updateBundleService(muaId, serviceId, dto);
    }

    @Transactional
    public ProviderServiceResponseDto toggleServiceStatus(Long muaId, Long serviceId, Boolean isActive) {
        ProviderServiceEntity entity = providerServiceRepository.findById(serviceId)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_REQUEST, "Service package not found"));

        if (!entity.getProviderId().equals(muaId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "You are not authorized to modify this service package");
        }

        boolean nextStatus = (isActive != null) ? isActive : !Boolean.TRUE.equals(entity.getIsActive());
        entity.setIsActive(nextStatus);
        entity = providerServiceRepository.save(entity);
        return mapToProviderServiceResponse(entity);
    }

    @Transactional
    public void deleteService(Long muaId, Long serviceId, boolean permanent) {
        ProviderServiceEntity entity = providerServiceRepository.findById(serviceId)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_REQUEST, "Service package not found"));

        if (!entity.getProviderId().equals(muaId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "You are not authorized to delete this service package");
        }

        if (permanent) {
            providerServiceRepository.delete(entity);
        } else {
            entity.setIsActive(false); // Soft delete / Tạm ẩn
            providerServiceRepository.save(entity);
        }
    }

    // ==========================================
    // DYNAMIC BUNDLE & OPTIONS MANAGEMENT
    // ==========================================

    @Transactional
    public ProviderServiceResponseDto createBundleService(Long providerId, ProviderServiceRequestDto dto) {
        if (!userRepository.existsById(providerId)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND, "Provider MUA user not found");
        }

        ProviderServiceEntity entity = ProviderServiceEntity.builder()
                .providerId(providerId)
                .masterServiceId(dto.getMasterServiceId())
                .category(dto.getCategory())
                .serviceName(dto.getServiceName())
                .description(dto.getDescription())
                .basePrice(dto.getBasePrice())
                .estimatedDurationMinutes(dto.getEstimatedDurationMinutes())
                .attributesJson(dto.getAttributesJson())
                .isActive(true)
                .options(new ArrayList<>())
                .build();

        if (dto.getOptions() != null && !dto.getOptions().isEmpty()) {
            for (ProviderServiceOptionDto optDto : dto.getOptions()) {
                ProviderServiceOptionEntity optionEntity = ProviderServiceOptionEntity.builder()
                        .providerService(entity)
                        .optionType(optDto.getOptionType() != null ? optDto.getOptionType().toUpperCase() : "COMPONENT")
                        .optionName(optDto.getOptionName())
                        .price(optDto.getPrice() != null ? optDto.getPrice() : BigDecimal.ZERO)
                        .isDefault(Boolean.TRUE.equals(optDto.getIsDefault()))
                        .isRemovable(Boolean.TRUE.equals(optDto.getIsRemovable()))
                        .build();
                entity.getOptions().add(optionEntity);
            }
        }

        entity = providerServiceRepository.save(entity);
        return mapToProviderServiceResponse(entity);
    }

    @Transactional(readOnly = true)
    public List<ProviderServiceResponseDto> getBundleServices(Long providerId, boolean includeInactive) {
        List<ProviderServiceEntity> list = includeInactive
                ? providerServiceRepository.findByProviderId(providerId)
                : providerServiceRepository.findByProviderIdAndIsActiveTrue(providerId);

        return list.stream()
                .map(this::mapToProviderServiceResponse)
                .toList();
    }

    @Transactional
    public ProviderServiceResponseDto updateBundleService(Long providerId, Long serviceId, ProviderServiceRequestDto dto) {
        ProviderServiceEntity entity = providerServiceRepository.findById(serviceId)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_REQUEST, "Bundle service package not found"));

        if (!entity.getProviderId().equals(providerId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "You are not authorized to update this service package");
        }

        entity.setMasterServiceId(dto.getMasterServiceId());
        entity.setCategory(dto.getCategory());
        entity.setServiceName(dto.getServiceName());
        entity.setDescription(dto.getDescription());
        entity.setBasePrice(dto.getBasePrice());
        entity.setEstimatedDurationMinutes(dto.getEstimatedDurationMinutes());
        entity.setAttributesJson(dto.getAttributesJson());

        // Refresh options
        entity.getOptions().clear();
        if (dto.getOptions() != null && !dto.getOptions().isEmpty()) {
            for (ProviderServiceOptionDto optDto : dto.getOptions()) {
                ProviderServiceOptionEntity optionEntity = ProviderServiceOptionEntity.builder()
                        .providerService(entity)
                        .optionType(optDto.getOptionType() != null ? optDto.getOptionType().toUpperCase() : "COMPONENT")
                        .optionName(optDto.getOptionName())
                        .price(optDto.getPrice() != null ? optDto.getPrice() : BigDecimal.ZERO)
                        .isDefault(Boolean.TRUE.equals(optDto.getIsDefault()))
                        .isRemovable(Boolean.TRUE.equals(optDto.getIsRemovable()))
                        .build();
                entity.getOptions().add(optionEntity);
            }
        }

        entity = providerServiceRepository.save(entity);
        return mapToProviderServiceResponse(entity);
    }

    private ProviderServiceResponseDto mapToProviderServiceResponse(ProviderServiceEntity entity) {
        List<ProviderServiceOptionDto> optionDtos = (entity.getOptions() != null)
                ? entity.getOptions().stream().map(o -> ProviderServiceOptionDto.builder()
                        .id(o.getId())
                        .optionType(o.getOptionType())
                        .optionName(o.getOptionName())
                        .price(o.getPrice())
                        .isDefault(o.getIsDefault())
                        .isRemovable(o.getIsRemovable())
                        .build()).toList()
                : List.of();

        // Tự động tính defaultTotalPrice = basePrice + SUM(options default)
        BigDecimal optionsDefaultSum = optionDtos.stream()
                .filter(o -> Boolean.TRUE.equals(o.getIsDefault()) && o.getPrice() != null)
                .map(ProviderServiceOptionDto::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal defaultTotalPrice = (entity.getBasePrice() != null ? entity.getBasePrice() : BigDecimal.ZERO)
                .add(optionsDefaultSum);

        return ProviderServiceResponseDto.builder()
                .id(entity.getId())
                .providerId(entity.getProviderId())
                .masterServiceId(entity.getMasterServiceId())
                .category(entity.getCategory())
                .serviceName(entity.getServiceName())
                .description(entity.getDescription())
                .basePrice(entity.getBasePrice())
                .defaultTotalPrice(defaultTotalPrice)
                .estimatedDurationMinutes(entity.getEstimatedDurationMinutes())
                .attributesJson(entity.getAttributesJson())
                .isActive(entity.getIsActive())
                .options(optionDtos)
                .createdAt(entity.getCreatedAt())
                .build();
    }

    // ==========================================
    // MUA PORTFOLIO MANAGEMENT
    // ==========================================

    @Transactional
    public MuaPortfolioResponseDto addPortfolioImage(Long muaId, MultipartFile file, String imageUrlParam, String caption) {
        if (!userRepository.existsById(muaId)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND, "MUA user not found");
        }

        String finalImageUrl = null;
        if (imageUrlParam != null && !imageUrlParam.isBlank()) {
            finalImageUrl = imageUrlParam.trim();
        } else if (file != null && !file.isEmpty()) {
            finalImageUrl = imageStorageService.uploadImage(file);
        }

        if (finalImageUrl == null || finalImageUrl.isBlank()) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Image file or imageUrl is required");
        }

        MuaPortfolioEntity portfolioEntity = MuaPortfolioEntity.builder()
                .muaId(muaId)
                .imageUrl(finalImageUrl)
                .caption(caption)
                .build();

        portfolioEntity = muaPortfolioRepository.save(portfolioEntity);
        return mapToPortfolioResponse(portfolioEntity);
    }

    @Transactional(readOnly = true)
    public List<MuaPortfolioResponseDto> getMuaPortfolios(Long muaId) {
        return muaPortfolioRepository.findByMuaIdOrderByCreatedAtDesc(muaId)
                .stream()
                .map(this::mapToPortfolioResponse)
                .toList();
    }

    @Transactional
    public void deletePortfolioImage(Long muaId, Long portfolioId) {
        MuaPortfolioEntity portfolio = muaPortfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_REQUEST, "Portfolio image not found"));

        if (!portfolio.getMuaId().equals(muaId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED, "You are not authorized to delete this portfolio image");
        }

        muaPortfolioRepository.delete(portfolio);
    }

    @Transactional(readOnly = true)
    public List<MuaSummaryDto> getMuaSummaries(List<Long> muaIds) {
        if (muaIds == null || muaIds.isEmpty()) {
            return List.of();
        }

        Map<Long, UserEntity> userMap = userRepository.findAllById(muaIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, u -> u));

        Map<Long, MuaProfileEntity> profileMap = muaProfileRepository.findAllById(muaIds).stream()
                .collect(Collectors.toMap(MuaProfileEntity::getUserId, p -> p));

        Map<Long, List<ProviderServiceResponseDto>> servicesMap = providerServiceRepository.findByProviderIdInAndIsActiveTrue(muaIds).stream()
                .map(this::mapToProviderServiceResponse)
                .collect(Collectors.groupingBy(ProviderServiceResponseDto::getProviderId));

        return muaIds.stream().map(id -> {
            UserEntity user = userMap.get(id);
            MuaProfileEntity profile = profileMap.get(id);
            String avatar = (profile != null && profile.getAvatarUrl() != null && !profile.getAvatarUrl().isBlank())
                    ? profile.getAvatarUrl()
                    : DEFAULT_AVATAR_URL;
            return MuaSummaryDto.builder()
                    .userId(id)
                    .fullName(user != null ? user.getFullName() : "MUA #" + id)
                    .avatarUrl(avatar)
                    .rating((profile != null && profile.getRating() != null) ? profile.getRating().doubleValue() : 5.0)
                    .totalCompletedJobs((profile != null && profile.getTotalCompletedJobs() != null) ? profile.getTotalCompletedJobs() : 0)
                    .currentStatus(profile != null ? profile.getCurrentStatus() : "OFFLINE")
                    .services(servicesMap.getOrDefault(id, List.of()))
                    .build();
        }).toList();
    }

    // ==========================================
    // MAPPER HELPERS
    // ==========================================

    private MuaPortfolioResponseDto mapToPortfolioResponse(MuaPortfolioEntity entity) {
        return MuaPortfolioResponseDto.builder()
                .id(entity.getId())
                .muaId(entity.getMuaId())
                .imageUrl(entity.getImageUrl())
                .caption(entity.getCaption())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
