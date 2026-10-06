package com.example.lokaleondernemers.security;

import com.example.lokaleondernemers.model.Gebruiker;
import com.example.lokaleondernemers.repository.GebruikerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GebruikerDetailsService implements UserDetailsService {

    private final GebruikerRepository repository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Gebruiker g = repository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new UsernameNotFoundException("Onbekende gebruiker"));
        return User.withUsername(g.getEmail())
                .password(g.getWachtwoord())
                .roles(g.getRol().name())
                .disabled(!g.isActief())
                .build();
    }
}
