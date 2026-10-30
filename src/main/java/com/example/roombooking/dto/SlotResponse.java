package com.example.roombooking.dto;

import java.time.OffsetDateTime;

/** One bookable hour of a room's day; unavailable when already booked or in the past. */
public record SlotResponse(OffsetDateTime startTime, OffsetDateTime endTime, boolean available) {
}
