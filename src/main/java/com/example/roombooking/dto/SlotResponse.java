package com.example.roombooking.dto;

import java.time.OffsetDateTime;

public record SlotResponse(OffsetDateTime startTime, OffsetDateTime endTime) {
}
