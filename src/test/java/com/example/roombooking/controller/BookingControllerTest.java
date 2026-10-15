package com.example.roombooking.controller;

import com.example.roombooking.model.Booking;
import com.example.roombooking.model.Room;
import com.example.roombooking.repository.BookingRepository;
import com.example.roombooking.repository.RoomRepository;
import com.example.roombooking.repository.SlotRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class BookingControllerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("roombooking")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private SlotRepository slotRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private Room testRoom;
    private LocalDateTime futureStartTime;

    @BeforeEach
    void setUp() {
        // Clear database
        bookingRepository.deleteAll();
        slotRepository.deleteAll();
        roomRepository.deleteAll();

        // Create a test room
        testRoom = new Room();
        testRoom.setName("Test Room");
        testRoom.setCapacity(10);
        testRoom = roomRepository.save(testRoom);

        // Set a future start time (e.g., tomorrow at 10:00)
        futureStartTime = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
    }

    @Test
    void testConcurrentBookingRequests() throws Exception {
        int numberOfRequests = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfRequests);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch readyLatch = new CountDownLatch(numberOfRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Future<ResponseEntity<Booking>>> futures = new ArrayList<>();

        for (int i = 0; i < numberOfRequests; i++) {
            int userId = i; // Unique user ID for each request
            Future<ResponseEntity<Booking>> future = executorService.submit(() -> {
                try {
                    readyLatch.countDown(); // Signal that this thread is ready
                    latch.await(); // Wait for the main thread to release the latch

                    // Make the booking request
                    ResponseEntity<Booking> response = restTemplate.exchange(
                            "/bookings?roomId={roomId}&startTime={startTime}&userId={userId}",
                            HttpMethod.POST,
                            null,
                            Booking.class,
                            testRoom.getId(),
                            futureStartTime,
                            "user" + userId
                    );
                    if (response.getStatusCode() == HttpStatus.CREATED) {
                        successCount.incrementAndGet();
                    } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                        conflictCount.incrementAndGet();
                    }
                    return response;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            });
            futures.add(future);
        }

        // Wait for all threads to be ready
        readyLatch.await();
        // Release all threads to start simultaneously
        latch.countDown();

        // Wait for all tasks to complete
        for (Future<ResponseEntity<Booking>> future : futures) {
            future.get(); // This will also propagate any exceptions
        }

        executorService.shutdown();

        // Assertions
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(numberOfRequests - 1);

        // Verify that exactly one confirmed booking exists in the database
        List<Booking> confirmedBookings = bookingRepository.findByRoomIdAndSlotIdAndStatus(
                testRoom.getId(),
                // We need to find the slot ID for the booked slot. We can query by room and time.
                // Alternatively, we can check the total confirmed bookings for the room.
                // Since we know the slot is the one we tried to book, we can find the slot by room and time.
                // Let's find the slot by room and start time (we know the slot duration is 1 hour).
                slotRepository.findByRoomIdAndStartTimeAndEndTime(testRoom, futureStartTime, futureStartTime.plusHours(1))
                        .orElseThrow().getId(),
                "CONFIRMED"
        ).stream().toList();

        assertThat(confirmedBookings).hasSize(1);
    }

    @Test
    void testCancellingBookingFreesSlot() {
        // First, make a booking
        ResponseEntity<Booking> bookingResponse = restTemplate.exchange(
                "/bookings?roomId={roomId}&startTime={startTime}&userId={userId}",
                HttpMethod.POST,
                null,
                Booking.class,
                testRoom.getId(),
                futureStartTime,
                "user1"
        );

        assertThat(bookingResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Booking booking = bookingResponse.getBody();
        assertThat(booking).isNotNull();
        assertThat(booking.getId()).isNotNull();

        // Verify that the slot is now booked (no longer available)
        List<com.example.roombooking.model.Slot> availableBeforeCancel = restTemplate.exchange(
                "/rooms/{id}/availability?date={date}",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<com.example.roombooking.model.Slot>>() {},
                testRoom.getId(),
                futureStartTime.toLocalDate()
        ).getBody();

        assertThat(availableBeforeCancel).isEmpty(); // Because the slot is booked

        // Cancel the booking
        ResponseEntity<Void> cancelResponse = restTemplate.exchange(
                "/bookings/{id}",
                HttpMethod.DELETE,
                null,
                Void.class,
                booking.getId()
        );

        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Verify that the slot is now available again
        List<com.example.roombooking.model.Slot> availableAfterCancel = restTemplate.exchange(
                "/rooms/{id}/availability?date={date}",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<com.example.roombooking.model.Slot>>() {},
                testRoom.getId(),
                futureStartTime.toLocalDate()
        ).getBody();

        assertThat(availableAfterCancel).isNotEmpty();
        // We expect at least the slot we booked to be available
        boolean slotFound = availableAfterCancel.stream()
                .anyMatch(slot -> slot.getStartTime().equals(futureStartTime) &&
                        slot.getEndTime().equals(futureStartTime.plusHours(1)));
        assertThat(slotFound).isTrue();
    }

    @Test
    void testValidation() {
        // Test invalid room ID
        ResponseEntity<String> response = restTemplate.exchange(
                "/bookings?roomId={roomId}&startTime={startTime}&userId={userId}",
                HttpMethod.POST,
                null,
                String.class,
                999L, // non-existent room
                futureStartTime,
                "user1"
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Test start time in the past
        LocalDateTime pastTime = LocalDateTime.now().minusHours(1);
        response = restTemplate.exchange(
                "/bookings?roomId={roomId}&startTime={startTime}&userId={userId}",
                HttpMethod.POST,
                null,
                String.class,
                testRoom.getId(),
                pastTime,
                "user1"
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        // Test missing user ID (blank)
        response = restTemplate.exchange(
                "/bookings?roomId={roomId}&startTime={startTime}&userId={userId}",
                HttpMethod.POST,
                null,
                String.class,
                testRoom.getId(),
                futureStartTime,
                "" // blank user ID
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void testAvailabilityEndpoint() {
        // First, check that the slot is available (no booking yet)
        ResponseEntity<List<com.example.roombooking.model.Slot>> availabilityResponse = restTemplate.exchange(
                "/rooms/{id}/availability?date={date}",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<com.example.roombooking.model.Slot>>() {},
                testRoom.getId(),
                futureStartTime.toLocalDate()
        );

        assertThat(availabilityResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<com.example.roombooking.model.Slot> slots = availabilityResponse.getBody();
        assertThat(slots).isNotEmpty();

        // Find the slot that matches our futureStartTime (assuming 1-hour slots)
        Optional<com.example.roombooking.model.Slot> targetSlot = slots.stream()
                .filter(s -> s.getStartTime().equals(futureStartTime) &&
                        s.getEndTime().equals(futureStartTime.plusHours(1)))
                .findFirst();

        assertThat(targetSlot).isPresent();

        // Now book the slot
        restTemplate.exchange(
                "/bookings?roomId={roomId}&startTime={startTime}&userId={userId}",
                HttpMethod.POST,
                null,
                Booking.class,
                testRoom.getId(),
                futureStartTime,
                "user1"
        );

        // Check availability again: the slot should now be unavailable
        availabilityResponse = restTemplate.exchange(
                "/rooms/{id}/availability?date={date}",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<com.example.roombooking.model.Slot>>() {},
                testRoom.getId(),
                futureStartTime.toLocalDate()
        );

        assertThat(availabilityResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        slots = availabilityResponse.getBody();
        assertThat(slots).isNotEmpty();

        // The target slot should not be in the list
        boolean slotFound = slots.stream()
                .anyMatch(s -> s.getStartTime().equals(futureStartTime) &&
                        s.getEndTime().equals(futureStartTime.plusHours(1)));
        assertThat(slotFound).isFalse();
    }

    /**
     * Test that the database constraint prevents double booking even if we bypass the service layer.
     * This test directly uses the repository to try to create two confirmed bookings for the same slot.
     * We expect the second save to throw a DataIntegrityViolationException.
     */
    @Test
    void testDatabaseConstraintPreventsDoubleBooking() {
        // Create a slot via the service (or repository) to ensure it exists
        com.example.roombooking.model.Slot slot = slotRepository.save(
                com.example.roombooking.model.Slot.builder()
                        .room(testRoom)
                        .startTime(futureStartTime)
                        .endTime(futureStartTime.plusHours(1))
                        .build()
        );

        // Create first confirmed booking
        Booking booking1 = new Booking();
        booking1.setRoom(testRoom);
        booking1.setSlot(slot);
        booking1.setUserId("user1");
        booking1.setStatus("CONFIRMED");
        booking1.setCreatedAt(LocalDateTime.now());
        bookingRepository.save(booking1);

        // Attempt to create a second confirmed booking for the same slot
        Booking booking2 = new Booking();
        booking2.setRoom(testRoom);
        booking2.setSlot(slot);
        booking2.setUserId("user2");
        booking2.setStatus("CONFIRMED");
        booking2.setCreatedAt(LocalDateTime.now());

        // This should throw a DataIntegrityViolationException due to the unique index
        assertThatThrownBy(() -> bookingRepository.save(booking2))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}