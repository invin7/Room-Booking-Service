package com.example.roombooking.controller;

import com.example.roombooking.dto.BookingRequest;
import com.example.roombooking.dto.BookingResponse;
import com.example.roombooking.dto.RoomRequest;
import com.example.roombooking.dto.RoomResponse;
import com.example.roombooking.dto.SlotResponse;
import com.example.roombooking.repository.BookingRepository;
import com.example.roombooking.repository.RoomRepository;
import com.example.roombooking.repository.SlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class BookingControllerTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("roombooking").withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired TestRestTemplate restTemplate;
    @Autowired BookingRepository bookingRepository;
    @Autowired SlotRepository slotRepository;
    @Autowired RoomRepository roomRepository;

    private Long roomId;
    private OffsetDateTime startTime;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        slotRepository.deleteAll();
        roomRepository.deleteAll();
        ResponseEntity<RoomResponse> room = restTemplate.postForEntity("/rooms",
                new RoomRequest("Atlas", 10), RoomResponse.class);
        assertThat(room.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        roomId = room.getBody().id();
        startTime = OffsetDateTime.now(ZoneOffset.UTC).plusDays(2).truncatedTo(java.time.temporal.ChronoUnit.HOURS);
    }

    @Test
    void booksCanonicalSlotAndCancellationMakesItAvailableAgain() {
        assertThat(availability()).hasSize(24);
        ResponseEntity<BookingResponse> created = book("alex");
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().status()).isEqualTo("CONFIRMED");
        assertThat(availability()).hasSize(23);

        ResponseEntity<Void> cancelled = restTemplate.exchange("/bookings/{id}", HttpMethod.DELETE,
                null, Void.class, created.getBody().id());
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(availability()).hasSize(24);
    }

    @Test
    void rejectsNonCanonicalStartAndUnknownRoom() {
        BookingRequest unaligned = new BookingRequest();
        unaligned.setRoomId(roomId);
        unaligned.setUserId("alex");
        unaligned.setStartTime(startTime.plusMinutes(30));
        assertThat(post(unaligned, BookingResponse.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        BookingRequest missingRoom = new BookingRequest();
        missingRoom.setRoomId(999999L);
        missingRoom.setUserId("alex");
        missingRoom.setStartTime(startTime);
        assertThat(post(missingRoom, BookingResponse.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void allowsExactlyOneConcurrentConfirmedBooking() throws Exception {
        int clients = 20;
        CountDownLatch ready = new CountDownLatch(clients);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        List<Future<HttpStatusCode>> requests = new ArrayList<>();
        for (int i = 0; i < clients; i++) {
            String user = "user-" + i;
            requests.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return book(user).getStatusCode();
            }));
        }
        ready.await();
        start.countDown();
        int successes = 0;
        int conflicts = 0;
        for (Future<HttpStatusCode> request : requests) {
            if (request.get() == HttpStatus.CREATED) successes++;
            if (request.get() == HttpStatus.CONFLICT) conflicts++;
        }
        pool.shutdown();
        assertThat(successes).isEqualTo(1);
        assertThat(conflicts).isEqualTo(clients - 1);
    }

    private List<SlotResponse> availability() {
        ResponseEntity<List<SlotResponse>> response = restTemplate.exchange(
                "/rooms/{id}/availability?date={date}", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<SlotResponse>>() {}, roomId, LocalDate.from(startTime));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<BookingResponse> book(String userId) {
        BookingRequest request = new BookingRequest();
        request.setRoomId(roomId);
        request.setStartTime(startTime);
        request.setUserId(userId);
        return post(request, BookingResponse.class);
    }

    private <T> ResponseEntity<T> post(BookingRequest request, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange("/bookings", HttpMethod.POST, new HttpEntity<>(request, headers), type);
    }
}
