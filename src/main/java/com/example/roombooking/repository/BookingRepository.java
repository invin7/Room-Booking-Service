package com.example.roombooking.repository;

import com.example.roombooking.model.Booking;
import com.example.roombooking.model.BookingStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    @EntityGraph(attributePaths = {"room", "slot", "user"})
    Optional<Booking> findByRoomIdAndSlotIdAndStatus(Long roomId, Long slotId, BookingStatus status);

    @EntityGraph(attributePaths = {"room", "slot", "user"})
    Optional<Booking> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"room", "slot", "user"})
    List<Booking> findByUserIdOrderBySlotStartTimeDesc(Long userId);

    @EntityGraph(attributePaths = {"room", "slot", "user"})
    List<Booking> findByStatusAndSlotStartTimeGreaterThanEqualOrderBySlotStartTime(
            BookingStatus status, OffsetDateTime from);

    boolean existsBySlotRoomIdAndStatusAndSlotStartTimeGreaterThanEqual(
            Long roomId, BookingStatus status, OffsetDateTime from);

    /** Start times of the room's confirmed bookings within [from, to). */
    @Query("""
            select s.startTime from Booking b join b.slot s
            where s.room.id = :roomId and b.status = :status
              and s.startTime >= :from and s.startTime < :to
            """)
    List<OffsetDateTime> findBookedStartTimes(@Param("roomId") Long roomId,
                                              @Param("status") BookingStatus status,
                                              @Param("from") OffsetDateTime from,
                                              @Param("to") OffsetDateTime to);

    /**
     * Atomically claims a slot. The conflict target matches the partial unique
     * index in the Flyway migrations, so a concurrent loser receives a row count of zero
     * rather than a constraint exception that marks its transaction rollback-only.
     */
    @Modifying
    @Query(value = """
            INSERT INTO booking (room_id, slot_id, user_id, status, created_at)
            VALUES (:roomId, :slotId, :userId, 'CONFIRMED', :createdAt)
            ON CONFLICT (slot_id) WHERE status = 'CONFIRMED' DO NOTHING
            """, nativeQuery = true)
    int insertConfirmedIfAvailable(@Param("roomId") Long roomId,
                                   @Param("slotId") Long slotId,
                                   @Param("userId") Long userId,
                                   @Param("createdAt") OffsetDateTime createdAt);
}
