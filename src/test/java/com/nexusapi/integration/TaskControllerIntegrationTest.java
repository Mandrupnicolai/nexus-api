package com.nexusapi.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexusapi.dto.request.CreateTaskRequest;
import com.nexusapi.dto.request.LoginRequest;
import com.nexusapi.dto.request.RegisterRequest;
import com.nexusapi.dto.response.AuthResponse;
import com.nexusapi.entity.Task;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests for the Task API endpoints.
 *
 * <p>Uses Testcontainers to spin up a real PostgreSQL instance in Docker.
 * This means tests run against the actual database schema and Flyway migrations,
 * catching issues that in-memory databases like H2 would miss (e.g. PostgreSQL-
 * specific SQL syntax, constraint names, UUID generation).
 *
 * <p>Test lifecycle:
 * <ol>
 *   <li>PostgreSQL container starts once per test class (shared container pattern)</li>
 *   <li>{@link DynamicPropertySource} overrides {@code spring.datasource.*} to point
 *       at the container</li>
 *   <li>Flyway runs all migrations on the fresh schema</li>
 *   <li>Each test gets a clean HTTP context via {@link MockMvc}</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Task API integration tests")
class TaskControllerIntegrationTest {

    // Shared container — starts once for the entire test class for speed
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("nexusapi_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Disable Redis for integration tests — use a no-op cache
        registry.add("spring.cache.type", () -> "none");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // Shared state across ordered tests — simulates a real user session
    private static String authToken;
    private static UUID projectId;
    private static UUID createdTaskId;

    // ---------------------------------------------------------------------------
    // Auth setup
    // ---------------------------------------------------------------------------

    @Test
    @Order(1)
    @DisplayName("POST /api/v1/auth/register — registers a new user")
    void register_validRequest_returns201() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "integration@test.com", "intuser", "Password123!", "Integration User"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.email").value("integration@test.com"))
            .andReturn();

        AuthResponse response = objectMapper.readValue(
            result.getResponse().getContentAsString(), AuthResponse.class
        );
        authToken = response.accessToken();
    }

    @Test
    @Order(2)
    @DisplayName("POST /api/v1/auth/login — authenticates existing user")
    void login_validCredentials_returns200WithToken() throws Exception {
        LoginRequest request = new LoginRequest("integration@test.com", "Password123!");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    @Order(3)
    @DisplayName("POST /api/v1/auth/login — rejects invalid credentials with 401")
    void login_wrongPassword_returns401() throws Exception {
        LoginRequest request = new LoginRequest("integration@test.com", "wrongpassword");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------
    // Task CRUD
    // ---------------------------------------------------------------------------

    @Test
    @Order(10)
    @DisplayName("GET /api/v1/tasks — requires authentication, returns 401 without token")
    void getTasks_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/tasks")
                .param("projectId", UUID.randomUUID().toString()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(11)
    @DisplayName("POST /api/v1/tasks — validates request body, returns 400 on blank title")
    void createTask_blankTitle_returns400WithFieldErrors() throws Exception {
        // Use seed data project from V2 migration
        CreateTaskRequest request = new CreateTaskRequest(
            "", // blank title — should fail validation
            null,
            UUID.fromString("00000000-0000-0000-0002-000000000001"),
            null, null, null
        );

        mockMvc.perform(post("/api/v1/tasks")
                .header("Authorization", "Bearer " + authToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.title").isNotEmpty());
    }

    @Test
    @Order(12)
    @DisplayName("GET /api/v1/tasks/{id} — returns 404 for non-existent task")
    void getTaskById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/{id}", UUID.randomUUID())
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Not Found"));
    }

    // ---------------------------------------------------------------------------
    // Auth edge cases
    // ---------------------------------------------------------------------------

    @Test
    @Order(20)
    @DisplayName("POST /api/v1/auth/register — returns 409 on duplicate email")
    void register_duplicateEmail_returns409() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "integration@test.com", "differentuser", "Password123!", null
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Email address is already registered"));
    }

    @Test
    @Order(21)
    @DisplayName("POST /api/v1/auth/register — validates email format")
    void register_invalidEmail_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "not-an-email", "someuser", "Password123!", null
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.email").exists());
    }
}
