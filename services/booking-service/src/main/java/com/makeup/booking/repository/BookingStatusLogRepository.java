package com.makeup.booking.repository;

import com.makeup.booking.entity.BookingStatusLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingStatusLogRepository extends JpaRepository<BookingStatusLogEntity, Long> {

    List<BookingStatusLogEntity> findByBookingIdOrderByCreatedAtAsc(String bookingId);
}
