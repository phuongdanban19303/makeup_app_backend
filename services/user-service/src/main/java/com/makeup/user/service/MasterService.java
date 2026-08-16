package com.makeup.user.service;

import com.makeup.common.exception.AppException;
import com.makeup.common.exception.ErrorCode;
import com.makeup.user.dto.MasterServiceResponseDto;
import com.makeup.user.entity.MasterServiceEntity;
import com.makeup.user.repository.MasterServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MasterService {

    private final MasterServiceRepository masterServiceRepository;

    @Transactional(readOnly = true)
    public List<MasterServiceResponseDto> getAllMasterServices(String categoryName) {
        List<MasterServiceEntity> list = (categoryName != null && !categoryName.isBlank())
                ? masterServiceRepository.findByCategoryName(categoryName)
                : masterServiceRepository.findAll();

        return list.stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public MasterServiceResponseDto createMasterService(MasterServiceResponseDto request) {
        MasterServiceEntity entity = MasterServiceEntity.builder()
                .categoryName(request.getCategoryName())
                .serviceName(request.getServiceName())
                .description(request.getDescription())
                .build();

        entity = masterServiceRepository.save(entity);
        return mapToResponse(entity);
    }

    private MasterServiceResponseDto mapToResponse(MasterServiceEntity entity) {
        return MasterServiceResponseDto.builder()
                .id(entity.getId())
                .categoryName(entity.getCategoryName())
                .serviceName(entity.getServiceName())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
