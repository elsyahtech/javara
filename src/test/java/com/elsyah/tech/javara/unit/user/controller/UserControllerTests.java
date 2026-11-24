package com.elsyah.tech.javara.unit.user.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.elsyah.tech.javara.entity.user.UserEntity;
import com.elsyah.tech.javara.projection.user.UserSelectColumn;
import com.elsyah.tech.javara.request.user.UserRequest;
import com.elsyah.tech.javara.request.user.UserUpdateRequest;
import com.elsyah.tech.javara.response.common.ApiResponse;
import com.elsyah.tech.javara.service.common.ServiceResult;
import com.elsyah.tech.javara.response.user.UserResponse;
import com.elsyah.tech.javara.response.user.UserResponseWithSelectColumn;
import com.elsyah.tech.javara.service.user.UserService;
import com.elsyah.tech.javara.controller.user.UserController;


/**
 * 
 * UserControllerTests
 * This class contains unit tests for the UserController class, which is responsible for handling user-related HTTP requests.
 * It uses Mockito to mock the UserService and test the behavior of the controller methods.
 * The tests cover various scenarios, including creating a user, finding all users, finding users with selected columns, 
 * finding users with limit and offset, finding a user by ID, updating a user, and deleting a user.
 * Each test method sets up the necessary mock behavior for the UserService, invokes the corresponding controller method, 
 * and asserts that the response matches the expected outcome.
 * The tests ensure that the UserController class behaves as expected and correctly interacts with the UserService to 
 * handle user-related operations.
 * 
 * @author Elmansyah
 * @version 1.0.0
 * @since 2026-11-18
 * @contact https://www.linkedin.com/in/elmansyah
 */
class UserControllerTests {
    // Verifies that invalid create payloads return HTTP 400 before calling the service.
    @Test
    void createUserWithInvalidPayloadReturnsBadRequest() throws Exception {
        UserService service = mock(UserService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new UserController(service)).build();

        mockMvc.perform(post("/api/v1/users")
                        .contentType("application/json")
                        .content("{\"email\":\"invalid-email\",\"password\":\"short\",\"isActive\":true}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    // Verifies that invalid update payloads return HTTP 400 before calling the service.
    @Test
    void updateUserWithInvalidPayloadReturnsBadRequest() throws Exception {
        UserService service = mock(UserService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new UserController(service)).build();

        mockMvc.perform(put("/api/v1/users/11111111-1111-1111-1111-111111111111")
                        .contentType("application/json")
                        .content("{\"email\":\" \",\"password\":\"short\",\"isActive\":true}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    // Verifies that malformed ID formats are rejected by route constraints.
    @Test
    void invalidUserIdDoesNotCallTheService() throws Exception {
        UserService service = mock(UserService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new UserController(service)).build();

        mockMvc.perform(get("/api/v1/users/not-a-valid-id"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(service);
    }

    // Verifies that the old verb-based list paths are no longer exposed.
    @Test
    void verbBasedListPathsAreNotFound() throws Exception {
        UserService service = mock(UserService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new UserController(service)).build();

        mockMvc.perform(get("/api/v1/users/findAllWithSelectedColumn"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users/findAllWithLimitAndOffset?page=0&size=10"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(service);
    }

    // Verifies that user creation wraps the service result with the service status and message.
    @Test
    void createUserReturnsWrappedResponseUsingServiceStatusAndMessage() {
        UserService service = mock(UserService.class);
        UserRequest request = new UserRequest("new@example.com", "password123", true);
        UserEntity user = user(uuid("user-1"), "new@example.com", true);
        when(service.createUser(request)).thenReturn(new ServiceResult<>(
                user,
                HttpStatus.CREATED,
                "User created successfully with id: user-1"));

        ResponseEntity<ApiResponse<UserResponse>> response =
                new UserController(service).createUser(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(201, response.getBody().getHttpCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals("User created successfully with id: user-1", response.getBody().getMessage());
        assertEquals(uuid("user-1"), response.getBody().getData().id());
        verify(service).createUser(request);
    }

    // Verifies that the canonical users endpoint maps the page and total metadata.
    @Test
    void findAllUsersReturnsPageWithMappedResponsesAndMetadata() {
        UserService service = mock(UserService.class);
        UserSelectColumn projection = projection(uuid("user-1"), "first@example.com", true);
        Page<UserSelectColumn> page = new PageImpl<>(
                List.of(projection),
                PageRequest.of(1, 10),
                21);
        when(service.findAllUsers(PageRequest.of(1, 10))).thenReturn(new ServiceResult<>(
                page,
                HttpStatus.OK,
                "Users retrieved successfully"));

        ResponseEntity<ApiResponse<List<UserResponseWithSelectColumn>>> response =
                new UserController(service).findAllUsers(1, 10);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getHttpCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals("Users retrieved successfully", response.getBody().getMessage());
        assertEquals(1, response.getBody().getData().size());
        assertEquals("first@example.com", response.getBody().getData().get(0).email());
        assertEquals(1, response.getBody().getAdditionalFields().get("count"));
        assertEquals(1, response.getBody().getAdditionalFields().get("page"));
        assertEquals(10, response.getBody().getAdditionalFields().get("size"));
        assertEquals(21L, response.getBody().getAdditionalFields().get("totalElements"));
        assertEquals(3, response.getBody().getAdditionalFields().get("totalPages"));
        assertEquals(true, response.getBody().getAdditionalFields().get("hasNext"));
        assertEquals(true, response.getBody().getAdditionalFields().get("hasPrevious"));
        verify(service).findAllUsers(PageRequest.of(1, 10));
    }

    // Verifies that an empty page remains a successful response with zero totals.
    @Test
    void findAllUsersReturnsEmptyPageWithZeroTotalCount() {
        UserService service = mock(UserService.class);
        Page<UserSelectColumn> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(service.findAllUsers(PageRequest.of(0, 20))).thenReturn(
                new ServiceResult<>(page, HttpStatus.OK, "Users retrieved successfully"));

        ResponseEntity<ApiResponse<List<UserResponseWithSelectColumn>>> response =
                new UserController(service).findAllUsers(0, 20);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getHttpCode());
        assertTrue(response.getBody().isSuccess());
        assertTrue(response.getBody().getData().isEmpty());
        assertEquals(0L, response.getBody().getAdditionalFields().get("totalElements"));
        assertEquals(0, response.getBody().getAdditionalFields().get("totalPages"));
        verify(service).findAllUsers(PageRequest.of(0, 20));
    }

    @Test
    void usersEndpointReturnsSerializedPageMetadataAndUsesDefaultParameters() throws Exception {
        UserService service = mock(UserService.class);
        Page<UserSelectColumn> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(service.findAllUsers(PageRequest.of(0, 20))).thenReturn(
                new ServiceResult<>(page, HttpStatus.OK, "Users retrieved successfully"));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new UserController(service)).build();

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", Matchers.hasSize(0)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(false));

        verify(service).findAllUsers(PageRequest.of(0, 20));
    }

    @Test
    void findAllUsersRejectsNegativePage() {
        UserService service = mock(UserService.class);
        ResponseEntity<ApiResponse<List<UserResponseWithSelectColumn>>> response =
                new UserController(service).findAllUsers(-1, 10);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        verifyNoInteractions(service);
    }

    @Test
    void findAllUsersRejectsInvalidSize() {
        UserService service = mock(UserService.class);
        ResponseEntity<ApiResponse<List<UserResponseWithSelectColumn>>> response =
                new UserController(service).findAllUsers(0, 101);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        verifyNoInteractions(service);
    }

    // Verifies that a found user is returned through the standard API response wrapper.
    @Test
    void findUserByIdReturnsWrappedResponseFromService() {
        UserService service = mock(UserService.class);
        UserEntity user = user(uuid("user-1"), "user@example.com", true);
        when(service.findUserById(uuid("user-1"))).thenReturn(
                new ServiceResult<>(user, HttpStatus.OK, "User found"));

        ResponseEntity<ApiResponse<UserResponse>> response =
                new UserController(service).findUserById(uuid("user-1"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getHttpCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals("User found", response.getBody().getMessage());
        assertEquals(uuid("user-1"), response.getBody().getData().id());
        assertEquals("user@example.com", response.getBody().getData().email());
        verify(service).findUserById(uuid("user-1"));
    }

    // Verifies that a missing user is returned as a wrapped not-found response.
    @Test
    void findUserByIdReturnsWrappedNotFoundResult() {
        UserService service = mock(UserService.class);
        when(service.findUserById(uuid("missing-id"))).thenReturn(
                new ServiceResult<>(null, HttpStatus.NOT_FOUND, "User not found with id: missing-id"));

        ResponseEntity<ApiResponse<UserResponse>> response =
                new UserController(service).findUserById(uuid("missing-id"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getHttpCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("User not found with id: missing-id", response.getBody().getError());
        assertNull(response.getBody().getData());
    }

    // Verifies that a selected-column user lookup is mapped correctly.
    @Test
    void findUserByIdWithSelectColumnReturnsResponseFromService() {
        UserService service = mock(UserService.class);
        UserSelectColumn projection = projection(uuid("user-1"), "user@example.com", true);
        when(service.findUserByIdWithSelectedColumns(uuid("user-1"))).thenReturn(
                new ServiceResult<>(projection, HttpStatus.OK, "User found"));

        ResponseEntity<ApiResponse<UserResponseWithSelectColumn>> response =
                new UserController(service).findUserByIdWithSelectedColumns(uuid("user-1"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(uuid("user-1"), response.getBody().getData().id());
        assertEquals("user@example.com", response.getBody().getData().email());
        verify(service).findUserByIdWithSelectedColumns(uuid("user-1"));
    }

    // Verifies that selected-column lookup returns not-found for a missing user.
    @Test
    void findUserByIdWithSelectColumnReturnsNotFoundResponse() {
        UserService service = mock(UserService.class);
        when(service.findUserByIdWithSelectedColumns(uuid("missing-id"))).thenReturn(
                new ServiceResult<>(null, HttpStatus.NOT_FOUND,
                        "User not found with id: missing-id"));

        ResponseEntity<ApiResponse<UserResponseWithSelectColumn>> response =
                new UserController(service).findUserByIdWithSelectedColumns(uuid("missing-id"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("User not found with id: missing-id",
                response.getBody().getError());
        assertNull(response.getBody().getData());
        verify(service).findUserByIdWithSelectedColumns(uuid("missing-id"));
    }

    // Verifies that a successful update uses the service status and message.
    @Test
    void updateUserReturnsWrappedResponseUsingServiceStatusAndMessage() {
        UserService service = mock(UserService.class);
        UserUpdateRequest request = new UserUpdateRequest("updated@example.com", false);
        UserEntity user = user(uuid("user-1"), "updated@example.com", false);
        when(service.updateUser(uuid("user-1"), request)).thenReturn(
                new ServiceResult<>(user, HttpStatus.OK, "User updated successfully"));

        ResponseEntity<ApiResponse<UserResponse>> response =
                new UserController(service).updateUser(uuid("user-1"), request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getHttpCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals("User updated successfully", response.getBody().getMessage());
        assertEquals("updated@example.com", response.getBody().getData().email());
        assertEquals(false, response.getBody().getData().isActive());
        verify(service).updateUser(uuid("user-1"), request);
    }

    // Verifies that updating a missing user returns a wrapped not-found response.
    @Test
    void updateUserReturnsWrappedNotFoundUsingServiceStatusAndMessage() {
        UserService service = mock(UserService.class);
        UserUpdateRequest request = new UserUpdateRequest("updated@example.com", false);
        when(service.updateUser(uuid("missing-id"), request)).thenReturn(
                new ServiceResult<>(null, HttpStatus.NOT_FOUND, "User not found with id: missing-id"));

        ResponseEntity<ApiResponse<UserResponse>> response =
                new UserController(service).updateUser(uuid("missing-id"), request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getHttpCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("User not found with id: missing-id", response.getBody().getError());
        assertNull(response.getBody().getData());
        verify(service).updateUser(uuid("missing-id"), request);
    }

    // Verifies that a successful deletion returns the deleted user response.
    @Test
    void deleteUserReturnsDeletedUserResponse() {
        UserService service = mock(UserService.class);
        UserEntity deletedUser = user(uuid("user-1"), "user@example.com", true);
        when(service.deleteUser(uuid("user-1"))).thenReturn(
                new ServiceResult<>(deletedUser, HttpStatus.OK, "User deleted successfully"));

        ResponseEntity<ApiResponse<UserResponse>> response = new UserController(service).deleteUser(uuid("user-1"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("User deleted successfully", response.getBody().getMessage());
        assertEquals(uuid("user-1"), response.getBody().getData().id());
        verify(service).deleteUser(uuid("user-1"));
    }

    // Verifies that deleting a missing user returns a wrapped not-found response.
    @Test
    void deleteUserReturnsWrappedNotFoundUsingServiceStatusAndMessage() {
        UserService service = mock(UserService.class);
        when(service.deleteUser(uuid("missing-id"))).thenReturn(
                new ServiceResult<>(null, HttpStatus.NOT_FOUND,
                        "User not found with id: missing-id"));

        ResponseEntity<ApiResponse<UserResponse>> response =
                new UserController(service).deleteUser(uuid("missing-id"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getHttpCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("User not found with id: missing-id",
                response.getBody().getError());
        assertNull(response.getBody().getData());
        verify(service).deleteUser(uuid("missing-id"));
    }

    private static UserEntity user(UUID id, String email, Boolean isActive) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail(email);
        user.setPassword("password123");
        user.setIsActive(isActive);
        return user;
    }

    private static UserSelectColumn projection(UUID id, String email, Boolean isActive) {
        UserSelectColumn projection = mock(UserSelectColumn.class);
        when(projection.getId()).thenReturn(id);
        when(projection.getEmail()).thenReturn(email);
        when(projection.getIsActive()).thenReturn(isActive);
        return projection;
    }

    private static UUID uuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
