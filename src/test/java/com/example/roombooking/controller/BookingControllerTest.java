package com.example.roombooking.controller;

import com.example.roombooking.ApiTestSupport;
import com.example.roombooking.advice.ErrorResponse;
import com.example.roombooking.dto.BookingRequest;
import com.example.roombooking.dto.BookingResponse;
import com.example.roombooking.dto.RoomResponse;
import com.example.roombooking.dto.SlotResponse;
import com.example.roombooking.model.AppUser;
import com.example.roombooking.model.BookingStatus;
import com.example.roombooking.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class BookingControllerTest extends ApiTestSupport {
    private RoomResponse room;
    private OffsetDateTime tenAm;
    private AppUser alex;
    private String alexToken;

    @BeforeEach
    void setUp() {
        room = createRoom("Atlas");
        tenAm = slotAt(room, 10);
        alex = createUser("Alex", Role.USER);
        alexToken = tokenFor(alex);
    }

    @Test
    void booksSlotAsTheSignedInUserAndCancellationMakesItAvailableAgain() {
        assertThat(availableCount()).isEqualTo(10);
        ResponseEntity<BookingResponse> created = book(alexToken, tenAm);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(created.getBody().userId()).isEqualTo(alex.getId());
        assertThat(created.getBody().userName()).isEqualTo("Alex");
        assertThat(availableCount()).isEqualTo(9);

        assertThat(cancel(alexToken, created.getBody().id()).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(availableCount()).isEqualTo(10);
    }

    @Test
    void availabilityListsEveryOpeningHourInTheRoomsZoneAndFlagsBookedOnes() {
        book(alexToken, tenAm);
        List<SlotResponse> slots = availability();
        assertThat(slots).hasSize(10);
        ZoneId zone = ZoneId.of(room.timezone());
        assertThat(slots.get(0).startTime().toInstant().atZone(zone).getHour()).isEqualTo(9);
        assertThat(slots.get(9).startTime().toInstant().atZone(zone).getHour()).isEqualTo(18);
        assertThat(slots).filteredOn(s -> !s.available()).extracting(SlotResponse::startTime)
                .singleElement().satisfies(t -> assertThat(t.toInstant()).isEqualTo(tenAm.toInstant()));
    }

    @Test
    void rejectsBadStartTimesAndUnknownRooms() {
        assertThat(bookStatus(alexToken, tenAm.plusMinutes(30))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bookStatus(alexToken, slotAt(room, 8))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bookStatus(alexToken, slotAt(room, 19))).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(bookStatus(alexToken, tenAm.minusDays(10))).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<ErrorResponse> missing = call(HttpMethod.POST, "/bookings", alexToken,
                new BookingRequest(999999L, tenAm), ErrorResponse.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void secondBookingOfTheSameSlotConflicts() {
        assertThat(bookStatus(alexToken, tenAm)).isEqualTo(HttpStatus.CREATED);
        AppUser sam = createUser("Sam", Role.USER);
        assertThat(bookStatus(tokenFor(sam), tenAm)).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void usersCanOnlyCancelTheirOwnBookingsButAdminsCanCancelAny() {
        Long id = book(alexToken, tenAm).getBody().id();
        String samToken = tokenFor(createUser("Sam", Role.USER));

        assertThat(cancel(samToken, id).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(availableCount()).isEqualTo(9);

        assertThat(cancel(adminToken, id).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(cancel(alexToken, id).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(cancel(alexToken, 999999L).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void myBookingsOnlyContainsTheCallersBookings() {
        String samToken = tokenFor(createUser("Sam", Role.USER));
        book(alexToken, tenAm);
        book(samToken, slotAt(room, 11));

        ResponseEntity<List<BookingResponse>> mine = http.exchange("/bookings/me", HttpMethod.GET,
                new HttpEntity<>(bearer(alexToken)), new ParameterizedTypeReference<>() {});
        assertThat(mine.getBody()).hasSize(1);
        assertThat(mine.getBody().get(0).userId()).isEqualTo(alex.getId());
    }

    @Test
    void allowsExactlyOneConcurrentConfirmedBooking() throws Exception {
        int clients = 50;
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < clients; i++) tokens.add(tokenFor(createUser("User " + i, Role.USER)));

        CountDownLatch ready = new CountDownLatch(clients);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        List<Future<HttpStatusCode>> requests = new ArrayList<>();
        for (String token : tokens) {
            requests.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return bookStatus(token, tenAm);
            }));
        }
        ready.await();
        start.countDown();
        int successes = 0;
        int conflicts = 0;
        for (Future<HttpStatusCode> request : requests) {
            HttpStatusCode status = request.get();
            if (status == HttpStatus.CREATED) successes++;
            if (status == HttpStatus.CONFLICT) conflicts++;
        }
        pool.shutdown();
        assertThat(successes).isEqualTo(1);
        assertThat(conflicts).isEqualTo(clients - 1);
    }

    private long availableCount() {
        return availability().stream().filter(SlotResponse::available).count();
    }

    private List<SlotResponse> availability() {
        ResponseEntity<List<SlotResponse>> response = http.exchange(
                "/rooms/{id}/availability?date={date}", HttpMethod.GET, new HttpEntity<>(bearer(alexToken)),
                new ParameterizedTypeReference<>() {}, room.id(),
                LocalDate.from(tenAm.atZoneSameInstant(ZoneId.of(room.timezone()))));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private ResponseEntity<BookingResponse> book(String token, OffsetDateTime start) {
        return call(HttpMethod.POST, "/bookings", token, new BookingRequest(room.id(), start), BookingResponse.class);
    }

    /** Status only, so error bodies are never parsed as a booking. */
    private HttpStatusCode bookStatus(String token, OffsetDateTime start) {
        return call(HttpMethod.POST, "/bookings", token, new BookingRequest(room.id(), start), String.class)
                .getStatusCode();
    }

    private ResponseEntity<Void> cancel(String token, Long id) {
        return call(HttpMethod.DELETE, "/bookings/{id}", token, null, Void.class, id);
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
