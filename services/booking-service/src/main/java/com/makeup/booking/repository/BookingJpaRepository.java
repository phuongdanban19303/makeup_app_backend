package com.makeup.booking.repository;

import com.makeup.booking.entity.BookingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingJpaRepository extends JpaRepository<BookingEntity, String> {

    Optional<BookingEntity> findByBookingCode(String bookingCode);

    @Query("SELECT b FROM BookingEntity b WHERE b.muaId = :muaId AND b.status IN :statuses ORDER BY b.createdAt DESC")
    List<BookingEntity> findByMuaIdAndStatusIn(@Param("muaId") String muaId, @Param("statuses") List<String> statuses);

    @Query("SELECT b FROM BookingEntity b WHERE b.customerId = :customerId AND b.status IN :statuses ORDER BY b.createdAt DESC")
    List<BookingEntity> findByCustomerIdAndStatusIn(@Param("customerId") String customerId, @Param("statuses") List<String> statuses);
}
