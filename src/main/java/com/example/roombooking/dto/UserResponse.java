package com.example.roombooking.dto;

import com.example.roombooking.model.AppUser;
import com.example.roombooking.model.Role;

public record UserResponse(Long id, String name, String email, Role role) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
