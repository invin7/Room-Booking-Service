package com.example.roombooking.controller;

import com.example.roombooking.dto.BookingRequest;
import com.example.roombooking.exception.BookingConflictException;
import com.example.roombooking.model.Booking;
import com.example.roombooking.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    /**
     * Book a slot for a user.
     * Expects a JSON request body with roomId, startTime (ISO format), and userId.
     * Returns 201 on success, 409 if slot is already booked, 400 for invalid input.
     */
    @PostMapping
    public ResponseEntity<Booking> createBooking(@RequestBody BookingRequest request) {
        try {
            Booking booking = bookingService.bookSlot(request.getRoomId(), request.getStartTime(), request.getUserId());
            return ResponseEntity.ok(booking);
        } catch (IllegalArgumentException e) {
            // Validation errors (room not found, start time in past, etc.)
            return ResponseEntity.badRequest().build();
        } catch (BookingConflictException e) {
            // Slot already booked
            return ResponseEntity.status(409).build();
        }
    }

    /**
     * Cancel a booking by ID.
     * Sets the status to CANCELLED.
     * Returns 204 on success, 400 if booking not found or not confirmed, 404 if booking ID not found.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelBooking(@PathVariable Long id) {
        try {
            bookingService.cancelBooking(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            // Booking not found or not confirmed
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get all bookings for a user.
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<List<Booking>> getBookingsByUser(@PathVariable String userId) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        List<Booking> bookings = bookingService.getBookingsByUser(userId);
        return ResponseEntity.ok(bookings);
    }
}