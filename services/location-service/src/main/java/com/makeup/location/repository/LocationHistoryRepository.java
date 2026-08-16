package com.makeup.location.repository;

import com.makeup.location.entity.MuaLocationHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LocationHistoryRepository extends JpaRepository<MuaLocationHistoryEntity, Long> {

    List<MuaLocationHistoryEntity> findByMuaIdOrderByRecordedAtDesc(Long muaId);
}
