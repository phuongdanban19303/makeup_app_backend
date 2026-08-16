package com.makeup.user.repository;

import com.makeup.user.entity.ProviderServiceOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProviderServiceOptionRepository extends JpaRepository<ProviderServiceOptionEntity, Long> {
    List<ProviderServiceOptionEntity> findByProviderServiceId(Long providerServiceId);
}
