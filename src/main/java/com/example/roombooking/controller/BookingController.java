package com.example.roombooking.controller;

import com.example.roombooking.dto.BookingRequest;
import com.example.roombooking.dto.BookingResponse;
import com.example.roombooking.service.BookingService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<BookingResponse> createBooking(@Valid @RequestBody BookingRequest request) {
        BookingResponse booking = BookingResponse.from(
                bookingService.bookSlot(request.getRoomId(), request.getStartTime(), request.getUserId()));
        return ResponseEntity.status(201).body(booking);
    }

    /**
     * Cancel a booking by ID.
     * Sets the status to CANCELLED.
     * Returns 204 on success, 400 if booking not found or not confirmed, 404 if booking ID not found.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelBooking(@PathVariable Long id) {
        bookingService.cancelBooking(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Get all bookings for a user.
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<List<BookingResponse>> getBookingsByUser(@PathVariable String userId) {
        List<BookingResponse> bookings = bookingService.getBookingsByUser(userId).stream()
                .map(BookingResponse::from).toList();
        return ResponseEntity.ok(bookings);
    }
}
