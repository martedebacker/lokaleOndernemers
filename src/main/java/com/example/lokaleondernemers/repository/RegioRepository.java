package com.example.lokaleondernemers.repository;

import com.example.lokaleondernemers.model.Regio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RegioRepository extends JpaRepository<Regio, Long> {

    List<Regio> findAllByOrderByNaamAsc();

    boolean existsByNaamIgnoreCase(String naam);
}
