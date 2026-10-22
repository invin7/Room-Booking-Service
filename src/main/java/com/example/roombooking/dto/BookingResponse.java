package com.example.roombooking.dto;

import com.example.roombooking.model.Booking;
import java.time.OffsetDateTime;

public record BookingResponse(Long id, Long roomId, OffsetDateTime startTime,
                              OffsetDateTime endTime, String userId, String status,
                              OffsetDateTime createdAt) {
    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(), booking.getRoom().getId(), booking.getSlot().getStartTime(),
                booking.getSlot().getEndTime(), booking.getUserId(), booking.getStatus(),
                booking.getCreatedAt());
    }
}
