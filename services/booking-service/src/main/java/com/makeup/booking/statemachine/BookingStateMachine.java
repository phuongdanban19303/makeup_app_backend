package com.makeup.booking.statemachine;

import com.makeup.booking.enums.BookingStatus;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class BookingStateMachine {

    // Valid state transitions map
    private static final Map<BookingStatus, Set<BookingStatus>> VALID_TRANSITIONS = Map.of(
            BookingStatus.CREATED, Set.of(BookingStatus.MATCHING, BookingStatus.CANCELLED),
            BookingStatus.REQUESTED, Set.of(BookingStatus.MATCHING, BookingStatus.CANCELLED),
            BookingStatus.MATCHING, Set.of(BookingStatus.ACCEPTED, BookingStatus.CANCELLED),
            BookingStatus.ACCEPTED, Set.of(BookingStatus.MUA_MOVING, BookingStatus.CANCELLED),
            BookingStatus.MUA_MOVING, Set.of(BookingStatus.ARRIVED, BookingStatus.CANCELLED),
            BookingStatus.ARRIVED, Set.of(BookingStatus.MAKING_UP, BookingStatus.CANCELLED),
            BookingStatus.MAKING_UP, Set.of(BookingStatus.COMPLETED, BookingStatus.CANCELLED),
            BookingStatus.COMPLETED, Set.of(),
            BookingStatus.CANCELLED, Set.of()
    );

    public boolean canTransition(BookingStatus currentStatus, BookingStatus targetStatus) {
        Set<BookingStatus> allowedNextStates = VALID_TRANSITIONS.get(currentStatus);
        return allowedNextStates != null && allowedNextStates.contains(targetStatus);
    }

    public BookingStatus transition(BookingStatus currentStatus, BookingStatus targetStatus) {
        if (!canTransition(currentStatus, targetStatus)) {
            throw new IllegalStateException(
                    String.format("Invalid state transition from %s to %s", currentStatus, targetStatus)
            );
        }
        return targetStatus;
    }
}
