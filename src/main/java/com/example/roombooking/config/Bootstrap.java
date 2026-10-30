package com.example.roombooking.config;

import com.example.roombooking.dto.RoomRequest;
import com.example.roombooking.model.Role;
import com.example.roombooking.repository.RoomRepository;
import com.example.roombooking.repository.UserRepository;
import com.example.roombooking.service.AuthService;
import com.example.roombooking.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * First-run setup. There is no public way to become an admin, so the first one comes from
 * APP_ADMIN_EMAIL / APP_ADMIN_PASSWORD. Demo rooms are only added when APP_SEED_DEMO_ROOMS=true.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class Bootstrap implements ApplicationRunner {
    private final AuthService authService;
    private final UserRepository userRepository;
    private final RoomService roomService;
    private final RoomRepository roomRepository;

    @Value("${app.admin.email:}") private String adminEmail;
    @Value("${app.admin.password:}") private String adminPassword;
    @Value("${app.seed-demo-rooms:false}") private boolean seedDemoRooms;

    @Override
    public void run(ApplicationArguments args) {
        if (!adminEmail.isBlank() && !adminPassword.isBlank()
                && !userRepository.existsByEmailIgnoreCase(adminEmail)) {
            authService.createUser("Admin", adminEmail, adminPassword, Role.ADMIN);
            log.info("Created admin account {}", adminEmail);
        }
        if (seedDemoRooms && roomRepository.count() == 0) {
            roomService.createRoom(new RoomRequest("Atlas", 12, "Large boardroom with a wall display and video conferencing.",
                    "Floor 3, East wing", "Asia/Kolkata", 9, 19));
            roomService.createRoom(new RoomRequest("Borealis", 6, "Mid-size meeting room with a whiteboard wall.",
                    "Floor 2, West wing", "Asia/Kolkata", 9, 19));
            roomService.createRoom(new RoomRequest("Cedar", 4, "Quiet room for small teams and interviews.",
                    "Floor 2, West wing", "Asia/Kolkata", 9, 19));
            roomService.createRoom(new RoomRequest("Delta", 20, "Training room, theatre seating and a projector.",
                    "Ground floor", "Asia/Kolkata", 8, 20));
            log.info("Seeded demo rooms");
        }
    }
}
