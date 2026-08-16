package com.makeup.payment.repository;

import com.makeup.payment.entity.WalletEntity;
import com.makeup.payment.enums.UserType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<WalletEntity, UUID> {

    Optional<WalletEntity> findByUserIdAndUserType(String userId, UserType userType);

    List<WalletEntity> findByUserId(String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WalletEntity w WHERE w.id IN :ids ORDER BY w.id ASC")
    List<WalletEntity> findAllByIdInOrderByIdAscForUpdate(@Param("ids") Collection<UUID> ids);
}
