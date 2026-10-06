package com.example.lokaleondernemers.web;

import com.example.lokaleondernemers.model.Gebruiker;
import com.example.lokaleondernemers.service.GebruikerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Geeft de ingelogde gebruiker (als entiteit) voor de huidige request. */
@Component
@RequiredArgsConstructor
public class HuidigeGebruiker {

    private final GebruikerService gebruikerService;

    public Optional<Gebruiker> zoek() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return gebruikerService.zoekOpEmail(auth.getName());
    }

    public Gebruiker get() {
        return zoek().orElseThrow(() -> new IllegalStateException("Niemand ingelogd"));
    }
}
