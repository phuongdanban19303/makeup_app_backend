package com.makeup.user.repository;

import com.makeup.user.entity.MuaPortfolioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MuaPortfolioRepository extends JpaRepository<MuaPortfolioEntity, Long> {
    List<MuaPortfolioEntity> findByMuaIdOrderByCreatedAtDesc(Long muaId);
}
