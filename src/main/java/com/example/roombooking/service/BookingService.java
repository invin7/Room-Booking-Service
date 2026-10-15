package com.example.roombooking.service;

import com.example.roombooking.exception.BookingConflictException;
import com.example.roombooking.model.Booking;
import com.example.roombooking.model.Room;
import com.example.roombooking.model.Slot;
import com.example.roombooking.repository.BookingRepository;
import com.example.roombooking.repository.RoomRepository;
import com.example.roombooking.repository.SlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final RoomRepository roomRepository;
    private final SlotRepository slotRepository;
    private final BookingRepository bookingRepository;

    private static final int SLOT_DURATION_HOURS = 1;

    /**
     * Get all rooms.
     */
    @Transactional(readOnly = true)
    public List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    /**
     * Save a room.
     */
    @Transactional
    public Room saveRoom(Room room) {
        return roomRepository.save(room);
    }

    /**
     * Get available slots for a room on a given date.
     * We consider a slot available if there is no confirmed booking for that slot.
     * We generate slots for the day from 00:00 to 23:59 with a fixed duration.
     */
    @Transactional(readOnly = true)
    public List<Slot> getAvailableSlots(Long roomId, LocalDateTime date) {
        // Validate room exists
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        // Validate date is not null
        if (date == null) {
            throw new IllegalArgumentException("Date must not be null");
        }

        // We consider the day starting at 00:00 of the given date
        LocalDateTime startOfDay = date.toLocalDate().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        List<Slot> availableSlots = new ArrayList<>();

        // Generate slot time ranges for the day
        LocalDateTime currentStart = startOfDay;
        while (currentStart.isBefore(endOfDay)) {
            LocalDateTime currentEnd = currentStart.plusHours(SLOT_DURATION_HOURS);
            if (currentEnd.isAfter(endOfDay)) {
                currentEnd = endOfDay;
            }

            // Find or create slot for this time range
            Slot slot = findOrCreateSlot(room, currentStart, currentEnd);

            // Check if slot is free (no confirmed booking)
            Optional<Booking> existingBooking = bookingRepository.findByRoomIdAndSlotIdAndStatus(room.getId(), slot.getId(), "CONFIRMED");
            if (!existingBooking.isPresent()) {
                availableSlots.add(slot);
            }

            currentStart = currentEnd;
        }

        return availableSlots;
    }

    /**
     * Book a slot for a user.
     * We take roomId, start time, and userId.
     * We compute the end time as start time plus SLOT_DURATION_HOURS.
     * We validate the room exists and the start time is not in the past.
     * We find or create a slot for the room and time range.
     * We then attempt to create a booking. If a concurrent booking happens, we catch the DataIntegrityViolationException
     * and throw a BookingConflictException.
     */
    @Transactional
    public Booking bookSlot(Long roomId, LocalDateTime startTime, String userId) {
        // Validate inputs
        if (roomId == null) {
            throw new IllegalArgumentException("Room ID must not be null");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("Start time must not be null");
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID must not be blank");
        }

        // Validate start time is not in the past
        if (startTime.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Start time must not be in the past");
        }

        // Validate room exists
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        // Compute end time
        LocalDateTime endTime = startTime.plusHours(SLOT_DURATION_HOURS);

        // Find or create slot
        Slot slot = findOrCreateSlot(room, startTime, endTime);

        // Check if slot is already booked (service-level check, though we rely on DB constraint for concurrency safety)
        Optional<Booking> existingBooking = bookingRepository.findByRoomIdAndSlotIdAndStatus(room.getId(), slot.getId(), "CONFIRMED");
        if (existingBooking.isPresent()) {
            throw new BookingConflictException("Slot is already booked");
        }

        // Create booking
        Booking booking = Booking.builder()
                .room(room)
                .slot(slot)
                .userId(userId)
                .status("CONFIRMED")
                .createdAt(LocalDateTime.now())
                .build();

        try {
            return bookingRepository.save(booking);
        } catch (DataIntegrityViolationException e) {
            // This can happen due to the unique index on (room_id, slot_id) where status = 'CONFIRMED'
            // if two requests try to book the same slot concurrently.
            throw new BookingConflictException("Slot is already booked");
        }
    }

    /**
     * Cancel a booking by setting its status to CANCELLED.
     * We do not delete the booking row.
     */
    @Transactional
    public void cancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));

        if (!"CONFIRMED".equals(booking.getStatus())) {
            throw new IllegalArgumentException("Only confirmed bookings can be cancelled");
        }

        booking.setStatus("CANCELLED");
        bookingRepository.save(booking);
    }

    /**
     * Get bookings for a user.
     */
    @Transactional(readOnly = true)
    public List<Booking> getBookingsByUser(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("User ID must not be blank");
        }
        return bookingRepository.findByUserId(userId);
    }

    /**
     * Find a slot by room and time range, or create it if it doesn't exist.
     * This method is used to ensure that the slot exists in the database.
     */
    @Transactional
    public Slot findOrCreateSlot(Room room, LocalDateTime startTime, LocalDateTime endTime) {
        // Validate inputs
        if (room == null) {
            throw new IllegalArgumentException("Room must not be null");
        }
        if (startTime == null || endTime == null) {
            throw new IllegalArgumentException("Start and end times must not be null");
        }
        if (startTime.isAfter(endTime) || startTime.isEqual(endTime)) {
            throw new IllegalArgumentException("Start time must be before end time");
        }

        // Try to find an existing slot
        Optional<Slot> existingSlot = slotRepository.findByRoomIdAndStartTimeAndEndTime(room, startTime, endTime);
        if (existingSlot.isPresent()) {
            return existingSlot.get();
        }

        // Create a new slot
        Slot slot = Slot.builder()
                .room(room)
                .startTime(startTime)
                .endTime(endTime)
                .build();

        return slotRepository.save(slot);
    }
}