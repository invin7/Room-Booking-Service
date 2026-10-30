package com.example.roombooking;

import com.example.roombooking.dto.RoomRequest;
import com.example.roombooking.dto.RoomResponse;
import com.example.roombooking.model.AppUser;
import com.example.roombooking.model.Role;
import com.example.roombooking.repository.BookingRepository;
import com.example.roombooking.repository.RoomRepository;
import com.example.roombooking.repository.SlotRepository;
import com.example.roombooking.repository.UserRepository;
import com.example.roombooking.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Clean database per test, plus helpers for creating users and calling the API as them. */
public abstract class ApiTestSupport extends IntegrationTestBase {
    @Autowired protected TestRestTemplate http;
    @Autowired protected BookingRepository bookingRepository;
    @Autowired protected SlotRepository slotRepository;
    @Autowired protected RoomRepository roomRepository;
    @Autowired protected UserRepository userRepository;
    @Autowired protected TokenService tokenService;

    protected String adminToken;
    protected AppUser admin;

    @BeforeEach
    void cleanDatabase() {
        bookingRepository.deleteAll();
        slotRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
        admin = createUser("Admin", Role.ADMIN);
        adminToken = tokenFor(admin);
    }

    protected AppUser createUser(String name, Role role) {
        return userRepository.save(AppUser.builder()
                .name(name).email(name.toLowerCase().replace(' ', '.') + "-" + UUID.randomUUID() + "@example.com")
                .passwordHash("not-a-real-hash").role(role).createdAt(OffsetDateTime.now(ZoneOffset.UTC)).build());
    }

    protected String tokenFor(AppUser user) {
        return tokenService.issue(user).value();
    }

    /** Admin creates a Kolkata room open 09:00-19:00. */
    protected RoomResponse createRoom(String name) {
        ResponseEntity<RoomResponse> response = call(HttpMethod.POST, "/rooms", adminToken,
                new RoomRequest(name, 10, "A room", "Floor 1", "Asia/Kolkata", 9, 19), RoomResponse.class);
        if (!response.getStatusCode().is2xxSuccessful()) throw new AssertionError("room not created: " + response);
        return response.getBody();
    }

    /** The given local hour, two days from now, in the room's zone. */
    protected OffsetDateTime slotAt(RoomResponse room, int localHour) {
        return OffsetDateTime.now(ZoneOffset.UTC).plusDays(2)
                .atZoneSameInstant(ZoneId.of(room.timezone()))
                .truncatedTo(ChronoUnit.DAYS).plusHours(localHour).toOffsetDateTime();
    }

    protected <T> ResponseEntity<T> call(HttpMethod method, String path, String token, Object body, Class<T> type,
                                         Object... uriVars) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(path, method, new HttpEntity<>(body, headers), type, uriVars);
    }
}
