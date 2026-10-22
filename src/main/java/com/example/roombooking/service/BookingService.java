package com.example.roombooking.service;

import com.example.roombooking.dto.RoomRequest;
import com.example.roombooking.dto.SlotResponse;
import com.example.roombooking.exception.BookingConflictException;
import com.example.roombooking.exception.ResourceNotFoundException;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingService {
    private static final String CONFIRMED = "CONFIRMED";
    private static final String CANCELLED = "CANCELLED";
    private final RoomRepository roomRepository;
    private final SlotRepository slotRepository;
    private final BookingRepository bookingRepository;

    @Transactional(readOnly = true)
    public List<Room> getAllRooms() { return roomRepository.findAll(); }

    @Transactional
    public Room saveRoom(RoomRequest request) {
        return roomRepository.save(Room.builder().name(request.name().trim()).capacity(request.capacity()).build());
    }

    /** Returns canonical UTC hourly slots without creating rows during a read. */
    @Transactional(readOnly = true)
    public List<SlotResponse> getAvailableSlots(Long roomId, LocalDate date) {
        requireRoom(roomId);
        if (date == null) throw new IllegalArgumentException("date is required");
        OffsetDateTime startOfDay = date.atStartOfDay().atOffset(ZoneOffset.UTC);
        return java.util.stream.IntStream.range(0, 24)
                .mapToObj(hour -> new TimeRange(startOfDay.plusHours(hour), startOfDay.plusHours(hour + 1)))
                .filter(range -> isAvailable(roomId, range.start(), range.end()))
                .map(range -> new SlotResponse(range.start(), range.end())).toList();
    }

    @Transactional
    public Booking bookSlot(Long roomId, OffsetDateTime startTime, String userId) {
        if (roomId == null || startTime == null || userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("roomId, startTime, and userId are required");
        }
        OffsetDateTime suppliedUtc = startTime.withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime canonicalStart = suppliedUtc.truncatedTo(ChronoUnit.HOURS);
        if (!canonicalStart.equals(suppliedUtc)) {
            throw new IllegalArgumentException("startTime must begin exactly on an hourly UTC boundary");
        }
        if (!canonicalStart.isAfter(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new IllegalArgumentException("startTime must be in the future");
        }
        Room room = requireRoom(roomId);
        Slot slot = findOrCreateSlot(room, canonicalStart, canonicalStart.plusHours(1));
        if (bookingRepository.existsBySlotIdAndStatus(slot.getId(), CONFIRMED)) {
            throw new BookingConflictException("Slot is already booked");
        }
        try {
            return bookingRepository.saveAndFlush(Booking.builder().room(room).slot(slot)
                    .userId(userId.trim()).status(CONFIRMED)
                    .createdAt(OffsetDateTime.now(ZoneOffset.UTC)).build());
        } catch (DataIntegrityViolationException exception) {
            throw new BookingConflictException("Slot is already booked");
        }
    }

    @Transactional
    public void cancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (!CONFIRMED.equals(booking.getStatus())) {
            throw new IllegalArgumentException("Only confirmed bookings can be cancelled");
        }
        booking.setStatus(CANCELLED);
    }

    @Transactional(readOnly = true)
    public List<Booking> getBookingsByUser(String userId) {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId is required");
        return bookingRepository.findByUserId(userId.trim());
    }

    private boolean isAvailable(Long roomId, OffsetDateTime start, OffsetDateTime end) {
        return slotRepository.findByRoomAndStartTimeAndEndTime(requireRoom(roomId), start, end)
                .map(slot -> !bookingRepository.existsBySlotIdAndStatus(slot.getId(), CONFIRMED)).orElse(true);
    }

    private Slot findOrCreateSlot(Room room, OffsetDateTime start, OffsetDateTime end) {
        slotRepository.insertIfAbsent(room.getId(), start, end);
        return slotRepository.findByRoomAndStartTimeAndEndTime(room, start, end)
                .orElseThrow(() -> new IllegalStateException("Slot was not created"));
    }

    private Room requireRoom(Long roomId) {
        if (roomId == null) throw new IllegalArgumentException("roomId is required");
        return roomRepository.findById(roomId).orElseThrow(() -> new ResourceNotFoundException("Room not found"));
    }

    private record TimeRange(OffsetDateTime start, OffsetDateTime end) { }
}
