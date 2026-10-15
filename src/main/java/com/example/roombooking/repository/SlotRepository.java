package com.example.roombooking.repository;

import com.example.roombooking.model.Room;
import com.example.roombooking.model.Slot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SlotRepository extends JpaRepository<Slot, Long> {
    List<Slot> findByRoomIdAndStartTimeBetween(Long roomId, OffsetDateTime start, OffsetDateTime end);
    Optional<Slot> findByRoomIdAndStartTimeAndEndTime(Room room, OffsetDateTime startTime, OffsetDateTime endTime);
}