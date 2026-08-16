package com.makeup.user.repository;

import com.makeup.user.entity.ProviderServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProviderServiceRepository extends JpaRepository<ProviderServiceEntity, Long> {
    List<ProviderServiceEntity> findByProviderIdAndIsActiveTrue(Long providerId);
    List<ProviderServiceEntity> findByProviderIdInAndIsActiveTrue(List<Long> providerIds);
    List<ProviderServiceEntity> findByProviderId(Long providerId);
}
