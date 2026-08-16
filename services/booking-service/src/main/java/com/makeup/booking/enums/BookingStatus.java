package com.makeup.booking.enums;

public enum BookingStatus {
    CREATED,       // Booking document created in system
    REQUESTED,     // Customer submitted a booking request
    MATCHING,      // System searching for nearby available MUAs
    ACCEPTED,      // MUA accepted the booking request
    MUA_MOVING,    // MUA is en route to customer location
    ARRIVED,       // MUA arrived at customer address
    MAKING_UP,     // MUA currently performing makeup service
    COMPLETED,     // Service successfully completed
    CANCELLED      // Booking cancelled by customer or MUA
}
