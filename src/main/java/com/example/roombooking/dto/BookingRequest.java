package com.example.roombooking.dto;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/** The booker is taken from the authentication token, never from the request. */
public record BookingRequest(@NotNull Long roomId, @NotNull OffsetDateTime startTime) {
}
