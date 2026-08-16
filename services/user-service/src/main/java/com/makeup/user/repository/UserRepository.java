package com.makeup.user.repository;

import com.makeup.user.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByPhone(String phone);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);
}
