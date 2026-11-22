package com.elsyah.tech.javara.controller;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

import com.elsyah.tech.javara.response.WelcomeResponse;
import com.elsyah.tech.javara.service.WelcomeService;;

@RestController  
public class WelcomeController {
    private final WelcomeService service;

    public WelcomeController(WelcomeService service) {
        this.service = service;
    }

    @GetMapping("/")
    public WelcomeResponse welcome() {
        return new WelcomeResponse(service.welcome());
    }
}
