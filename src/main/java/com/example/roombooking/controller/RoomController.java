package com.example.roombooking.controller;

import com.example.roombooking.dto.RoomRequest;
import com.example.roombooking.dto.RoomResponse;
import com.example.roombooking.dto.SlotResponse;
import com.example.roombooking.service.BookingService;
import com.example.roombooking.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final BookingService bookingService;

    @GetMapping
    public List<RoomResponse> getAllRooms() {
        return roomService.getAllRooms().stream().map(RoomResponse::from).toList();
    }

    @GetMapping("/{id}")
    public RoomResponse getRoom(@PathVariable Long id) {
        return RoomResponse.from(roomService.getRoom(id));
    }

    /** Admin only. */
    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody RoomRequest request) {
        return ResponseEntity.status(201).body(RoomResponse.from(roomService.createRoom(request)));
    }

    /** Admin only. */
    @PutMapping("/{id}")
    public RoomResponse updateRoom(@PathVariable Long id, @Valid @RequestBody RoomRequest request) {
        return RoomResponse.from(roomService.updateRoom(id, request));
    }

    /** Admin only. Refused while the room has upcoming bookings. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRoom(@PathVariable Long id) {
        roomService.deleteRoom(id);
        return ResponseEntity.noContent().build();
    }

    /** Every bookable hour of the given local date, flagged available or not. */
    @GetMapping("/{id}/availability")
    public List<SlotResponse> getAvailability(@PathVariable Long id,
                                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return bookingService.getAvailability(id, date);
    }
}
