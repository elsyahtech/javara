package com.elsyah.tech.javara.repository.welcome;

import org.springframework.stereotype.Repository;

@Repository  
public class WelcomeRepository {
    public String welcome() {
        return "Welcome";
    }
}