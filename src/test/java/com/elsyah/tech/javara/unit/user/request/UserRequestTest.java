package com.elsyah.tech.javara.unit.user.request;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import com.elsyah.tech.javara.request.user.UserRequest;

class UserRequestTest {
    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    // Verifies that a valid email and password satisfy all validation constraints.
    @Test
    void shouldAcceptValidUserRequest() {
        UserRequest request =
                new UserRequest("user@example.com", "password123", true);

        assertThat(validator.validate(request)).isEmpty();
    }

    // Verifies that blank email values produce the required validation message.
    @Test
    void shouldRejectBlankEmail() {
        UserRequest request =
                new UserRequest(" ", "password123", true);

        Set<ConstraintViolation<UserRequest>> violations =
                validator.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getMessage())
                .contains("email is required");
    }

    // Verifies that malformed email and short password values are rejected.
    @Test
    void shouldRejectInvalidEmailAndShortPassword() {
        UserRequest request =
                new UserRequest("invalid-email", "short", true);

        Set<ConstraintViolation<UserRequest>> violations =
                validator.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getMessage())
                .contains("email is not valid",
                        "password must be 8-72 characters");
    }

    // Verifies that passwords longer than 72 characters are rejected.
    @Test
    void shouldRejectPasswordOverMaximumLength() {
        UserRequest request =
                new UserRequest("user@example.com", "a".repeat(73), true);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getMessage())
                .contains("password must be 8-72 characters");
    }
}
