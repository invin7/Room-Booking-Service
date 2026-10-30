package com.example.roombooking.service;

import com.example.roombooking.dto.AuthResponse;
import com.example.roombooking.dto.LoginRequest;
import com.example.roombooking.dto.RegisterRequest;
import com.example.roombooking.exception.ConflictException;
import com.example.roombooking.exception.UnauthorizedException;
import com.example.roombooking.model.AppUser;
import com.example.roombooking.model.Role;
import com.example.roombooking.dto.UserResponse;
import com.example.roombooking.repository.UserRepository;
import com.example.roombooking.security.TokenService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    /** Compared against when the email is unknown, so a miss costs the same as a wrong password. */
    private final String decoyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.decoyHash = passwordEncoder.encode("decoy-password");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        return respond(createUser(request.name(), request.email(), request.password(), Role.USER));
    }

    /** Creates an account; used for sign-up and for the bootstrap admin. */
    @Transactional
    public AppUser createUser(String name, String email, String password, Role role) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalized)) {
            throw new ConflictException("An account with this email already exists");
        }
        try {
            return userRepository.save(AppUser.builder()
                    .name(name.trim()).email(normalized)
                    .passwordHash(passwordEncoder.encode(password))
                    .role(role).createdAt(OffsetDateTime.now(ZoneOffset.UTC)).build());
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("An account with this email already exists");
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        AppUser user = userRepository.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        boolean matches = passwordEncoder.matches(request.password(), user != null ? user.getPasswordHash() : decoyHash);
        if (user == null || !matches) {
            throw new UnauthorizedException("Invalid email or password");
        }
        return respond(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return userRepository.findById(userId).map(UserResponse::from)
                .orElseThrow(() -> new UnauthorizedException("Account no longer exists"));
    }

    private AuthResponse respond(AppUser user) {
        TokenService.IssuedToken token = tokenService.issue(user);
        return new AuthResponse(token.value(), token.expiresAt(), UserResponse.from(user));
    }
}
