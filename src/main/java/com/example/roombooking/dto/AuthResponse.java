package com.example.roombooking.dto;

import java.time.Instant;

public record AuthResponse(String token, Instant expiresAt, UserResponse user) {
}
