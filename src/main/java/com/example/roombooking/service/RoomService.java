package com.example.roombooking.service;

import com.example.roombooking.dto.RoomRequest;
import com.example.roombooking.exception.ConflictException;
import com.example.roombooking.exception.ResourceNotFoundException;
import com.example.roombooking.model.BookingStatus;
import com.example.roombooking.model.Room;
import com.example.roombooking.repository.BookingRepository;
import com.example.roombooking.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomService {
    private static final String DEFAULT_TIMEZONE = "UTC";
    private static final int DEFAULT_OPEN_HOUR = 8;
    private static final int DEFAULT_CLOSE_HOUR = 18;

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;

    @Transactional(readOnly = true)
    public List<Room> getAllRooms() {
        return roomRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(Room::getName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    @Transactional(readOnly = true)
    public Room getRoom(Long id) {
        return requireRoom(id);
    }

    @Transactional
    public Room createRoom(RoomRequest request) {
        return roomRepository.save(apply(Room.builder().build(), request));
    }

    @Transactional
    public Room updateRoom(Long id, RoomRequest request) {
        return roomRepository.save(apply(requireRoom(id), request));
    }

    @Transactional
    public void deleteRoom(Long id) {
        Room room = requireRoom(id);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (bookingRepository.existsBySlotRoomIdAndStatusAndSlotStartTimeGreaterThanEqual(
                id, BookingStatus.CONFIRMED, now)) {
            throw new ConflictException("Room has upcoming bookings; cancel them before deleting the room");
        }
        roomRepository.delete(room);
    }

    public Room requireRoom(Long roomId) {
        if (roomId == null) throw new IllegalArgumentException("roomId is required");
        return roomRepository.findById(roomId).orElseThrow(() -> new ResourceNotFoundException("Room not found"));
    }

    private Room apply(Room room, RoomRequest request) {
        int open = request.openHour() != null ? request.openHour() : DEFAULT_OPEN_HOUR;
        int close = request.closeHour() != null ? request.closeHour() : DEFAULT_CLOSE_HOUR;
        if (open >= close) throw new IllegalArgumentException("openHour must be before closeHour");
        room.setName(request.name().trim());
        room.setCapacity(request.capacity());
        room.setDescription(blankToNull(request.description()));
        room.setLocation(blankToNull(request.location()));
        room.setTimezone(validZone(request.timezone()).getId());
        room.setOpenHour(open);
        room.setCloseHour(close);
        return room;
    }

    /** Slots start on the local hour, which the database only allows for quarter-hour UTC offsets. */
    private ZoneId validZone(String timezone) {
        String id = timezone == null || timezone.isBlank() ? DEFAULT_TIMEZONE : timezone.trim();
        ZoneId zone;
        try {
            zone = ZoneId.of(id);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Unknown timezone: " + id);
        }
        if (zone.getRules().getOffset(Instant.now()).getTotalSeconds() % (15 * 60) != 0) {
            throw new IllegalArgumentException("Unsupported timezone offset: " + id);
        }
        return zone;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
