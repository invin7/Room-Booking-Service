package com.example.roombooking.controller;

import com.example.roombooking.dto.RoomRequest;
import com.example.roombooking.dto.RoomResponse;
import com.example.roombooking.dto.SlotResponse;
import com.example.roombooking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
    public List<RoomResponse> getAllRooms() {
        return bookingService.getAllRooms().stream().map(RoomResponse::from).toList();
    }

    /**
     * Create a new room.
     */
    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody RoomRequest request) {
        return ResponseEntity.status(201).body(RoomResponse.from(bookingService.saveRoom(request)));
    }

    @GetMapping("/{id}/availability")
    public List<SlotResponse> getAvailability(@PathVariable Long id, @RequestParam LocalDate date) {
        return bookingService.getAvailableSlots(id, date);
    }
}
