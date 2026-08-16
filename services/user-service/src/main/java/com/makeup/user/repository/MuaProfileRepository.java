package com.makeup.user.repository;

import com.makeup.user.entity.MuaProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MuaProfileRepository extends JpaRepository<MuaProfileEntity, Long> {
}
