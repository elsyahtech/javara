package com.elsyah.tech.javara.unit.welcome.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.elsyah.tech.javara.repository.welcome.WelcomeRepository;
import com.elsyah.tech.javara.service.welcome.WelcomeService;

class WelcomeServiceTests {

    // Verifies that the welcome service returns the message provided by the repository.
    @Test
    void welcomeShouldReturnRepositoryMessage() {
        WelcomeRepository repository = mock(WelcomeRepository.class);
        when(repository.welcome()).thenReturn("Welcome");
        WelcomeService service = new WelcomeService(repository);

        String result = service.welcome();

        assertEquals("Welcome", result);
        verify(repository).welcome();
    }
}