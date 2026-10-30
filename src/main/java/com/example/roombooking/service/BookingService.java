package com.example.roombooking.service;

import com.example.roombooking.dto.SlotResponse;
import com.example.roombooking.exception.BookingConflictException;
import com.example.roombooking.exception.ConflictException;
import com.example.roombooking.exception.ForbiddenException;
import com.example.roombooking.exception.ResourceNotFoundException;
import com.example.roombooking.exception.UnauthorizedException;
import com.example.roombooking.model.AppUser;
import com.example.roombooking.model.Booking;
import com.example.roombooking.model.BookingStatus;
import com.example.roombooking.model.Room;
import com.example.roombooking.model.Slot;
import com.example.roombooking.repository.BookingRepository;
import com.example.roombooking.repository.SlotRepository;
import com.example.roombooking.repository.UserRepository;
import com.example.roombooking.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class BookingService {
    private final RoomService roomService;
    private final SlotRepository slotRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    /**
     * The room's bookable hours for one local date, each flagged available or not. Slots are
     * computed, not stored, so reading never writes; rows are only created when a booking is made.
     */
    @Transactional(readOnly = true)
    public List<SlotResponse> getAvailability(Long roomId, LocalDate date) {
        if (date == null) throw new IllegalArgumentException("date is required");
        Room room = roomService.requireRoom(roomId);
        ZoneId zone = ZoneId.of(room.getTimezone());
        Set<Instant> booked = bookingRepository.findBookedStartTimes(roomId, BookingStatus.CONFIRMED,
                        date.atStartOfDay(zone).toOffsetDateTime(),
                        date.plusDays(1).atStartOfDay(zone).toOffsetDateTime())
                .stream().map(OffsetDateTime::toInstant).collect(Collectors.toSet());
        Instant now = Instant.now();
        return IntStream.range(room.getOpenHour(), room.getCloseHour())
                .mapToObj(hour -> date.atTime(hour, 0).atZone(zone))
                .map(ZonedDateTime::toInstant).distinct()
                .map(start -> new SlotResponse(start.atZone(zone).toOffsetDateTime(),
                        start.plusSeconds(3600).atZone(zone).toOffsetDateTime(),
                        start.isAfter(now) && !booked.contains(start)))
                .toList();
    }

    @Transactional
    public Booking bookSlot(Long userId, Long roomId, OffsetDateTime startTime) {
        if (roomId == null || startTime == null) {
            throw new IllegalArgumentException("roomId and startTime are required");
        }
        Room room = roomService.requireRoom(roomId);
        ZoneId zone = ZoneId.of(room.getTimezone());
        ZonedDateTime local = startTime.toInstant().atZone(zone);
        if (local.getMinute() != 0 || local.getSecond() != 0 || local.getNano() != 0) {
            throw new IllegalArgumentException("startTime must begin exactly on the hour in " + zone);
        }
        if (local.getHour() < room.getOpenHour() || local.getHour() >= room.getCloseHour()) {
            throw new IllegalArgumentException(String.format("%s can only be booked between %02d:00 and %02d:00 (%s)",
                    room.getName(), room.getOpenHour(), room.getCloseHour() % 24, zone));
        }
        if (!startTime.toInstant().isAfter(Instant.now())) {
            throw new IllegalArgumentException("startTime must be in the future");
        }
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Account no longer exists"));
        OffsetDateTime start = startTime.withOffsetSameInstant(ZoneOffset.UTC);
        Slot slot = findOrCreateSlot(room, start, start.plusHours(1));
        int inserted = bookingRepository.insertConfirmedIfAvailable(
                room.getId(), slot.getId(), user.getId(), OffsetDateTime.now(ZoneOffset.UTC));
        if (inserted == 0) {
            throw new BookingConflictException("Slot is already booked");
        }
        return bookingRepository.findByRoomIdAndSlotIdAndStatus(room.getId(), slot.getId(), BookingStatus.CONFIRMED)
                .orElseThrow(() -> new IllegalStateException("Booking was created but could not be read"));
    }

    @Transactional
    public Booking cancelBooking(Long bookingId, CurrentUser actor) {
        Booking booking = bookingRepository.findWithDetailsById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (!actor.admin() && !booking.getUser().getId().equals(actor.id())) {
            throw new ForbiddenException("You can only cancel your own bookings");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new ConflictException("Booking is already cancelled");
        }
        booking.setStatus(BookingStatus.CANCELLED);
        return booking;
    }

    @Transactional(readOnly = true)
    public List<Booking> getBookingsForUser(Long userId) {
        return bookingRepository.findByUserIdOrderBySlotStartTimeDesc(userId);
    }

    /** Every confirmed booking that has not started yet, soonest first (admin view). */
    @Transactional(readOnly = true)
    public List<Booking> getUpcomingBookings() {
        return bookingRepository.findByStatusAndSlotStartTimeGreaterThanEqualOrderBySlotStartTime(
                BookingStatus.CONFIRMED, OffsetDateTime.now(ZoneOffset.UTC));
    }

    private Slot findOrCreateSlot(Room room, OffsetDateTime start, OffsetDateTime end) {
        slotRepository.insertIfAbsent(room.getId(), start, end);
        return slotRepository.findByRoomAndStartTimeAndEndTime(room, start, end)
                .orElseThrow(() -> new IllegalStateException("Slot was not created"));
    }
}
