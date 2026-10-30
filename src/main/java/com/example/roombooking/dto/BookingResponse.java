package com.example.roombooking.dto;

import com.example.roombooking.model.Booking;
import com.example.roombooking.model.BookingStatus;
import java.time.OffsetDateTime;

public record BookingResponse(Long id, Long roomId, String roomName, String roomTimezone,
                              OffsetDateTime startTime, OffsetDateTime endTime,
                              Long userId, String userName, String userEmail,
                              BookingStatus status, OffsetDateTime createdAt) {
    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(), booking.getRoom().getId(), booking.getRoom().getName(),
                booking.getRoom().getTimezone(),
                booking.getSlot().getStartTime(), booking.getSlot().getEndTime(),
                booking.getUser().getId(), booking.getUser().getName(), booking.getUser().getEmail(),
                booking.getStatus(), booking.getCreatedAt());
    }
}
