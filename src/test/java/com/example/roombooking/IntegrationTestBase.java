package com.example.roombooking;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Runs against the Postgres named by TEST_DB_URL (plus optional TEST_DB_USER / TEST_DB_PASSWORD)
 * when set, otherwise against a Testcontainers Postgres, which needs Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {
    private static PostgreSQLContainer<?> container;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("TEST_DB_URL");
        if (url != null) {
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DB_USER", "test"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", "test"));
            return;
        }
        synchronized (IntegrationTestBase.class) {
            if (container == null) {
                container = new PostgreSQLContainer<>("postgres:15")
                        .withDatabaseName("roombooking").withUsername("test").withPassword("test");
                container.start();
            }
        }
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
    }
}
