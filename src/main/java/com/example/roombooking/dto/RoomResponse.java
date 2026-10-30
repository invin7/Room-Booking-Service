package com.example.roombooking.dto;

import com.example.roombooking.model.Room;

public record RoomResponse(Long id, String name, Integer capacity, String description, String location,
                           String timezone, Integer openHour, Integer closeHour) {
    public static RoomResponse from(Room room) {
        return new RoomResponse(room.getId(), room.getName(), room.getCapacity(), room.getDescription(),
                room.getLocation(), room.getTimezone(), room.getOpenHour(), room.getCloseHour());
    }
}
