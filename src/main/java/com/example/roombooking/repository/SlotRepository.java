package com.example.roombooking.repository;

import com.example.roombooking.model.Room;
import com.example.roombooking.model.Slot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SlotRepository extends JpaRepository<Slot, Long> {
    Optional<Slot> findByRoomAndStartTimeAndEndTime(Room room, OffsetDateTime startTime, OffsetDateTime endTime);

    @Modifying
    @Query(value = """
            INSERT INTO slot (room_id, start_time, end_time)
            VALUES (:roomId, :startTime, :endTime)
            ON CONFLICT (room_id, start_time, end_time) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("roomId") Long roomId,
                       @Param("startTime") OffsetDateTime startTime,
                       @Param("endTime") OffsetDateTime endTime);
}
