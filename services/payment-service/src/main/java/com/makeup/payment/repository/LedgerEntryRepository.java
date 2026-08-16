package com.makeup.payment.repository;

import com.makeup.payment.entity.LedgerEntryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntryEntity, UUID> {

    List<LedgerEntryEntity> findByTransactionId(UUID transactionId);

    List<LedgerEntryEntity> findByWalletIdOrderByCreatedAtDesc(UUID walletId);
}
