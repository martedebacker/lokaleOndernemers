package com.example.lokaleondernemers.repository;

import com.example.lokaleondernemers.model.Gebruiker;
import com.example.lokaleondernemers.model.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GebruikerRepository extends JpaRepository<Gebruiker, Long> {

    Optional<Gebruiker> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRol(Rol rol);

    long countByRol(Rol rol);

    List<Gebruiker> findAllByOrderByAangemaaktOpDesc();
}
