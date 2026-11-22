package com.elsyah.tech.javara.unit.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.elsyah.tech.javara.response.WelcomeResponse;
import com.elsyah.tech.javara.service.WelcomeService;
import com.elsyah.tech.javara.controller.WelcomeController;

class WelcomeControllerTests {

    // Verifies that the controller wraps the service message in a welcome response.
    @Test
    void welcomeShouldWrapServiceMessageInResponse() {
        WelcomeService service = mock(WelcomeService.class);
        when(service.welcome()).thenReturn("Welcome");
        WelcomeController controller = new WelcomeController(service);

        WelcomeResponse response = controller.welcome();

        assertEquals("Welcome", response.welcome());
        verify(service).welcome();
    }
}
