package com.elsyah.tech.javara.unit.controller;

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

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.elsyah.tech.javara.entity.UserEntity;
import com.elsyah.tech.javara.projection.UserSelectColumn;
import com.elsyah.tech.javara.request.UserRequest;
import com.elsyah.tech.javara.response.ApiResponse;
import com.elsyah.tech.javara.service.ServiceResult;
import com.elsyah.tech.javara.response.UserResponse;
import com.elsyah.tech.javara.response.UserResponseWithSelectColumn;
import com.elsyah.tech.javara.service.UserService;
import com.elsyah.tech.javara.controller.UserController;


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

    // Verifies that an unknown user path returns HTTP 404 and does not call the service.
    @Test
    void unknownPathIsNotTreatedAsUserId() throws Exception {
        UserService service = mock(UserService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new UserController(service)).build();

        mockMvc.perform(get("/api/v1/users/findAllWithSelectedColumnsas"))
                .andExpect(status().isNotFound());

        verifyNoInteractions(service);
    }

    // Verifies that user creation wraps the service result with the service status and message.
    @Test
    void createUserReturnsWrappedResponseUsingServiceStatusAndMessage() {
        UserService service = mock(UserService.class);
        UserRequest request = new UserRequest("new@example.com", "password123", true);
        UserEntity user = user("user-1", "new@example.com", true);
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
        assertEquals("user-1", response.getBody().getData().id());
        verify(service).createUser(request);
    }

    // Verifies that the find-all endpoint maps entities from the service into API responses.
    @Test
    void findAllUsersReturnsWrappedMappedResponsesFromService() {
        UserService service = mock(UserService.class);
        when(service.findAllUsers()).thenReturn(new ServiceResult<>(
                List.of(
                    user("user-1", "first@example.com", true),
                    user("user-2", "second@example.com", false)
                ),
                HttpStatus.OK,
                "Users retrieved successfully"));

        ResponseEntity<ApiResponse<List<UserResponse>>> response =
                new UserController(service).findAllUsers();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getHttpCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals("Users retrieved successfully", response.getBody().getMessage());
        assertEquals(2, response.getBody().getData().size());
        assertEquals("first@example.com", response.getBody().getData().get(0).email());
        assertEquals("second@example.com", response.getBody().getData().get(1).email());
        assertEquals(2, response.getBody().getAdditionalFields().get("count"));
        verify(service).findAllUsers();
    }

    // Verifies that an empty service result is wrapped as a failed response.
    @Test
    void findAllUsersReturnsEmptyListWhenServiceReturnsEmptyList() {
        UserService service = mock(UserService.class);
        when(service.findAllUsers()).thenReturn(
                new ServiceResult<>(null, HttpStatus.NOT_FOUND, "No users found"));

        ResponseEntity<ApiResponse<List<UserResponse>>> response =
                new UserController(service).findAllUsers();

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("No users found", response.getBody().getError());
        assertNull(response.getBody().getData());
    }

    // Verifies that selected-column projections are mapped by the controller.
    @Test
    void findUsersWithSelectColumnReturnsMappedResponsesFromService() {
        UserService service = mock(UserService.class);
        UserSelectColumn projection = projection("user-1", "user@example.com", true);
        when(service.findAllUsersWithSelectedColumns()).thenReturn(
                new ServiceResult<>(List.of(projection), HttpStatus.OK, "Users retrieved successfully"));

        ResponseEntity<ApiResponse<List<UserResponseWithSelectColumn>>> response =
                new UserController(service).findAllUsersWithSelectedColumns();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getHttpCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals("Users retrieved successfully", response.getBody().getMessage());
        assertEquals(1, response.getBody().getData().size());
        assertEquals("user-1", response.getBody().getData().get(0).id());
        assertEquals("user@example.com", response.getBody().getData().get(0).email());
        assertEquals(1, response.getBody().getAdditionalFields().get("count"));
        verify(service).findAllUsersWithSelectedColumns();
    }

    // Verifies that paginated selected-column results are mapped and counted.
    @Test
    void findAllUsersWithLimitAndOffsetReturnsMappedResponsesFromService() {
        UserService service = mock(UserService.class);
        Pageable pageable = PageRequest.of(0, 10);
        UserSelectColumn projection = projection("user-1", "user@example.com", true);
        when(service.findAllUsersWithLimitAndOffset(pageable)).thenReturn(
                new ServiceResult<>(List.of(projection), HttpStatus.OK, "Users retrieved successfully"));

        ResponseEntity<ApiResponse<List<UserResponseWithSelectColumn>>> response =
                new UserController(service).findAllUsersWithLimitAndOffset(pageable, 10);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getHttpCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals("Users retrieved successfully", response.getBody().getMessage());
        assertEquals(1, response.getBody().getData().size());
        assertEquals("user-1", response.getBody().getData().get(0).id());
        assertEquals("user@example.com", response.getBody().getData().get(0).email());
        assertEquals(1, response.getBody().getAdditionalFields().get("count"));
        verify(service).findAllUsersWithLimitAndOffset(pageable);
    }

    // Verifies that empty paginated results are wrapped as not-found responses.
    @Test
    void findAllUsersWithLimitAndOffsetReturnsNotFoundForEmptyResult() {
        UserService service = mock(UserService.class);
        Pageable pageable = PageRequest.of(0, 10);
        when(service.findAllUsersWithLimitAndOffset(pageable)).thenReturn(
                new ServiceResult<>(null, HttpStatus.NOT_FOUND, "No users found"));

        ResponseEntity<ApiResponse<List<UserResponseWithSelectColumn>>> response =
                new UserController(service).findAllUsersWithLimitAndOffset(pageable, 10);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("No users found", response.getBody().getError());
        assertNull(response.getBody().getData());
    }

    @Test
    void findAllUsersWithLimitAndOffsetRejectsInvalidRequestedSize() {
        UserService service = mock(UserService.class);
        ResponseEntity<ApiResponse<List<UserResponseWithSelectColumn>>> response =
                new UserController(service).findAllUsersWithLimitAndOffset(
                        PageRequest.of(0, 20),
                        0);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        verifyNoInteractions(service);
    }

    // Verifies that a found user is returned through the standard API response wrapper.
    @Test
    void findUserByIdReturnsWrappedResponseFromService() {
        UserService service = mock(UserService.class);
        UserEntity user = user("user-1", "user@example.com", true);
        when(service.findUserById("user-1")).thenReturn(
                new ServiceResult<>(user, HttpStatus.OK, "User found"));

        ResponseEntity<ApiResponse<UserResponse>> response =
                new UserController(service).findUserById("user-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getHttpCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals("User found", response.getBody().getMessage());
        assertEquals("user-1", response.getBody().getData().id());
        assertEquals("user@example.com", response.getBody().getData().email());
        verify(service).findUserById("user-1");
    }

    // Verifies that a missing user is returned as a wrapped not-found response.
    @Test
    void findUserByIdReturnsWrappedNotFoundResult() {
        UserService service = mock(UserService.class);
        when(service.findUserById("missing-id")).thenReturn(
                new ServiceResult<>(null, HttpStatus.NOT_FOUND, "User not found with id: missing-id"));

        ResponseEntity<ApiResponse<UserResponse>> response =
                new UserController(service).findUserById("missing-id");

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
        UserSelectColumn projection = projection("user-1", "user@example.com", true);
        when(service.findUserByIdWithSelectedColumns("user-1")).thenReturn(
                new ServiceResult<>(projection, HttpStatus.OK, "User found"));

        ResponseEntity<ApiResponse<UserResponseWithSelectColumn>> response =
                new UserController(service).findUserByIdWithSelectedColumns("user-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("user-1", response.getBody().getData().id());
        assertEquals("user@example.com", response.getBody().getData().email());
        verify(service).findUserByIdWithSelectedColumns("user-1");
    }

    // Verifies that selected-column lookup returns not-found for a missing user.
    @Test
    void findUserByIdWithSelectColumnReturnsNotFoundResponse() {
        UserService service = mock(UserService.class);
        when(service.findUserByIdWithSelectedColumns("missing-id")).thenReturn(
                new ServiceResult<>(null, HttpStatus.NOT_FOUND,
                        "User not found with id: missing-id"));

        ResponseEntity<ApiResponse<UserResponseWithSelectColumn>> response =
                new UserController(service).findUserByIdWithSelectedColumns("missing-id");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("User not found with id: missing-id",
                response.getBody().getError());
        assertNull(response.getBody().getData());
        verify(service).findUserByIdWithSelectedColumns("missing-id");
    }

    // Verifies that a successful update uses the service status and message.
    @Test
    void updateUserReturnsWrappedResponseUsingServiceStatusAndMessage() {
        UserService service = mock(UserService.class);
        UserRequest request = new UserRequest("updated@example.com", "newpassword", false);
        UserEntity user = user("user-1", "updated@example.com", false);
        when(service.updateUser("user-1", request)).thenReturn(
                new ServiceResult<>(user, HttpStatus.OK, "User updated successfully"));

        ResponseEntity<ApiResponse<UserResponse>> response =
                new UserController(service).updateUser("user-1", request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(200, response.getBody().getHttpCode());
        assertTrue(response.getBody().isSuccess());
        assertEquals("User updated successfully", response.getBody().getMessage());
        assertEquals("updated@example.com", response.getBody().getData().email());
        assertEquals(false, response.getBody().getData().isActive());
        verify(service).updateUser("user-1", request);
    }

    // Verifies that updating a missing user returns a wrapped not-found response.
    @Test
    void updateUserReturnsWrappedNotFoundUsingServiceStatusAndMessage() {
        UserService service = mock(UserService.class);
        UserRequest request = new UserRequest("updated@example.com", "newpassword", false);
        when(service.updateUser("missing-id", request)).thenReturn(
                new ServiceResult<>(null, HttpStatus.NOT_FOUND, "User not found with id: missing-id"));

        ResponseEntity<ApiResponse<UserResponse>> response =
                new UserController(service).updateUser("missing-id", request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getHttpCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("User not found with id: missing-id", response.getBody().getError());
        assertNull(response.getBody().getData());
        verify(service).updateUser("missing-id", request);
    }

    // Verifies that a successful deletion returns the deleted user response.
    @Test
    void deleteUserReturnsDeletedUserResponse() {
        UserService service = mock(UserService.class);
        UserEntity deletedUser = user("user-1", "user@example.com", true);
        when(service.deleteUser("user-1")).thenReturn(
                new ServiceResult<>(deletedUser, HttpStatus.OK, "User deleted successfully"));

        ResponseEntity<ApiResponse<UserResponse>> response = new UserController(service).deleteUser("user-1");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals("User deleted successfully", response.getBody().getMessage());
        assertEquals("user-1", response.getBody().getData().id());
        verify(service).deleteUser("user-1");
    }

    // Verifies that deleting a missing user returns a wrapped not-found response.
    @Test
    void deleteUserReturnsWrappedNotFoundUsingServiceStatusAndMessage() {
        UserService service = mock(UserService.class);
        when(service.deleteUser("missing-id")).thenReturn(
                new ServiceResult<>(null, HttpStatus.NOT_FOUND,
                        "User not found with id: missing-id"));

        ResponseEntity<ApiResponse<UserResponse>> response =
                new UserController(service).deleteUser("missing-id");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getHttpCode());
        assertFalse(response.getBody().isSuccess());
        assertEquals("User not found with id: missing-id",
                response.getBody().getError());
        assertNull(response.getBody().getData());
        verify(service).deleteUser("missing-id");
    }

    private static UserEntity user(String id, String email, Boolean isActive) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setEmail(email);
        user.setPassword("password123");
        user.setIsActive(isActive);
        return user;
    }

    private static UserSelectColumn projection(String id, String email, Boolean isActive) {
        UserSelectColumn projection = mock(UserSelectColumn.class);
        when(projection.getId()).thenReturn(id);
        when(projection.getEmail()).thenReturn(email);
        when(projection.getIsActive()).thenReturn(isActive);
        return projection;
    }
}
