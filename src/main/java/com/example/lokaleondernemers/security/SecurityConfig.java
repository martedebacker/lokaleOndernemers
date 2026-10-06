package com.example.lokaleondernemers.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/beheer/**").hasAnyRole("ONDERNEMER", "ADMIN")
                        .requestMatchers("/mijn/**", "/afrekenen/**", "/na-login").authenticated()
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("email")
                        .passwordParameter("wachtwoord")
                        // Na het inloggen gaan we terug naar de gevraagde pagina, anders naar het
                        // startpunt dat bij de rol past.
                        .defaultSuccessUrl("/na-login")
                        .failureUrl("/login?fout")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/uitloggen")
                        .logoutSuccessUrl("/?uitgelogd")
                        .permitAll());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
