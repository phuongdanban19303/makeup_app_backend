package com.makeup.payment.repository;

import com.makeup.payment.entity.WithdrawalRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequestEntity, UUID> {

    List<WithdrawalRequestEntity> findByWalletId(UUID walletId);
}
