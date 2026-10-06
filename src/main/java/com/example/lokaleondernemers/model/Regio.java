package com.example.lokaleondernemers.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "regio")
@Getter
@Setter
@NoArgsConstructor
public class Regio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String naam;

    @Column(length = 1000)
    private String beschrijving;

    public Regio(String naam, String beschrijving) {
        this.naam = naam;
        this.beschrijving = beschrijving;
    }
}
