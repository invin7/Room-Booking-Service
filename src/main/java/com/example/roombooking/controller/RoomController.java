package com.example.roombooking.controller;

import com.example.roombooking.model.Room;
import com.example.roombooking.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final BookingService bookingService;

    /**
     * Get all rooms.
     */
    @GetMapping
    public List<Room> getAllRooms() {
        return bookingService.getAllRooms();
    }

    /**
     * Create a new room.
     */
    @PostMapping
    public ResponseEntity<Room> createRoom(@RequestBody Room room) {
        Room savedRoom = bookingService.saveRoom(room);
        return ResponseEntity.ok(savedRoom);
    }
}