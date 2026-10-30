package com.example.roombooking.controller;

import com.example.roombooking.dto.BookingRequest;
import com.example.roombooking.dto.BookingResponse;
import com.example.roombooking.security.CurrentUser;
import com.example.roombooking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    /** Books a slot for the signed-in user. 201 on success, 409 if the slot is taken, 400 for invalid input. */
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(@AuthenticationPrincipal Jwt jwt,
                                                         @Valid @RequestBody BookingRequest request) {
        BookingResponse booking = BookingResponse.from(
                bookingService.bookSlot(CurrentUser.from(jwt).id(), request.roomId(), request.startTime()));
        return ResponseEntity.status(201).body(booking);
    }

    /** Cancels a booking. Owners can cancel their own; admins can cancel any. 204 on success. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelBooking(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        bookingService.cancelBooking(id, CurrentUser.from(jwt));
        return ResponseEntity.noContent().build();
    }

    /** The signed-in user's bookings, most recent first. */
    @GetMapping("/me")
    public List<BookingResponse> myBookings(@AuthenticationPrincipal Jwt jwt) {
        return bookingService.getBookingsForUser(CurrentUser.from(jwt).id()).stream()
                .map(BookingResponse::from).toList();
    }

    /** Admin only: every upcoming confirmed booking. */
    @GetMapping
    public List<BookingResponse> upcomingBookings() {
        return bookingService.getUpcomingBookings().stream().map(BookingResponse::from).toList();
    }
}
