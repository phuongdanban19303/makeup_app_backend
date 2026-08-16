package com.makeup.payment.repository;

import com.makeup.payment.entity.UserBankAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserBankAccountRepository extends JpaRepository<UserBankAccountEntity, UUID> {

    List<UserBankAccountEntity> findByUserId(String userId);
}
