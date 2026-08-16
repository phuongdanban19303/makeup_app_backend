package com.makeup.booking.repository;

import com.makeup.booking.document.BookingDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository thao tác với cơ sở dữ liệu MongoDB (collection: bookings).
 * Lưu trữ thông tin chi tiết đơn đặt lịch, lịch sử chuyển trạng thái, vị trí và thông tin tính cước.
 */
@Repository
public interface BookingMongoRepository extends MongoRepository<BookingDocument, String> {

    /**
     * Tìm kiếm đơn đặt lịch theo mã bookingCode duy nhất.
     */
    Optional<BookingDocument> findByBookingCode(String bookingCode);

    @org.springframework.data.mongodb.repository.Query("{ 'mua.user_id': ?0, 'status': { $in: ?1 } }")
    java.util.List<BookingDocument> findByMuaUserIdAndStatusIn(Long muaUserId, java.util.List<String> statuses);

    @org.springframework.data.mongodb.repository.Query("{ 'customer.user_id': ?0, 'status': { $in: ?1 } }")
    java.util.List<BookingDocument> findByCustomerUserIdAndStatusIn(Long customerUserId, java.util.List<String> statuses);
}

