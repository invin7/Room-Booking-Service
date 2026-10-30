package com.example.roombooking.config;

import com.example.roombooking.security.JsonErrorHandlers;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;

@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JsonErrorHandlers errorHandlers) throws Exception {
        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(jwt ->
                List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role"))));

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/register", "/auth/login", "/actuator/health", "/error").permitAll()
                        // the single-page UI is public; every API call it makes needs a token
                        .requestMatchers(HttpMethod.GET, "/", "/index.html", "/favicon.ico",
                                "/css/**", "/js/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/rooms").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/rooms/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/rooms/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/bookings").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter))
                        .authenticationEntryPoint(errorHandlers)
                        .accessDeniedHandler(errorHandlers))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errorHandlers)
                        .accessDeniedHandler(errorHandlers));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * HS256 signing key from APP_JWT_SECRET (at least 32 characters). Without one a random key is
     * generated, which is fine for local runs but invalidates every session on restart.
     */
    @Bean
    SecretKey jwtSecretKey(@Value("${app.jwt.secret:}") String secret) {
        byte[] bytes;
        if (secret.isBlank()) {
            log.warn("app.jwt.secret is not set; using a random key, so logins will not survive a restart");
            bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
        } else {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
            if (bytes.length < 32) {
                throw new IllegalStateException("app.jwt.secret must be at least 32 bytes long");
            }
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
