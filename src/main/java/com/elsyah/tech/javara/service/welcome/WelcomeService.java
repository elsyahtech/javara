package com.elsyah.tech.javara.service.welcome;

import org.springframework.stereotype.Service;

import com.elsyah.tech.javara.repository.welcome.WelcomeRepository;

@Service  
public class WelcomeService {
    private final WelcomeRepository repo;

    public WelcomeService(WelcomeRepository repo) {
        this.repo = repo;
    }

    public String welcome() {
        return repo.welcome();
    }
}