package com.example.roombooking.dto;

import com.example.roombooking.model.Room;

public record RoomResponse(Long id, String name, Integer capacity) {
    public static RoomResponse from(Room room) {
        return new RoomResponse(room.getId(), room.getName(), room.getCapacity());
    }
}
