package com.example.roombooking.controller;

import com.example.roombooking.ApiTestSupport;
import com.example.roombooking.advice.ErrorResponse;
import com.example.roombooking.dto.AuthResponse;
import com.example.roombooking.dto.BookingRequest;
import com.example.roombooking.dto.BookingResponse;
import com.example.roombooking.dto.LoginRequest;
import com.example.roombooking.dto.RegisterRequest;
import com.example.roombooking.dto.RoomRequest;
import com.example.roombooking.dto.RoomResponse;
import com.example.roombooking.dto.UserResponse;
import com.example.roombooking.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthAndRoomsTest extends ApiTestSupport {

    @Test
    void registerThenLoginThenMe() {
        ResponseEntity<AuthResponse> registered = call(HttpMethod.POST, "/auth/register", null,
                new RegisterRequest("Jo Smith", "Jo@Example.com", "correct horse"), AuthResponse.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registered.getBody().user().role()).isEqualTo(Role.USER);
        assertThat(registered.getBody().user().email()).isEqualTo("jo@example.com");

        ResponseEntity<AuthResponse> login = call(HttpMethod.POST, "/auth/login", null,
                new LoginRequest("JO@example.com", "correct horse"), AuthResponse.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<UserResponse> me = call(HttpMethod.GET, "/auth/me", login.getBody().token(), null, UserResponse.class);
        assertThat(me.getBody().name()).isEqualTo("Jo Smith");
    }

    @Test
    void registrationAndLoginRejectBadInput() {
        call(HttpMethod.POST, "/auth/register", null,
                new RegisterRequest("Jo", "jo@example.com", "correct horse"), AuthResponse.class);

        assertThat(call(HttpMethod.POST, "/auth/register", null,
                new RegisterRequest("Jo Again", "JO@example.com", "another pass"), ErrorResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(HttpMethod.POST, "/auth/register", null,
                new RegisterRequest("Al", "al@example.com", "short"), ErrorResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.POST, "/auth/register", null,
                new RegisterRequest("Al", "not-an-email", "long enough pw"), ErrorResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.POST, "/auth/login", null,
                new LoginRequest("jo@example.com", "wrong password"), ErrorResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(HttpMethod.POST, "/auth/login", null,
                new LoginRequest("nobody@example.com", "whatever"), ErrorResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void registeringNeverGrantsAdmin() {
        ResponseEntity<String> raw = http.exchange("/auth/register", HttpMethod.POST, new HttpEntity<>(
                "{\"name\":\"Eve\",\"email\":\"eve@example.com\",\"password\":\"long enough pw\",\"role\":\"ADMIN\"}",
                json()), String.class);
        assertThat(raw.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(raw.getBody()).contains("\"role\":\"USER\"");
    }

    @Test
    void apiRequiresAValidToken() {
        for (String path : List.of("/rooms", "/bookings/me", "/auth/me")) {
            ResponseEntity<ErrorResponse> response = call(HttpMethod.GET, path, null, null, ErrorResponse.class);
            assertThat(response.getStatusCode()).as(path).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(response.getBody().getStatus()).isEqualTo(401);
        }
        assertThat(call(HttpMethod.GET, "/rooms", "garbage.token.value", null, ErrorResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(HttpMethod.GET, "/actuator/health", null, null, String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void onlyAdminsManageRoomsAndSeeAllBookings() {
        String userToken = tokenFor(createUser("Alex", Role.USER));
        RoomRequest request = new RoomRequest("Borealis", 6, null, null, null, null, null);

        assertThat(call(HttpMethod.POST, "/rooms", userToken, request, ErrorResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET, "/bookings", userToken, null, ErrorResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<RoomResponse> created = call(HttpMethod.POST, "/rooms", adminToken, request, RoomResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().timezone()).isEqualTo("UTC");
        assertThat(created.getBody().openHour()).isEqualTo(8);
        assertThat(created.getBody().closeHour()).isEqualTo(18);

        assertThat(call(HttpMethod.PUT, "/rooms/{id}", userToken, request, ErrorResponse.class, created.getBody().id())
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<RoomResponse> updated = call(HttpMethod.PUT, "/rooms/{id}", adminToken,
                new RoomRequest("Borealis 2", 8, "New", "Floor 9", "Europe/London", 7, 21), RoomResponse.class,
                created.getBody().id());
        assertThat(updated.getBody().name()).isEqualTo("Borealis 2");
        assertThat(updated.getBody().timezone()).isEqualTo("Europe/London");

        assertThat(call(HttpMethod.GET, "/rooms", userToken, null, String.class).getBody()).contains("Borealis 2");
    }

    @Test
    void roomInputIsValidated() {
        assertThat(call(HttpMethod.POST, "/rooms", adminToken,
                new RoomRequest("X", 4, null, null, "Mars/Olympus", null, null), ErrorResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.POST, "/rooms", adminToken,
                new RoomRequest("X", 4, null, null, null, 18, 9), ErrorResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.POST, "/rooms", adminToken,
                new RoomRequest(" ", 4, null, null, null, null, null), ErrorResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void roomWithUpcomingBookingsCannotBeDeletedUntilTheyAreCancelled() {
        RoomResponse room = createRoom("Atlas");
        ResponseEntity<BookingResponse> booking = call(HttpMethod.POST, "/bookings", adminToken,
                new BookingRequest(room.id(), slotAt(room, 10)), BookingResponse.class);

        assertThat(call(HttpMethod.DELETE, "/rooms/{id}", adminToken, null, ErrorResponse.class, room.id())
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        call(HttpMethod.DELETE, "/bookings/{id}", adminToken, null, Void.class, booking.getBody().id());
        assertThat(call(HttpMethod.DELETE, "/rooms/{id}", adminToken, null, Void.class, room.id())
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(call(HttpMethod.GET, "/rooms/{id}", adminToken, null, ErrorResponse.class, room.id())
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void adminSeesUpcomingBookingsOfEveryone() {
        RoomResponse room = createRoom("Atlas");
        String userToken = tokenFor(createUser("Alex", Role.USER));
        call(HttpMethod.POST, "/bookings", userToken, new BookingRequest(room.id(), slotAt(room, 10)), BookingResponse.class);

        ResponseEntity<List<BookingResponse>> all = http.exchange("/bookings", HttpMethod.GET,
                new HttpEntity<>(bearer(adminToken)), new ParameterizedTypeReference<>() {});
        assertThat(all.getBody()).hasSize(1);
        assertThat(all.getBody().get(0).userName()).isEqualTo("Alex");
    }

    @Test
    void malformedJsonAndUnknownPathsAreClientErrorsNotServerErrors() {
        HttpHeaders headers = json();
        headers.setBearerAuth(adminToken);
        assertThat(http.exchange("/rooms", HttpMethod.POST, new HttpEntity<>("{not json", headers), ErrorResponse.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.GET, "/nope", adminToken, null, ErrorResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(HttpMethod.GET, "/rooms/abc", adminToken, null, ErrorResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(HttpMethod.PATCH, "/rooms", adminToken, null, ErrorResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private static HttpHeaders json() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
