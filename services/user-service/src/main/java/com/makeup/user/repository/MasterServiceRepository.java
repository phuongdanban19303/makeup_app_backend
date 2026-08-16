package com.makeup.user.repository;

import com.makeup.user.entity.MasterServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MasterServiceRepository extends JpaRepository<MasterServiceEntity, Long> {
    List<MasterServiceEntity> findByCategoryName(String categoryName);
}
