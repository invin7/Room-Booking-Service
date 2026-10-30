package com.example.roombooking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Omitted timezone / hours default to UTC and 08:00-18:00. */
public record RoomRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull @Positive @Max(1000) Integer capacity,
        @Size(max = 1000) String description,
        @Size(max = 255) String location,
        @Size(max = 64) String timezone,
        @Min(0) @Max(23) Integer openHour,
        @Min(1) @Max(24) Integer closeHour) {
}
